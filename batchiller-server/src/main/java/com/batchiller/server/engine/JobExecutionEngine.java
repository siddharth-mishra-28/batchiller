package com.batchiller.server.engine;

import com.batchiller.api.*;
import com.batchiller.api.JobLifecycle;
import com.batchiller.api.JobEventListener;
import com.batchiller.server.config.BatchillerConfiguration;
import com.batchiller.server.context.DefaultJobContext;
import com.batchiller.server.database.DatabaseManager;
import com.batchiller.server.logging.LogManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

/**
 * A class that executes jobs and pipelines.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class JobExecutionEngine {
    
    private static final Logger logger = LoggerFactory.getLogger(JobExecutionEngine.class);
    
    private final ThreadPoolExecutor executor;
    private final DatabaseManager databaseManager;
    private final LogManager logManager;
    private final Map<String, BatchJob> jobs = new ConcurrentHashMap<>();
    private final Map<String, Pipeline> pipelines = new ConcurrentHashMap<>();
    private final List<JobListener> listeners = new CopyOnWriteArrayList<>();
    private final List<JobEventListener> eventListeners = new CopyOnWriteArrayList<>();
    
    public JobExecutionEngine(BatchillerConfiguration config, DatabaseManager databaseManager, LogManager logManager) {
        this.databaseManager = databaseManager;
        this.logManager = logManager;
        this.executor = new ThreadPoolExecutor(
            config.getCorePoolSize(),
            config.getMaxPoolSize(),
            60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(config.getQueueCapacity()),
            new ThreadFactory() {
                private int counter = 0;
                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, "batchiller-worker-" + (++counter));
                }
            }
        );
        logger.info("JobExecutionEngine initialized with core pool size: {}, max pool size: {}", 
            config.getCorePoolSize(), config.getMaxPoolSize());
    }
    
    public void registerJob(BatchJob job) {
        jobs.put(job.getName(), job);
        
        // Initialize job if it implements JobLifecycle
        if (job instanceof JobLifecycle) {
            try {
                String executionId = "init-" + UUID.randomUUID().toString();
                DefaultJobContext initContext = new DefaultJobContext(
                    executionId, job.getName(), null, Map.of(), "INIT", "SYSTEM", false, 0
                );
                ((JobLifecycle) job).init(initContext);
                logger.info("Initialized lifecycle for job: {}", job.getName());
            } catch (Exception e) {
                logger.error("Failed to initialize job lifecycle: " + job.getName(), e);
                throw new RuntimeException("Job initialization failed: " + job.getName(), e);
            }
        }
        
        logger.info("Registered job: {}", job.getName());
    }
    
    public void registerPipeline(Pipeline pipeline) {
        pipelines.put(pipeline.getName(), pipeline);
        pipeline.getJobs().forEach(this::registerJob);
        logger.info("Registered pipeline: {} with {} jobs", pipeline.getName(), pipeline.getJobs().size());
    }
    
    public void addListener(JobListener listener) {
        listeners.add(listener);
    }
    
    public void addEventListener(JobEventListener listener) {
        eventListeners.add(listener);
    }
    
    public CompletableFuture<JobResult> executeJob(String jobName, Map<String, Object> parameters, 
                                                    String triggeredBy, String triggerType) {
        BatchJob job = jobs.get(jobName);
        if (job == null) {
            return CompletableFuture.completedFuture(
                JobResult.failure("Job not found: " + jobName)
            );
        }
        
        String executionId = UUID.randomUUID().toString();
        DefaultJobContext context = new DefaultJobContext(
            executionId, jobName, null, parameters, triggerType, triggeredBy, false, 0
        );
        
        JobExecutionInfo execInfo = JobExecutionInfo.builder(executionId, jobName)
            .status(JobStatus.QUEUED)
            .triggeredBy(triggeredBy)
            .triggerType(triggerType)
            .build();
        
        databaseManager.saveJobExecution(execInfo);
        
        listeners.forEach(l -> l.onJobScheduled(context));
        eventListeners.forEach(l -> l.onPending(context));
        
        return CompletableFuture.supplyAsync(() -> {
            ch.qos.logback.classic.Logger jobLogger = null;
            eventListeners.forEach(l -> l.onQueued(context));
            listeners.forEach(l -> l.onJobStart(context));
            eventListeners.forEach(l -> l.onStart(context));
            
            String logFilePath = logManager.createLogFile(executionId, jobName);
            if (logFilePath != null) {
                jobLogger = logManager.getJobLogger(executionId, logFilePath);
                jobLogger.debug("Created log file for execution {}: {}", executionId, logFilePath);
            }
            
            JobExecutionInfo startInfo = JobExecutionInfo.builder(executionId, jobName)
                .status(JobStatus.RUNNING)
                .threadName(Thread.currentThread().getName())
                .triggeredBy(triggeredBy)
                .triggerType(triggerType)
                .build();
            databaseManager.updateJobExecution(startInfo);
            
            if (jobLogger != null) {
                jobLogger.info("=== Job Execution Log ===\nExecution ID: {}\nJob Name: {}\nTriggered By: {}\nTrigger Type: {}\nStart Time: {}\nThread: {}\n\n",
                    executionId, jobName, triggeredBy, triggerType, LocalDateTime.now(), Thread.currentThread().getName());
            }
            
            try {
                // Call beforeStart lifecycle hook
                if (job instanceof JobLifecycle) {
                    try {
                        ((JobLifecycle) job).beforeStart(context);
                        if (jobLogger != null) jobLogger.info("Lifecycle: beforeStart() completed successfully\n");
                    } catch (Exception e) {
                        if (jobLogger != null) jobLogger.error("Lifecycle: beforeStart() failed: {}", e.getMessage());
                        throw new RuntimeException("Job beforeStart failed", e);
                    }
                }
                
                JobResult result = job.execute(context).get();
                
                if (jobLogger != null) {
                    jobLogger.info("Execution Result:\nStatus: {}\nMessage: {}\nEnd Time: {}\nDuration: {}ms\n",
                        result.isSuccess() ? "SUCCESS" : "FAILURE",
                        result.getMessage(),
                        LocalDateTime.now(),
                        java.time.Duration.between(startInfo.getStartTime(), LocalDateTime.now()).toMillis());
                }
                
                JobExecutionInfo completeInfo = JobExecutionInfo.builder(executionId, jobName)
                    .status(result.isSuccess() ? JobStatus.COMPLETED : JobStatus.FAILED)
                    .endTime(LocalDateTime.now())
                    .threadName(Thread.currentThread().getName())
                    .triggeredBy(triggeredBy)
                    .triggerType(triggerType)
                    .resultMessage(result.getMessage())
                    .errorMessage(result.getException().map(Throwable::getMessage).orElse(null))
                    .build();
                
                databaseManager.updateJobExecution(completeInfo);
                
                // Call afterEnd lifecycle hook
                if (job instanceof JobLifecycle) {
                    try {
                        ((JobLifecycle) job).afterEnd(context, result);
                        if (jobLogger != null) jobLogger.info("Lifecycle: afterEnd() completed successfully\n");
                    } catch (Exception e) {
                        if (jobLogger != null) jobLogger.warn("Lifecycle: afterEnd() failed: {}", e.getMessage());
                        logger.warn("Job afterEnd failed for {}: {}", jobName, e.getMessage());
                    }
                }
                
                listeners.forEach(l -> l.onJobComplete(context, result));
                eventListeners.forEach(l -> l.onEnd(context, result));
                
                if (result.isFailure()) {
                    listeners.forEach(l -> l.onJobFailure(context, result));
                    eventListeners.forEach(l -> l.onFailed(context, result));
                } else {
                    eventListeners.forEach(l -> l.onPass(context, result));
                }
                
                return result;
            } catch (Exception e) {
                logger.error("Job execution failed: " + jobName, e);
                
                if (jobLogger != null) {
                    jobLogger.error("\nEXECUTION FAILED:\nError: {}\nStack Trace:\n{}\nEnd Time: {}\n",
                        e.getMessage(),
                        getStackTraceString(e),
                        LocalDateTime.now());
                }
                
                JobResult failureResult = JobResult.failure(e);
                
                JobExecutionInfo failInfo = JobExecutionInfo.builder(executionId, jobName)
                    .status(JobStatus.FAILED)
                    .endTime(LocalDateTime.now())
                    .threadName(Thread.currentThread().getName())
                    .triggeredBy(triggeredBy)
                    .triggerType(triggerType)
                    .errorMessage(e.getMessage())
                    .build();
                
                databaseManager.updateJobExecution(failInfo);
                
                // Call afterEnd lifecycle hook even on failure
                if (job instanceof JobLifecycle) {
                    try {
                        ((JobLifecycle) job).afterEnd(context, failureResult);
                        if (jobLogger != null) jobLogger.info("Lifecycle: afterEnd() completed successfully after failure\n");
                    } catch (Exception lifecycleE) {
                        if (jobLogger != null) jobLogger.warn("Lifecycle: afterEnd() failed: {}", lifecycleE.getMessage());
                        logger.warn("Job afterEnd failed for {}: {}", jobName, lifecycleE.getMessage());
                    }
                }
                
                listeners.forEach(l -> l.onJobComplete(context, failureResult));
                listeners.forEach(l -> l.onJobFailure(context, failureResult));
                eventListeners.forEach(l -> l.onEnd(context, failureResult));
                eventListeners.forEach(l -> l.onFailed(context, failureResult));
                
                return failureResult;
            }
        }, executor);
    }
    
    public CompletableFuture<Void> executePipeline(String pipelineName, String triggeredBy, String triggerType) {
        Pipeline pipeline = pipelines.get(pipelineName);
        if (pipeline == null) {
            return CompletableFuture.failedFuture(
                new PipelineExecutionException(pipelineName, "Pipeline not found")
            );
        }
        
        String executionId = UUID.randomUUID().toString();
        DefaultJobContext pipelineContext = new DefaultJobContext(
            executionId, pipelineName, pipelineName, Map.of(), triggerType, triggeredBy, false, 0
        );
        
        return pipeline.onStart(pipelineContext).thenCompose(v -> {
            if (pipeline.getFlow() == PipelineFlow.SEQUENTIAL) {
                return executeSequential(pipeline, triggeredBy, triggerType, pipelineContext);
            } else if (pipeline.getFlow() == PipelineFlow.PARALLEL) {
                return executeParallel(pipeline, triggeredBy, triggerType, pipelineContext);
            } else {
                return executeConditional(pipeline, triggeredBy, triggerType, pipelineContext);
            }
        }).thenCompose(success -> pipeline.onComplete(pipelineContext, success));
    }
    
    private CompletableFuture<Boolean> executeSequential(Pipeline pipeline, String triggeredBy, 
                                                         String triggerType, JobContext pipelineContext) {
        CompletableFuture<Boolean> result = CompletableFuture.completedFuture(true);
        
        for (BatchJob job : pipeline.getJobs()) {
            result = result.thenCompose(success -> {
                if (!success) {
                    return CompletableFuture.completedFuture(false);
                }
                return executeJob(job.getName(), Map.of(), triggeredBy, triggerType)
                    .thenApply(JobResult::isSuccess);
            });
        }
        
        return result;
    }
    
    private CompletableFuture<Boolean> executeParallel(Pipeline pipeline, String triggeredBy, 
                                                       String triggerType, JobContext pipelineContext) {
        List<CompletableFuture<JobResult>> futures = pipeline.getJobs().stream()
            .map(job -> executeJob(job.getName(), Map.of(), triggeredBy, triggerType))
            .toList();
        
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenApply(v -> futures.stream().allMatch(f -> f.join().isSuccess()));
    }
    
    private CompletableFuture<Boolean> executeConditional(Pipeline pipeline, String triggeredBy, 
                                                          String triggerType, JobContext pipelineContext) {
        return executeSequential(pipeline, triggeredBy, triggerType, pipelineContext);
    }
    
    public ThreadPoolExecutor getExecutor() {
        return executor;
    }
    
    public Collection<BatchJob> getJobs() {
        return jobs.values();
    }
    
    public Collection<Pipeline> getPipelines() {
        return pipelines.values();
    }
    
    public void unregisterJob(String jobName) {
        BatchJob job = jobs.remove(jobName);
        if (job != null) {
            // Remove from listeners if applicable
            if (job instanceof JobListener) {
                listeners.remove(job);
            }
            if (job instanceof JobEventListener) {
                eventListeners.remove(job);
            }

            // Call shutdown lifecycle hook
            if (job instanceof JobLifecycle) {
                try {
                    String executionId = "shutdown-" + UUID.randomUUID().toString();
                    DefaultJobContext shutdownContext = new DefaultJobContext(
                        executionId, job.getName(), null, Map.of(), "SHUTDOWN", "SYSTEM", false, 0
                    );
                    ((JobLifecycle) job).shutdown(shutdownContext);
                    logger.info("Shutdown lifecycle completed for job: {}", job.getName());
                } catch (Exception e) {
                    logger.warn("Job shutdown failed for {}: {}", job.getName(), e.getMessage());
                }
            }
            logger.info("Unregistered job: {}", jobName);
        }
    }
    
    public void shutdown() {
        // Shutdown all jobs with lifecycle support
        for (String jobName : new ArrayList<>(jobs.keySet())) {
            unregisterJob(jobName);
        }
        
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        logger.info("JobExecutionEngine shut down");
    }
    
    private String getStackTraceString(Throwable e) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);
        e.printStackTrace(pw);
        return sw.toString();
    }
}
