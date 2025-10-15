package com.batchiller.api;

import java.util.concurrent.CompletableFuture;

/**
 * Core interface representing a batch job in the Batchiller pipeline orchestration system.
 * A {@code BatchJob} represents a unit of work that can be executed asynchronously
 * within a pipeline. Each job receives a {@link JobContext} containing execution
 * parameters and environment information, and returns a {@link JobResult} indicating
 * the outcome of the execution.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public interface BatchJob {
    
    String getName();
    
    String getDescription();
    
    CompletableFuture<JobResult> execute(JobContext context);
    
    default long getTimeoutMillis() {
        return 300000L;
    }
    
    default boolean isRetryable() {
        return true;
    }
    
    default int getMaxRetries() {
        return 3;
    }
}
