package com.batchiller.server.context;

import com.batchiller.api.JobContext;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

/**
 * Default implementation of {@link JobContext}.
 * Provides contextual information and parameters for batch job execution.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class DefaultJobContext implements JobContext {
    
    private final String executionId;
    private final String jobName;
    private final String pipelineName;
    private final LocalDateTime startTime;
    private final Map<String, Object> parameters;
    private final String triggerType;
    private final String triggeredBy;
    private final boolean isRetry;
    private final int retryAttempt;
    
    public DefaultJobContext(String executionId, String jobName, String pipelineName,
                           Map<String, Object> parameters, String triggerType, String triggeredBy,
                           boolean isRetry, int retryAttempt) {
        this.executionId = executionId;
        this.jobName = jobName;
        this.pipelineName = pipelineName;
        this.startTime = LocalDateTime.now();
        this.parameters = Collections.unmodifiableMap(parameters);
        this.triggerType = triggerType;
        this.triggeredBy = triggeredBy;
        this.isRetry = isRetry;
        this.retryAttempt = retryAttempt;
    }
    
    @Override
    public String getExecutionId() {
        return executionId;
    }
    
    @Override
    public String getJobName() {
        return jobName;
    }
    
    @Override
    public String getPipelineName() {
        return pipelineName;
    }
    
    @Override
    public LocalDateTime getStartTime() {
        return startTime;
    }
    
    @Override
    public Map<String, Object> getParameters() {
        return parameters;
    }
    
    @Override
    public String getTriggerType() {
        return triggerType;
    }
    
    @Override
    public String getTriggeredBy() {
        return triggeredBy;
    }
    
    @Override
    public boolean isRetry() {
        return isRetry;
    }
    
    @Override
    public int getRetryAttempt() {
        return retryAttempt;
    }
}
