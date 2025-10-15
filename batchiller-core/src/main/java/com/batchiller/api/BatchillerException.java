package com.batchiller.api;

/**
 * Base exception for all Batchiller-specific exceptions.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class BatchillerException extends RuntimeException {
    
    public BatchillerException(String message) {
        super(message);
    }
    
    public BatchillerException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public BatchillerException(Throwable cause) {
        super(cause);
    }
}
