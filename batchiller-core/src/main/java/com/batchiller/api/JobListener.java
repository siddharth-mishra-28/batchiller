package com.batchiller.api;

/**
 * Listener interface for job lifecycle events.
 * Implementations of {@code JobListener} can be registered with the
 * orchestration engine to receive notifications about job execution events.
 * This is useful for monitoring, auditing, and custom processing logic.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public interface JobListener {
    
    default void onJobScheduled(JobContext context) {
    }
    
    default void onJobStart(JobContext context) {
    }
    
    default void onJobComplete(JobContext context, JobResult result) {
    }
    
    default void onJobFailure(JobContext context, JobResult result) {
    }
    
    default void onJobRetry(JobContext context, int attemptNumber) {
    }
    
    default void onJobCancelled(JobContext context, String reason) {
    }
}
