package com.batchiller.api;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Immutable record of job execution information.
 * This class captures all relevant information about a job execution
 * for persistence, monitoring, and reporting purposes.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public final class JobExecutionInfo {
    
    private final String executionId;
    private final String jobName;
    private final String pipelineName;
    private final JobStatus status;
    private final LocalDateTime startTime;
    private final LocalDateTime endTime;
    private final String threadName;
    private final String triggeredBy;
    private final String triggerType;
    private final boolean isRetry;
    private final int retryAttempt;
    private final String resultMessage;
    private final String errorMessage;
    
    private JobExecutionInfo(Builder builder) {
        this.executionId = Objects.requireNonNull(builder.executionId);
        this.jobName = Objects.requireNonNull(builder.jobName);
        this.pipelineName = builder.pipelineName;
        this.status = Objects.requireNonNull(builder.status);
        this.startTime = Objects.requireNonNull(builder.startTime);
        this.endTime = builder.endTime;
        this.threadName = builder.threadName;
        this.triggeredBy = builder.triggeredBy;
        this.triggerType = builder.triggerType;
        this.isRetry = builder.isRetry;
        this.retryAttempt = builder.retryAttempt;
        this.resultMessage = builder.resultMessage;
        this.errorMessage = builder.errorMessage;
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
    
    public JobStatus getStatus() {
        return status;
    }
    
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    public LocalDateTime getEndTime() {
        return endTime;
    }
    
    public String getThreadName() {
        return threadName;
    }
    
    public String getTriggeredBy() {
        return triggeredBy;
    }
    
    public String getTriggerType() {
        return triggerType;
    }
    
    public boolean isRetry() {
        return isRetry;
    }
    
    public int getRetryAttempt() {
        return retryAttempt;
    }
    
    public String getResultMessage() {
        return resultMessage;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    public long getDurationMillis() {
        if (endTime == null) {
            return 0;
        }
        return java.time.Duration.between(startTime, endTime).toMillis();
    }
    
    public static Builder builder(String executionId, String jobName) {
        return new Builder(executionId, jobName);
    }
    
    public static final class Builder {
        private final String executionId;
        private final String jobName;
        private String pipelineName;
        private JobStatus status = JobStatus.PENDING;
        private LocalDateTime startTime = LocalDateTime.now();
        private LocalDateTime endTime;
        private String threadName;
        private String triggeredBy = "SYSTEM";
        private String triggerType = "MANUAL";
        private boolean isRetry = false;
        private int retryAttempt = 0;
        private String resultMessage;
        private String errorMessage;
        
        private Builder(String executionId, String jobName) {
            this.executionId = executionId;
            this.jobName = jobName;
        }
        
        public Builder pipelineName(String pipelineName) {
            this.pipelineName = pipelineName;
            return this;
        }
        
        public Builder status(JobStatus status) {
            this.status = status;
            return this;
        }
        
        public Builder startTime(LocalDateTime startTime) {
            this.startTime = startTime;
            return this;
        }
        
        public Builder endTime(LocalDateTime endTime) {
            this.endTime = endTime;
            return this;
        }
        
        public Builder threadName(String threadName) {
            this.threadName = threadName;
            return this;
        }
        
        public Builder triggeredBy(String triggeredBy) {
            this.triggeredBy = triggeredBy;
            return this;
        }
        
        public Builder triggerType(String triggerType) {
            this.triggerType = triggerType;
            return this;
        }
        
        public Builder isRetry(boolean isRetry) {
            this.isRetry = isRetry;
            return this;
        }
        
        public Builder retryAttempt(int retryAttempt) {
            this.retryAttempt = retryAttempt;
            return this;
        }
        
        public Builder resultMessage(String resultMessage) {
            this.resultMessage = resultMessage;
            return this;
        }
        
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }
        
        public JobExecutionInfo build() {
            return new JobExecutionInfo(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("JobExecutionInfo{executionId='%s', jobName='%s', status=%s, " +
            "startTime=%s, duration=%dms}",
            executionId, jobName, status, startTime, getDurationMillis());
    }
}
