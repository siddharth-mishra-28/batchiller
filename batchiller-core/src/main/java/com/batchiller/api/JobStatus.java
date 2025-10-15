package com.batchiller.api;

/**
 * Enumeration of possible job execution statuses.
 * Represents the lifecycle states of a batch job execution within
 * the Batchiller orchestration system.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public enum JobStatus {
    
    PENDING,
    
    QUEUED,
    
    RUNNING,
    
    COMPLETED,
    
    FAILED,
    
    CANCELLED,
    
    TIMEOUT,
    
    RETRYING;
    
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == TIMEOUT;
    }
    
    public boolean isActive() {
        return this == QUEUED || this == RUNNING || this == RETRYING;
    }
    
    public boolean isSuccess() {
        return this == COMPLETED;
    }
}
