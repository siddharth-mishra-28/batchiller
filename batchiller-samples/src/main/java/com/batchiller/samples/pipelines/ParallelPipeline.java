package com.batchiller.samples.pipelines;

import com.batchiller.api.BatchJob;
import com.batchiller.api.Pipeline;
import com.batchiller.api.PipelineFlow;
import com.batchiller.samples.jobs.CpuIntensiveJob;
import com.batchiller.samples.jobs.IoIntensiveJob;

import java.util.List;

/**
 * A pipeline that runs a CPU-intensive job and an I/O-intensive job in parallel.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class ParallelPipeline implements Pipeline {
    @Override
    public String getName() {
        return "ParallelPipeline";
    }

    @Override
    public String getDescription() {
        return "A pipeline that runs a CPU-intensive job and an I/O-intensive job in parallel.";
    }

    @Override
    public List<BatchJob> getJobs() {
        return List.of(new CpuIntensiveJob(), new IoIntensiveJob());
    }

    @Override
    public PipelineFlow getFlow() {
        return PipelineFlow.PARALLEL;
    }
}
