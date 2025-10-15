package com.batchiller.api;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

/**
 * Default immutable implementation of {@link JobResult}.
 * 
 * @author Siddharth Mishra <mishra.siddharth90@gmail.com>
 * @version 1.0
 */
final class DefaultJobResult implements JobResult {
    
    private final Status status;
    private final String message;
    private final Map<String, Object> data;
    private final Throwable exception;
    private final LocalDateTime completionTime;
    
    DefaultJobResult(Status status, String message, Map<String, Object> data, Throwable exception) {
        this.status = status;
        this.message = message;
        this.data = data != null ? Collections.unmodifiableMap(data) : Collections.emptyMap();
        this.exception = exception;
        this.completionTime = LocalDateTime.now();
    }
    
    @Override
    public Status getStatus() {
        return status;
    }
    
    @Override
    public String getMessage() {
        return message;
    }
    
    @Override
    public Map<String, Object> getData() {
        return data;
    }
    
    @Override
    public Optional<Throwable> getException() {
        return Optional.ofNullable(exception);
    }
    
    @Override
    public LocalDateTime getCompletionTime() {
        return completionTime;
    }
    
    @Override
    public String toString() {
        return String.format("JobResult{status=%s, message='%s', data=%s, hasException=%s, completionTime=%s}",
            status, message, data, exception != null, completionTime);
    }
}
