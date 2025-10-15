package com.batchiller.api;

/**
 * Exception thrown when job configuration is invalid.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class JobConfigurationException extends BatchillerException {
    
    public JobConfigurationException(String message) {
        super(message);
    }
    
    public JobConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
