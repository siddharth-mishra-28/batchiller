package com.batchiller.api;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Defines a pipeline of batch jobs to be executed in a coordinated manner.
 * A {@code Pipeline} orchestrates the execution of multiple {@link BatchJob}s
 * with support for sequential, parallel, and conditional execution flows.
 * The pipeline engine ensures jobs are executed in the correct order based
 * on their dependencies and flow rules.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public interface Pipeline {
    
    String getName();
    
    String getDescription();
    
    List<BatchJob> getJobs();
    
    PipelineFlow getFlow();
    
    default boolean isManualExecutionAllowed() {
        return true;
    }
    
    default boolean isSchedulingAllowed() {
        return true;
    }
    
    default CompletableFuture<Void> onStart(JobContext context) {
        return CompletableFuture.completedFuture(null);
    }
    
    default CompletableFuture<Void> onComplete(JobContext context, boolean success) {
        return CompletableFuture.completedFuture(null);
    }
}
