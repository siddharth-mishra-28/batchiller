package com.batchiller.server.scheduler;

import com.batchiller.api.JobConfiguration;
import com.batchiller.api.PipelineConfiguration;
import com.batchiller.server.config.BatchillerConfiguration;
import com.batchiller.server.database.DatabaseManager;
import com.batchiller.server.engine.JobExecutionEngine;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A class that schedules jobs using Quartz.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class JobScheduler {
    
    private static final Logger logger = LoggerFactory.getLogger(JobScheduler.class);
    private final Scheduler scheduler;
    private final JobExecutionEngine engine;
    private final DatabaseManager databaseManager;
    private final BatchillerConfiguration config;
    
    public JobScheduler(JobExecutionEngine engine, DatabaseManager databaseManager, BatchillerConfiguration config) {
        this.engine = engine;
        this.databaseManager = databaseManager;
        this.config = config;
        try {
            this.scheduler = StdSchedulerFactory.getDefaultScheduler();
            this.scheduler.start();
            logger.info("Quartz Scheduler started");
            loadAndSchedulePersistedJobs();
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to initialize scheduler", e);
        }
    }

    private void loadAndSchedulePersistedJobs() {
        List<ScheduledJob> scheduledJobs = databaseManager.getAllScheduledJobs();
        for (ScheduledJob scheduledJob : scheduledJobs) {
            if (scheduledJob.isEnabled()) {
                try {
                    schedule(scheduledJob);
                    logger.info("Rescheduled persisted job: {} with cron: {}", scheduledJob.getName(), scheduledJob.getCronExpression());
                } catch (SchedulerException e) {
                    logger.error("Failed to reschedule persisted job: " + scheduledJob.getName(), e);
                }
            }
        }
    }

    public void schedule(ScheduledJob scheduledJob) throws SchedulerException {
        if (scheduledJob.getId() == null) {
            scheduledJob.setId(java.util.UUID.randomUUID().toString());
        }

        String cronExpression = toQuartzCron(scheduledJob.getCronExpression());
        
        if (cronExpression != null) {
            String[] parts = cronExpression.trim().split("\\s+");
            if (parts.length == 6) {
                if (parts[3].equals("*") && parts[5].equals("*")) {
                    parts[5] = "?";
                    cronExpression = String.join(" ", parts);
                }
            }
        }
        
        scheduledJob.setCronExpression(cronExpression);

        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("scheduledJob", scheduledJob);
        jobDataMap.put("engine", engine);
        jobDataMap.put("databaseManager", databaseManager);

        Class<? extends org.quartz.Job> jobClass = null;
        if ("JOB".equals(scheduledJob.getType())) {
            jobClass = QuartzJobWrapper.class;
        } else if ("PIPELINE".equals(scheduledJob.getType())) {
            jobClass = QuartzPipelineWrapper.class;
        }

        if (jobClass == null) {
            logger.error("Invalid scheduled job type: {}", scheduledJob.getType());
            return;
        }

        JobDetail jobDetail = JobBuilder.newJob(jobClass)
                .withIdentity(scheduledJob.getId(), "batchiller-scheduled-tasks")
                .usingJobData(jobDataMap)
                .build();

        CronTrigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(scheduledJob.getId() + "-trigger", "batchiller-scheduled-triggers")
                .withSchedule(CronScheduleBuilder.cronSchedule(scheduledJob.getCronExpression()))
                .forJob(jobDetail)
                .build();

        scheduler.scheduleJob(jobDetail, trigger);
        logger.info("Scheduled {}: {} with cron: {}", scheduledJob.getType(), scheduledJob.getName(), scheduledJob.getCronExpression());

        // Update next execution time
        java.util.Date nextFireTime = trigger.getFireTimeAfter(new java.util.Date());
        if (nextFireTime != null) {
            scheduledJob.setNextExecutionTime(java.time.LocalDateTime.ofInstant(nextFireTime.toInstant(), java.time.ZoneId.systemDefault()));
        }
        databaseManager.saveScheduledJob(scheduledJob);
    }
    
    public void scheduleJob(JobConfiguration config) {
        if (config.getCronExpression() == null || config.getCronExpression().isEmpty()) {
            return;
        }
        ScheduledJob scheduledJob = new ScheduledJob(
            java.util.UUID.randomUUID().toString(),
            config.getJobName(),
            "JOB",
            config.getJobName(),
            config.getCronExpression(),
            java.util.Collections.emptyMap(), // Assuming no parameters for now
            true,
            null,
            null
        );
        try {
            schedule(scheduledJob);
        } catch (SchedulerException e) {
            logger.error("Failed to schedule job: " + config.getJobName(), e);
        }
    }
    
    public void schedulePipeline(PipelineConfiguration config) {
        if (config.getCronExpression() == null || config.getCronExpression().isEmpty()) {
            return;
        }
        ScheduledJob scheduledJob = new ScheduledJob(
            java.util.UUID.randomUUID().toString(),
            config.getPipelineName(),
            "PIPELINE",
            config.getPipelineName(),
            config.getCronExpression(),
            java.util.Collections.emptyMap(), // Assuming no parameters for now
            true,
            null,
            null
        );
        try {
            schedule(scheduledJob);
        } catch (SchedulerException e) {
            logger.error("Failed to schedule pipeline: " + config.getPipelineName(), e);
        }
    }
    
    public void updateSchedule(ScheduledJob scheduledJob) throws SchedulerException {
        unschedule(scheduledJob.getId());
        if (scheduledJob.isEnabled()) {
            schedule(scheduledJob);
        }
        databaseManager.updateScheduledJob(scheduledJob);
        logger.info("Updated scheduled job: {}", scheduledJob.getName());
    }

    public void unschedule(String scheduledJobId) throws SchedulerException {
        scheduler.unscheduleJob(new TriggerKey(scheduledJobId + "-trigger", "batchiller-scheduled-triggers"));
        scheduler.deleteJob(new JobKey(scheduledJobId, "batchiller-scheduled-tasks"));
        databaseManager.deleteScheduledJob(scheduledJobId);
        logger.info("Unscheduled job: {}", scheduledJobId);
    }

    public List<ScheduledJob> getAllScheduledJobs() {
        return databaseManager.getAllScheduledJobs();
    }

    public void shutdown() {
        try {
            if (scheduler != null && scheduler.isStarted()) {
                scheduler.shutdown(true);
                logger.info("Quartz Scheduler shut down");
            }
        } catch (SchedulerException e) {
            logger.error("Failed to shutdown scheduler", e);
        }
    }

    private String toQuartzCron(String cron) {
        if (cron == null || cron.trim().isEmpty()) {
            return null;
        }
        String[] parts = cron.trim().split("\\s+");
        if (parts.length == 5) {
            return "0 " + cron;
        }
        return cron;
    }

    public static class QuartzJobWrapper implements Job {
        private static final Logger jobLogger = LoggerFactory.getLogger(QuartzJobWrapper.class);

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            JobDataMap dataMap = context.getJobDetail().getJobDataMap();
            ScheduledJob scheduledJob = (ScheduledJob) dataMap.get("scheduledJob");
            JobExecutionEngine engine = (JobExecutionEngine) dataMap.get("engine");
            DatabaseManager databaseManager = (DatabaseManager) dataMap.get("databaseManager");

            if (scheduledJob == null || engine == null || databaseManager == null) {
                jobLogger.error("ScheduledJob, JobExecutionEngine or DatabaseManager not found in JobDataMap for job: {}", context.getJobDetail().getKey());
                throw new JobExecutionException("Missing scheduled job, engine or database manager");
            }

            jobLogger.info("Executing scheduled job: {} (Target: {})", scheduledJob.getName(), scheduledJob.getTargetName());

            try {
                engine.executeJob(scheduledJob.getTargetName(), scheduledJob.getParameters(), "SYSTEM", "SCHEDULED");
                scheduledJob.setLastExecutionTime(java.time.LocalDateTime.now());
                // Calculate next execution time
                java.util.Date nextFireTime = context.getTrigger().getFireTimeAfter(new java.util.Date());
                if (nextFireTime != null) {
                    scheduledJob.setNextExecutionTime(java.time.LocalDateTime.ofInstant(nextFireTime.toInstant(), java.time.ZoneId.systemDefault()));
                }
                databaseManager.updateScheduledJob(scheduledJob);
            } catch (Exception e) {
                jobLogger.error("Error executing scheduled job: {} - {}", scheduledJob.getName(), e.getMessage(), e);
                throw new JobExecutionException(e);
            }
        }
    }
    
    public static class QuartzPipelineWrapper implements Job {
        private static final Logger pipelineLogger = LoggerFactory.getLogger(QuartzPipelineWrapper.class);

        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            JobDataMap dataMap = context.getJobDetail().getJobDataMap();
            ScheduledJob scheduledJob = (ScheduledJob) dataMap.get("scheduledJob");
            JobExecutionEngine engine = (JobExecutionEngine) dataMap.get("engine");
            DatabaseManager databaseManager = (DatabaseManager) dataMap.get("databaseManager");

            if (scheduledJob == null || engine == null || databaseManager == null) {
                pipelineLogger.error("ScheduledJob, JobExecutionEngine or DatabaseManager not found in JobDataMap for pipeline: {}", context.getJobDetail().getKey());
                throw new JobExecutionException("Missing scheduled job, engine or database manager");
            }

            pipelineLogger.info("Executing scheduled pipeline: {} (Target: {})", scheduledJob.getName(), scheduledJob.getTargetName());

            try {
                engine.executePipeline(scheduledJob.getTargetName(), "SYSTEM", "SCHEDULED");
                scheduledJob.setLastExecutionTime(java.time.LocalDateTime.now());
                // Calculate next execution time
                java.util.Date nextFireTime = context.getTrigger().getFireTimeAfter(new java.util.Date());
                if (nextFireTime != null) {
                    scheduledJob.setNextExecutionTime(java.time.LocalDateTime.ofInstant(nextFireTime.toInstant(), java.time.ZoneId.systemDefault()));
                }
                databaseManager.updateScheduledJob(scheduledJob);
            } catch (Exception e) {
                pipelineLogger.error("Error executing scheduled pipeline: {} - {}", scheduledJob.getName(), e.getMessage(), e);
                throw new JobExecutionException(e);
            }
        }
    }
}
