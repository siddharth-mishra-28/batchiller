package com.batchiller.api;

/**
 * Exception thrown when a pipeline execution fails.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class PipelineExecutionException extends BatchillerException {
    
    private final String pipelineName;
    
    public PipelineExecutionException(String pipelineName, String message) {
        super(String.format("Pipeline '%s' failed: %s", pipelineName, message));
        this.pipelineName = pipelineName;
    }
    
    public PipelineExecutionException(String pipelineName, Throwable cause) {
        super(String.format("Pipeline '%s' failed", pipelineName), cause);
        this.pipelineName = pipelineName;
    }
    
    public String getPipelineName() {
        return pipelineName;
    }
}
