package com.batchiller.api;

/**
 * Enhanced event-based listener for comprehensive job lifecycle monitoring.
 * This interface provides fine-grained event notifications for all stages
 * of job execution, allowing for detailed monitoring, auditing, metrics collection,
 * and custom business logic based on job state changes.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public interface JobEventListener {
    
    default void onPending(JobContext context) {
        // Default implementation does nothing
    }
    
    default void onQueued(JobContext context) {
        // Default implementation does nothing
    }
    
    default void onStart(JobContext context) {
        // Default implementation does nothing
    }
    
    default void onEnd(JobContext context, JobResult result) {
        // Default implementation does nothing
    }
    
    default void onPass(JobContext context, JobResult result) {
        // Default implementation does nothing
    }
    
    default void onFailed(JobContext context, JobResult result) {
        // Default implementation does nothing
    }
    
    default void onRetry(JobContext context, int attemptNumber) {
        // Default implementation does nothing
    }
    
    default void onTimeout(JobContext context) {
        // Default implementation does nothing
    }
    
    default void onCancelled(JobContext context, String reason) {
        // Default implementation does nothing
    }
    
    default void onSkipped(JobContext context, String reason) {
        // Default implementation does nothing
    }
}