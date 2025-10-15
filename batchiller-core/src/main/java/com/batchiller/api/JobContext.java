package com.batchiller.api;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Provides contextual information and parameters for batch job execution.
 * The {@code JobContext} is passed to each {@link BatchJob} during execution
 * and contains all necessary information about the execution environment,
 * parameters, and runtime metadata.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public interface JobContext {
    
    String getExecutionId();
    
    String getJobName();
    
    String getPipelineName();
    
    LocalDateTime getStartTime();
    
    Map<String, Object> getParameters();
    
    default Optional<Object> getParameter(String key) {
        return Optional.ofNullable(getParameters().get(key));
    }
    
    @SuppressWarnings("unchecked")
    default <T> Optional<T> getParameter(String key, Class<T> type) {
        return getParameter(key)
            .filter(type::isInstance)
            .map(value -> (T) value);
    }
    
    String getTriggerType();
    
    String getTriggeredBy();
    
    boolean isRetry();
    
    int getRetryAttempt();
    
    default String getThreadName() {
        return Thread.currentThread().getName();
    }
}
