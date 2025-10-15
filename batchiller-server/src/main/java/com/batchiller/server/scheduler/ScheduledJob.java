package com.batchiller.server.scheduler;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * A class that represents a scheduled job.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class ScheduledJob {
    private String id;
    private String name;
    private String type; // JOB or PIPELINE
    private String targetName; // Job name or Pipeline name
    private String cronExpression;
    private Map<String, Object> parameters;
    private boolean enabled;
    private LocalDateTime lastExecutionTime;
    private LocalDateTime nextExecutionTime;

    // Constructors
    public ScheduledJob() {
    }

    public ScheduledJob(String id, String name, String type, String targetName, String cronExpression, Map<String, Object> parameters, boolean enabled, LocalDateTime lastExecutionTime, LocalDateTime nextExecutionTime) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.targetName = targetName;
        this.cronExpression = cronExpression;
        this.parameters = parameters;
        this.enabled = enabled;
        this.lastExecutionTime = lastExecutionTime;
        this.nextExecutionTime = nextExecutionTime;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public void setParameters(Map<String, Object> parameters) {
        this.parameters = parameters;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public LocalDateTime getLastExecutionTime() {
        return lastExecutionTime;
    }

    public void setLastExecutionTime(LocalDateTime lastExecutionTime) {
        this.lastExecutionTime = lastExecutionTime;
    }

    public LocalDateTime getNextExecutionTime() {
        return nextExecutionTime;
    }

    public void setNextExecutionTime(LocalDateTime nextExecutionTime) {
        this.nextExecutionTime = nextExecutionTime;
    }

    @Override
    public String toString() {
        return "ScheduledJob{"
               + "id='" + id + "'"
               + ", name='" + name + "'"
               + ", type='" + type + "'"
               + ", targetName='" + targetName + "'"
               + ", cronExpression='" + cronExpression + "'"
               + ", parameters=" + parameters
               + ", enabled=" + enabled
               + ", lastExecutionTime=" + lastExecutionTime
               + ", nextExecutionTime=" + nextExecutionTime
               + "}";
    }
}
