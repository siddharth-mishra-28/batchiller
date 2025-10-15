package com.batchiller.api;

/**
 * Provides lifecycle hooks for batch jobs.
 * Jobs can implement this interface to receive notifications about
 * various stages of their lifecycle, allowing for custom initialization,
 * cleanup, and other lifecycle-specific operations.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public interface JobLifecycle {
    
    default void init(JobContext context) throws Exception {
        // Default implementation does nothing
    }
    
    default void beforeStart(JobContext context) throws Exception {
        // Default implementation does nothing
    }
    
    default void afterEnd(JobContext context, JobResult result) {
        // Default implementation does nothing
    }
    
    default void shutdown(JobContext context) {
        // Default implementation does nothing
    }
}