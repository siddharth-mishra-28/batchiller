package com.batchiller.api;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * Represents the result of a batch job execution.
 * A {@code JobResult} encapsulates the outcome of a job execution, including
 * success/failure status, result data, error information, and execution metadata.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public interface JobResult {
    
    enum Status {
        SUCCESS,
        FAILURE,
        PARTIAL
    }
    
    Status getStatus();
    
    String getMessage();
    
    Map<String, Object> getData();
    
    Optional<Throwable> getException();
    
    LocalDateTime getCompletionTime();
    
    default boolean isSuccess() {
        return getStatus() == Status.SUCCESS;
    }
    
    default boolean isFailure() {
        return getStatus() == Status.FAILURE;
    }
    
    default boolean isPartial() {
        return getStatus() == Status.PARTIAL;
    }
    
    static JobResult success(String message) {
        return success(message, Map.of());
    }
    
    static JobResult success(String message, Map<String, Object> data) {
        return new DefaultJobResult(Status.SUCCESS, message, data, null);
    }
    
    static JobResult failure(Throwable exception) {
        return new DefaultJobResult(Status.FAILURE, exception.getMessage(), Map.of(), exception);
    }
    
    static JobResult failure(String message) {
        return new DefaultJobResult(Status.FAILURE, message, Map.of(), null);
    }
    
    static JobResult partial(String message, Map<String, Object> data) {
        return new DefaultJobResult(Status.PARTIAL, message, data, null);
    }
}
