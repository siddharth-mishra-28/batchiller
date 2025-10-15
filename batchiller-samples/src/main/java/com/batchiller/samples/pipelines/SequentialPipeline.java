package com.batchiller.samples.pipelines;

import com.batchiller.api.BatchJob;
import com.batchiller.api.Pipeline;
import com.batchiller.api.PipelineFlow;
import com.batchiller.samples.jobs.CpuIntensiveJob;
import com.batchiller.samples.jobs.IoIntensiveJob;

import java.util.List;

/**
 * A pipeline that runs a CPU-intensive job and an I/O-intensive job sequentially.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class SequentialPipeline implements Pipeline {
    @Override
    public String getName() {
        return "SequentialPipeline";
    }

    @Override
    public String getDescription() {
        return "A pipeline that runs a CPU-intensive job and an I/O-intensive job sequentially.";
    }

    @Override
    public List<BatchJob> getJobs() {
        return List.of(new CpuIntensiveJob(), new IoIntensiveJob());
    }

    @Override
    public PipelineFlow getFlow() {
        return PipelineFlow.SEQUENTIAL;
    }
}
