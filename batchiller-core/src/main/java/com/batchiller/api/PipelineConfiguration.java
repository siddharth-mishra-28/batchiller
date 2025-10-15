package com.batchiller.api;

import java.util.*;

/**
 * Configuration for a pipeline execution.
 * This class encapsulates all configuration parameters for a pipeline,
 * including flow type, job configurations, dependencies, and scheduling.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public final class PipelineConfiguration {
    
    private final String pipelineName;
    private final PipelineFlow flow;
    private final List<JobConfiguration> jobConfigurations;
    private final Map<String, List<String>> dependencies;
    private final Map<String, ConditionalFlow> conditionalFlows;
    private final String cronExpression;
    private final boolean enabled;
    private final boolean allowManualExecution;
    
    private PipelineConfiguration(Builder builder) {
        this.pipelineName = Objects.requireNonNull(builder.pipelineName, "Pipeline name cannot be null");
        this.flow = Objects.requireNonNull(builder.flow, "Pipeline flow cannot be null");
        this.jobConfigurations = Collections.unmodifiableList(new ArrayList<>(builder.jobConfigurations));
        this.dependencies = Collections.unmodifiableMap(new HashMap<>(builder.dependencies));
        this.conditionalFlows = Collections.unmodifiableMap(new HashMap<>(builder.conditionalFlows));
        this.cronExpression = builder.cronExpression;
        this.enabled = builder.enabled;
        this.allowManualExecution = builder.allowManualExecution;
        
        validate();
    }
    
    private void validate() {
        if (pipelineName.trim().isEmpty()) {
            throw new IllegalArgumentException("Pipeline name cannot be empty");
        }
        if (jobConfigurations.isEmpty()) {
            throw new IllegalArgumentException("Pipeline must have at least one job");
        }
        if (flow == PipelineFlow.CONDITIONAL && conditionalFlows.isEmpty()) {
            throw new IllegalArgumentException("CONDITIONAL flow requires conditional flow rules");
        }
    }
    
    public String getPipelineName() {
        return pipelineName;
    }
    
    public PipelineFlow getFlow() {
        return flow;
    }
    
    public List<JobConfiguration> getJobConfigurations() {
        return jobConfigurations;
    }
    
    public Map<String, List<String>> getDependencies() {
        return dependencies;
    }
    
    public Map<String, ConditionalFlow> getConditionalFlows() {
        return conditionalFlows;
    }
    
    public String getCronExpression() {
        return cronExpression;
    }
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public boolean isAllowManualExecution() {
        return allowManualExecution;
    }
    
    public boolean isScheduled() {
        return cronExpression != null && !cronExpression.isEmpty();
    }
    
    public static Builder builder(String pipelineName, PipelineFlow flow) {
        return new Builder(pipelineName, flow);
    }
    
    public static final class Builder {
        private final String pipelineName;
        private final PipelineFlow flow;
        private List<JobConfiguration> jobConfigurations = new ArrayList<>();
        private Map<String, List<String>> dependencies = new HashMap<>();
        private Map<String, ConditionalFlow> conditionalFlows = new HashMap<>();
        private String cronExpression;
        private boolean enabled = true;
        private boolean allowManualExecution = true;
        
        private Builder(String pipelineName, PipelineFlow flow) {
            this.pipelineName = pipelineName;
            this.flow = flow;
        }
        
        public Builder addJob(JobConfiguration jobConfig) {
            this.jobConfigurations.add(jobConfig);
            return this;
        }
        
        public Builder jobConfigurations(List<JobConfiguration> jobConfigurations) {
            this.jobConfigurations = new ArrayList<>(jobConfigurations);
            return this;
        }
        
        public Builder addDependency(String jobName, String dependsOn) {
            this.dependencies.computeIfAbsent(jobName, k -> new ArrayList<>()).add(dependsOn);
            return this;
        }
        
        public Builder dependencies(Map<String, List<String>> dependencies) {
            this.dependencies = new HashMap<>(dependencies);
            return this;
        }
        
        public Builder addConditionalFlow(String jobName, ConditionalFlow conditionalFlow) {
            this.conditionalFlows.put(jobName, conditionalFlow);
            return this;
        }
        
        public Builder conditionalFlows(Map<String, ConditionalFlow> conditionalFlows) {
            this.conditionalFlows = new HashMap<>(conditionalFlows);
            return this;
        }
        
        public Builder cronExpression(String cronExpression) {
            this.cronExpression = cronExpression;
            return this;
        }
        
        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }
        
        public Builder allowManualExecution(boolean allowManualExecution) {
            this.allowManualExecution = allowManualExecution;
            return this;
        }
        
        public PipelineConfiguration build() {
            return new PipelineConfiguration(this);
        }
    }
    
    public static final class ConditionalFlow {
        private final String executeOnSuccess;
        private final String executeOnFailure;
        
        private ConditionalFlow(String executeOnSuccess, String executeOnFailure) {
            this.executeOnSuccess = executeOnSuccess;
            this.executeOnFailure = executeOnFailure;
        }
        
        public String getExecuteOnSuccess() {
            return executeOnSuccess;
        }
        
        public String getExecuteOnFailure() {
            return executeOnFailure;
        }
        
        public static ConditionalFlow onSuccess(String jobName) {
            return new ConditionalFlow(jobName, null);
        }
        
        public static ConditionalFlow onFailure(String jobName) {
            return new ConditionalFlow(null, jobName);
        }
        
        public static ConditionalFlow onBoth(String onSuccess, String onFailure) {
            return new ConditionalFlow(onSuccess, onFailure);
        }
    }
    
    @Override
    public String toString() {
        return String.format("PipelineConfiguration{pipelineName='%s', flow=%s, jobs=%d, " +
            "cronExpression='%s', enabled=%s}",
            pipelineName, flow, jobConfigurations.size(), cronExpression, enabled);
    }
}
