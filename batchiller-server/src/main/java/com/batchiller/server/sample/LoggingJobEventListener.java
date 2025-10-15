package com.batchiller.server.sample;

import com.batchiller.api.JobContext;
import com.batchiller.api.JobEventListener;
import com.batchiller.api.JobResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Sample implementation of JobEventListener that logs all job events.
 * This demonstrates comprehensive job monitoring and can be used as a template
 * for implementing custom monitoring, metrics collection, or notification systems.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class LoggingJobEventListener implements JobEventListener {
    
    private static final Logger logger = LoggerFactory.getLogger(LoggingJobEventListener.class);
    
    @Override
    public void onPending(JobContext context) {
        logger.info("📋 JOB PENDING - Job: {} | Execution ID: {} | Triggered by: {}", 
            context.getJobName(), context.getExecutionId(), context.getTriggeredBy());
    }
    
    @Override
    public void onQueued(JobContext context) {
        logger.info("📄 JOB QUEUED - Job: {} | Execution ID: {} | Thread: {}", 
            context.getJobName(), context.getExecutionId(), context.getThreadName());
    }
    
    @Override
    public void onStart(JobContext context) {
        logger.info("🚀 JOB STARTED - Job: {} | Execution ID: {} | Start Time: {} | Thread: {}", 
            context.getJobName(), context.getExecutionId(), context.getStartTime(), context.getThreadName());
    }
    
    @Override
    public void onEnd(JobContext context, JobResult result) {
        String duration = calculateDuration(context);
        logger.info("🏁 JOB ENDED - Job: {} | Execution ID: {} | Status: {} | Duration: {}", 
            context.getJobName(), context.getExecutionId(), result.getStatus(), duration);
    }
    
    @Override
    public void onPass(JobContext context, JobResult result) {
        String duration = calculateDuration(context);
        logger.info("✅ JOB SUCCESS - Job: {} | Execution ID: {} | Message: {} | Duration: {}", 
            context.getJobName(), context.getExecutionId(), result.getMessage(), duration);
        
        // Log result data if available
        if (!result.getData().isEmpty()) {
            logger.debug("📊 Job result data: {}", result.getData());
        }
    }
    
    @Override
    public void onFailed(JobContext context, JobResult result) {
        String duration = calculateDuration(context);
        logger.error("❌ JOB FAILED - Job: {} | Execution ID: {} | Error: {} | Duration: {}", 
            context.getJobName(), context.getExecutionId(), result.getMessage(), duration);
        
        // Log exception details if available
        result.getException().ifPresent(e -> 
            logger.error("Exception details for job {}: ", context.getJobName(), e));
    }
    
    @Override
    public void onRetry(JobContext context, int attemptNumber) {
        logger.warn("🔄 JOB RETRY - Job: {} | Execution ID: {} | Attempt: {} | Retry: {}", 
            context.getJobName(), context.getExecutionId(), attemptNumber, context.isRetry());
    }
    
    @Override
    public void onTimeout(JobContext context) {
        String duration = calculateDuration(context);
        logger.error("⏰ JOB TIMEOUT - Job: {} | Execution ID: {} | Duration: {}", 
            context.getJobName(), context.getExecutionId(), duration);
    }
    
    @Override
    public void onCancelled(JobContext context, String reason) {
        String duration = calculateDuration(context);
        logger.warn("🚫 JOB CANCELLED - Job: {} | Execution ID: {} | Reason: {} | Duration: {}", 
            context.getJobName(), context.getExecutionId(), reason, duration);
    }
    
    @Override
    public void onSkipped(JobContext context, String reason) {
        logger.info("⏭️  JOB SKIPPED - Job: {} | Execution ID: {} | Reason: {}", 
            context.getJobName(), context.getExecutionId(), reason);
    }
    
    private String calculateDuration(JobContext context) {
        try {
            long startMillis = context.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
            long nowMillis = System.currentTimeMillis();
            long duration = nowMillis - startMillis;
            
            if (duration < 1000) {
                return duration + "ms";
            } else if (duration < 60000) {
                return String.format("%.1fs", duration / 1000.0);
            } else {
                return String.format("%.1fm", duration / 60000.0);
            }
        } catch (Exception e) {
            return "unknown";
        }
    }
}