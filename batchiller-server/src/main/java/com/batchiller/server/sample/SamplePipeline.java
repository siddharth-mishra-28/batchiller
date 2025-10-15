package com.batchiller.server.sample;

import com.batchiller.api.BatchJob;
import com.batchiller.api.Pipeline;
import com.batchiller.api.PipelineFlow;

import java.util.List;

/**
 * A sample pipeline that demonstrates the sequential execution of jobs.
 * This pipeline contains a single {@link SampleJob} to illustrate basic pipeline functionality.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class SamplePipeline implements Pipeline {
    
    @Override
    public String getName() {
        return "SamplePipeline";
    }
    
    @Override
    public String getDescription() {
        return "A sample pipeline demonstrating sequential execution";
    }
    
    @Override
    public List<BatchJob> getJobs() {
        return List.of(new SampleJob());
    }
    
    @Override
    public PipelineFlow getFlow() {
        return PipelineFlow.SEQUENTIAL;
    }
}
