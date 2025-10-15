package com.batchiller.api;

/**
 * Defines the execution flow strategy for a pipeline.
 * The flow determines how jobs within a pipeline are orchestrated
 * and executed by the orchestration engine.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public enum PipelineFlow {
    
    SEQUENTIAL,
    
    PARALLEL,
    
    CONDITIONAL
}
