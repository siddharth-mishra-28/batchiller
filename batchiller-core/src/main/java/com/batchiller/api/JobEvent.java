package com.batchiller.api;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Represents an event in the job lifecycle.
 * Events are published by the orchestration engine and can be
 * consumed by listeners for monitoring, auditing, and custom processing.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public final class JobEvent {
    
    public enum Type {
        SCHEDULED,
        STARTED,
        COMPLETED,
        FAILED,
        RETRYING,
        CANCELLED,
        TIMEOUT
    }
    
    private final Type type;
    private final String executionId;
    private final String jobName;
    private final String pipelineName;
    private final LocalDateTime timestamp;
    private final String message;
    private final JobStatus status;
    
    private JobEvent(Builder builder) {
        this.type = Objects.requireNonNull(builder.type);
        this.executionId = Objects.requireNonNull(builder.executionId);
        this.jobName = Objects.requireNonNull(builder.jobName);
        this.pipelineName = builder.pipelineName;
        this.timestamp = builder.timestamp != null ? builder.timestamp : LocalDateTime.now();
        this.message = builder.message;
        this.status = builder.status;
    }
    
    public Type getType() {
        return type;
    }
    
    public String getExecutionId() {
        return executionId;
    }
    
    public String getJobName() {
        return jobName;
    }
    
    public String getPipelineName() {
        return pipelineName;
    }
    
    public LocalDateTime getTimestamp() {
        return timestamp;
    }
    
    public String getMessage() {
        return message;
    }
    
    public JobStatus getStatus() {
        return status;
    }
    
    public static Builder builder(Type type, String executionId, String jobName) {
        return new Builder(type, executionId, jobName);
    }
    
    public static final class Builder {
        private final Type type;
        private final String executionId;
        private final String jobName;
        private String pipelineName;
        private LocalDateTime timestamp;
        private String message;
        private JobStatus status;
        
        private Builder(Type type, String executionId, String jobName) {
            this.type = type;
            this.executionId = executionId;
            this.jobName = jobName;
        }
        
        public Builder pipelineName(String pipelineName) {
            this.pipelineName = pipelineName;
            return this;
        }
        
        public Builder timestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
            return this;
        }
        
        public Builder message(String message) {
            this.message = message;
            return this;
        }
        
        public Builder status(JobStatus status) {
            this.status = status;
            return this;
        }
        
        public JobEvent build() {
            return new JobEvent(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("JobEvent{type=%s, jobName='%s', executionId='%s', timestamp=%s, message='%s'}",
            type, jobName, executionId, timestamp, message);
    }
}
