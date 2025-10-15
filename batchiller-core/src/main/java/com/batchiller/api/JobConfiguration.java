package com.batchiller.api;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration for a batch job execution.
 * This class encapsulates all configuration parameters for a job,
 * including execution parameters, timeout settings, retry policy, and
 * scheduling information.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public final class JobConfiguration {
    
    private final String jobName;
    private final Map<String, Object> parameters;
    private final long timeoutMillis;
    private final boolean retryable;
    private final int maxRetries;
    private final String cronExpression;
    private final boolean enabled;
    
    private JobConfiguration(Builder builder) {
        this.jobName = Objects.requireNonNull(builder.jobName, "Job name cannot be null");
        this.parameters = Collections.unmodifiableMap(new HashMap<>(builder.parameters));
        this.timeoutMillis = builder.timeoutMillis;
        this.retryable = builder.retryable;
        this.maxRetries = builder.maxRetries;
        this.cronExpression = builder.cronExpression;
        this.enabled = builder.enabled;
        
        validate();
    }
    
    private void validate() {
        if (jobName.trim().isEmpty()) {
            throw new IllegalArgumentException("Job name cannot be empty");
        }
        if (timeoutMillis < 0) {
            throw new IllegalArgumentException("Timeout cannot be negative");
        }
        if (maxRetries < 0) {
            throw new IllegalArgumentException("Max retries cannot be negative");
        }
    }
    
    public String getJobName() {
        return jobName;
    }
    
    public Map<String, Object> getParameters() {
        return parameters;
    }
    
    public long getTimeoutMillis() {
        return timeoutMillis;
    }
    
    public boolean isRetryable() {
        return retryable;
    }
    
    public int getMaxRetries() {
        return maxRetries;
    }
    
    public String getCronExpression() {
        return cronExpression;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public boolean isScheduled() {
        return cronExpression != null && !cronExpression.isEmpty();
    }
    
    public static Builder builder(String jobName) {
        return new Builder(jobName);
    }
    
    public static final class Builder {
        private final String jobName;
        private Map<String, Object> parameters = new HashMap<>();
        private long timeoutMillis = 300000L;
        private boolean retryable = true;
        private int maxRetries = 3;
        private String cronExpression;
        private boolean enabled = true;
        
        private Builder(String jobName) {
            this.jobName = jobName;
        }
        
        public Builder parameters(Map<String, Object> parameters) {
            this.parameters = new HashMap<>(parameters);
            return this;
        }
        
        public Builder parameter(String key, Object value) {
            this.parameters.put(key, value);
            return this;
        }
        
        public Builder timeoutMillis(long timeoutMillis) {
            this.timeoutMillis = timeoutMillis;
            return this;
        }
        
        public Builder retryable(boolean retryable) {
            this.retryable = retryable;
            return this;
        }
        
        public Builder maxRetries(int maxRetries) {
            this.maxRetries = maxRetries;
            return this;
        }
        
        public Builder cronExpression(String cronExpression) {
            this.cronExpression = cronExpression;
            return this;
        }
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public JobConfiguration build() {
            return new JobConfiguration(this);
        }
    }
    
    @Override
    public String toString() {
        return String.format("JobConfiguration{jobName='%s', parameters=%s, timeoutMillis=%d, " +
            "retryable=%s, maxRetries=%d, cronExpression='%s', enabled=%s}",
            jobName, parameters, timeoutMillis, retryable, maxRetries, cronExpression, enabled);
    }
}
