package com.batchiller.api;

/**
 * Exception thrown when a job execution fails.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class JobExecutionException extends BatchillerException {
    
    private final String jobName;
    private final String executionId;
    
    public JobExecutionException(String jobName, String executionId, String message) {
        super(String.format("Job '%s' (execution: %s) failed: %s", jobName, executionId, message));
        this.jobName = jobName;
        this.executionId = executionId;
    }
    
    public JobExecutionException(String jobName, String executionId, Throwable cause) {
        super(String.format("Job '%s' (execution: %s) failed", jobName, executionId), cause);
        this.jobName = jobName;
        this.executionId = executionId;
    }
    
    public String getJobName() {
        return jobName;
    }
    
    public String getExecutionId() {
        return executionId;
    }
}
