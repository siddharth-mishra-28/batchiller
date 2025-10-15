package com.batchiller.server;

import com.batchiller.api.JobConfiguration;
import com.batchiller.api.PipelineConfiguration;
import com.batchiller.api.PipelineFlow;
import com.batchiller.server.config.BatchillerConfiguration;
import com.batchiller.server.database.DatabaseManager;
import com.batchiller.server.engine.JobExecutionEngine;
import com.batchiller.server.http.HttpServer;
import com.batchiller.server.loader.DynamicJobLoader;
import com.batchiller.server.logging.LogManager;
import com.batchiller.server.monitoring.SystemMonitor;
import com.batchiller.server.sample.SampleJob;
import com.batchiller.server.sample.SamplePipeline;
import com.batchiller.server.scheduler.JobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Main class for the Batchiller application.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class BatchillerApplication {
    
    private static final Logger logger = LoggerFactory.getLogger(BatchillerApplication.class);
    
    private final BatchillerConfiguration config;
    private final DatabaseManager database;
    private final JobExecutionEngine engine;
    private final SystemMonitor monitor;
    private final JobScheduler scheduler;
    private final HttpServer httpServer;
    private final DynamicJobLoader jobLoader;
    private final LogManager logManager;
    
    public BatchillerApplication() {
        logger.info("Initializing Batchiller Application...");
        
        this.config = new BatchillerConfiguration();
        this.database = new DatabaseManager(config);
        this.logManager = new LogManager(config);
        this.engine = new JobExecutionEngine(config, database, logManager);
        this.monitor = new SystemMonitor(engine.getExecutor());
        this.scheduler = new JobScheduler(engine, database, config);
        this.httpServer = new HttpServer(config, engine, database, monitor, logManager, scheduler);
        this.jobLoader = new DynamicJobLoader(config, engine);
        
        registerSampleJobs();
        scheduleConfiguredJobs();
    }
    
    private void registerSampleJobs() {
        engine.registerJob(new SampleJob());
        engine.registerPipeline(new SamplePipeline());
        
        // Register sample event listener for comprehensive job monitoring
        engine.addEventListener(new com.batchiller.server.sample.LoggingJobEventListener());
        
        logger.info("Sample jobs, pipelines, and event listeners registered");
    }
    
    private void scheduleConfiguredJobs() {
        String sampleCron = config.get("sample.job.cron");
        if (sampleCron != null && !sampleCron.isEmpty()) {
            JobConfiguration jobConfig = JobConfiguration.builder("SampleJob")
                .cronExpression(sampleCron)
                .build();
            scheduler.scheduleJob(jobConfig);
        }
        
        String samplePipelineCron = config.get("sample.pipeline.cron");
        if (samplePipelineCron != null && !samplePipelineCron.isEmpty()) {
            PipelineConfiguration pipelineConfig = PipelineConfiguration.builder("SamplePipeline", PipelineFlow.SEQUENTIAL)
                .addJob(JobConfiguration.builder("SampleJob").build())
                .cronExpression(samplePipelineCron)
                .build();
            scheduler.schedulePipeline(pipelineConfig);
        }
        
        logger.info("Scheduled jobs configured");
    }
    
    public void start() {
        logger.info("Starting Batchiller Application...");
        jobLoader.start();
        httpServer.start();
        logger.info("Batchiller Application started successfully!");
        logger.info("Access the dashboard at: http://{}:{}", config.getServerHost(), config.getServerPort());
    }
    
    public void stop() {
        logger.info("Stopping Batchiller Application...");
        httpServer.stop();
        jobLoader.stop();
        scheduler.shutdown();
        engine.shutdown();
        logManager.shutdown();
        database.close();
        logger.info("Batchiller Application stopped");
    }
    
    public static void main(String[] args) {
        System.setProperty("app.home", new java.io.File("").getAbsolutePath());
        BatchillerApplication app = new BatchillerApplication();
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            logger.info("Shutdown hook triggered");
            app.stop();
        }));
        
        app.start();
    }
}
