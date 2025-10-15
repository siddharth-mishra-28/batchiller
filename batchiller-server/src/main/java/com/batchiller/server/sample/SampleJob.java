package com.batchiller.server.sample;

import com.batchiller.api.BatchJob;
import com.batchiller.api.JobContext;
import com.batchiller.api.JobResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * A sample batch job that demonstrates the basic functionality of the Batchiller framework.
 * This job simulates a simple task, logs its progress, and returns a successful result.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class SampleJob implements BatchJob {
    
    private static final Logger logger = LoggerFactory.getLogger(SampleJob.class);
    
    @Override
    public String getName() {
        return "SampleJob";
    }
    
    @Override
    public String getDescription() {
        return "A sample batch job that demonstrates the Batchiller framework";
    }
    
    @Override
    public CompletableFuture<JobResult> execute(JobContext context) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                logger.info("SampleJob started - Execution ID: {}", context.getExecutionId());
                
                Thread.sleep(2000);
                
                int randomValue = (int) (Math.random() * 100);
                logger.info("SampleJob processed value: {}", randomValue);
                
                return JobResult.success("SampleJob completed successfully", 
                    Map.of("processedValue", randomValue, "timestamp", System.currentTimeMillis()));
                    
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return JobResult.failure(e);
            }
        });
    }
}
