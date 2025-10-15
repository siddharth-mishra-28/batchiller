package com.batchiller.server.database;

import com.batchiller.api.JobExecutionInfo;
import com.batchiller.api.JobStatus;
import com.batchiller.server.config.BatchillerConfiguration;
import com.batchiller.server.scheduler.ScheduledJob;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A class that manages the database.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class DatabaseManager {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseManager.class);
    private final BatchillerConfiguration config;
    private Connection connection;
    private final ObjectMapper objectMapper;
    
    public DatabaseManager(BatchillerConfiguration config) {
        this.config = config;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        initialize();
    }
    
    private void initialize() {
        try {
            String url = config.getDatabaseUrl();
            String username = config.getDatabaseUsername();
            String password = config.getDatabasePassword();
            
            connection = DriverManager.getConnection(url, username, password);
            createTables();
            logger.info("Database initialized successfully: {}", url);
        } catch (SQLException e) {
            logger.error("Failed to initialize database", e);
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    private void createTables() throws SQLException {
        String createJobExecutionsTable = """
            CREATE TABLE IF NOT EXISTS job_executions (
                execution_id VARCHAR(255) PRIMARY KEY,
                job_name VARCHAR(255) NOT NULL,
                pipeline_name VARCHAR(255),
                status VARCHAR(50) NOT NULL,
                start_time TIMESTAMP NOT NULL,
                end_time TIMESTAMP,
                thread_name VARCHAR(255),
                triggered_by VARCHAR(255),
                trigger_type VARCHAR(50),
                is_retry BOOLEAN,
                retry_attempt INT,
                result_message TEXT,
                error_message TEXT,
                duration_millis BIGINT
            )
        """;
        
        String createPipelineConfigsTable = """
            CREATE TABLE IF NOT EXISTS pipeline_configurations (
                id VARCHAR(255) PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                description TEXT,
                flow_type VARCHAR(50) NOT NULL,
                config_json TEXT NOT NULL,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL
            )
        """;

        String createScheduledJobsTable = """
            CREATE TABLE IF NOT EXISTS scheduled_jobs (
                id VARCHAR(255) PRIMARY KEY,
                name VARCHAR(255) NOT NULL,
                type VARCHAR(50) NOT NULL,
                target_name VARCHAR(255) NOT NULL,
                cron_expression VARCHAR(255) NOT NULL,
                parameters TEXT,
                enabled BOOLEAN NOT NULL,
                last_execution_time TIMESTAMP,
                next_execution_time TIMESTAMP,
                created_at TIMESTAMP NOT NULL,
                updated_at TIMESTAMP NOT NULL
            )
        """;
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createJobExecutionsTable);
            stmt.execute(createPipelineConfigsTable);
            stmt.execute(createScheduledJobsTable);
        }
    }
    
    public void saveJobExecution(JobExecutionInfo info) {
        String sql = """
            INSERT INTO job_executions 
            (execution_id, job_name, pipeline_name, status, start_time, end_time, 
             thread_name, triggered_by, trigger_type, is_retry, retry_attempt, 
             result_message, error_message, duration_millis)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, info.getExecutionId());
            pstmt.setString(2, info.getJobName());
            pstmt.setString(3, info.getPipelineName());
            pstmt.setString(4, info.getStatus().name());
            pstmt.setTimestamp(5, Timestamp.valueOf(info.getStartTime()));
            pstmt.setTimestamp(6, info.getEndTime() != null ? Timestamp.valueOf(info.getEndTime()) : null);
            pstmt.setString(7, info.getThreadName());
            pstmt.setString(8, info.getTriggeredBy());
            pstmt.setString(9, info.getTriggerType());
            pstmt.setBoolean(10, info.isRetry());
            pstmt.setInt(11, info.getRetryAttempt());
            pstmt.setString(12, info.getResultMessage());
            pstmt.setString(13, info.getErrorMessage());
            pstmt.setLong(14, info.getDurationMillis());
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to save job execution: " + info.getExecutionId(), e);
        }
    }
    
    public void updateJobExecution(JobExecutionInfo info) {
        String sql = """
            UPDATE job_executions 
            SET status = ?, end_time = ?, result_message = ?, error_message = ?, duration_millis = ?
            WHERE execution_id = ?
        """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, info.getStatus().name());
            pstmt.setTimestamp(2, info.getEndTime() != null ? Timestamp.valueOf(info.getEndTime()) : null);
            pstmt.setString(3, info.getResultMessage());
            pstmt.setString(4, info.getErrorMessage());
            pstmt.setLong(5, info.getDurationMillis());
            pstmt.setString(6, info.getExecutionId());
            
            pstmt.executeUpdate();
        } catch (SQLException e) {
            logger.error("Failed to update job execution: " + info.getExecutionId(), e);
        }
    }
    
    public List<JobExecutionInfo> getJobHistory(String jobName, int limit) {
        String sql = """
            SELECT * FROM job_executions 
            WHERE job_name = ? 
            ORDER BY start_time DESC 
            LIMIT ?
        """;
        
        List<JobExecutionInfo> history = new ArrayList<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, jobName);
            pstmt.setInt(2, limit);
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                history.add(mapResultSetToJobExecutionInfo(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to get job history for: " + jobName, e);
        }
        return history;
    }
    
    public List<JobExecutionInfo> getAllExecutions(int limit) {
        String sql = "SELECT * FROM job_executions ORDER BY start_time DESC LIMIT ?";
        
        List<JobExecutionInfo> executions = new ArrayList<>();
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, limit);
            
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) {
                executions.add(mapResultSetToJobExecutionInfo(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to get all executions", e);
        }
        return executions;
    }
    
    private JobExecutionInfo mapResultSetToJobExecutionInfo(ResultSet rs) throws SQLException {
        return JobExecutionInfo.builder(
                rs.getString("execution_id"),
                rs.getString("job_name")
            )
            .pipelineName(rs.getString("pipeline_name"))
            .status(JobStatus.valueOf(rs.getString("status")))
            .startTime(rs.getTimestamp("start_time").toLocalDateTime())
            .endTime(rs.getTimestamp("end_time") != null ? 
                    rs.getTimestamp("end_time").toLocalDateTime() : null)
            .threadName(rs.getString("thread_name"))
            .triggeredBy(rs.getString("triggered_by"))
            .triggerType(rs.getString("trigger_type"))
            .isRetry(rs.getBoolean("is_retry"))
            .retryAttempt(rs.getInt("retry_attempt"))
            .resultMessage(rs.getString("result_message"))
            .errorMessage(rs.getString("error_message"))
            .build();
    }
    
    public boolean savePipelineConfiguration(String id, String name, String description, String flowType, String configJson) throws SQLException {
        String sql = """
            MERGE INTO pipeline_configurations (id, name, description, flow_type, config_json, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
        """;
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            pstmt.setString(1, id);
            pstmt.setString(2, name);
            pstmt.setString(3, description);
            pstmt.setString(4, flowType);
            pstmt.setString(5, configJson);
            pstmt.setTimestamp(6, now);
            pstmt.setTimestamp(7, now);
            
            int rowsAffected = pstmt.executeUpdate();
            logger.info("Saved pipeline configuration: {} (id: {})", name, id);
            return rowsAffected > 0;
        }
    }
    
    public List<java.util.Map<String, Object>> getAllPipelineConfigurations() {
        String sql = "SELECT * FROM pipeline_configurations ORDER BY updated_at DESC";
        
        List<java.util.Map<String, Object>> configs = new ArrayList<>();
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                configs.add(java.util.Map.of(
                    "id", rs.getString("id"),
                    "name", rs.getString("name"),
                    "description", rs.getString("description") != null ? rs.getString("description") : "",
                    "flowType", rs.getString("flow_type"),
                    "configJson", rs.getString("config_json"),
                    "createdAt", rs.getTimestamp("created_at").toString(),
                    "updatedAt", rs.getTimestamp("updated_at").toString()
                ));
            }
        } catch (SQLException e) {
            logger.error("Failed to get pipeline configurations", e);
        }
        return configs;
    }
    
    public void close() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("Database connection closed");
            } catch (SQLException e) {
                logger.error("Failed to close database connection", e);
            }
        }
    }

    public void saveScheduledJob(ScheduledJob job) {
        String sql = """
            INSERT INTO scheduled_jobs 
            (id, name, type, target_name, cron_expression, parameters, enabled, 
             last_execution_time, next_execution_time, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            pstmt.setString(1, job.getId());
            pstmt.setString(2, job.getName());
            pstmt.setString(3, job.getType());
            pstmt.setString(4, job.getTargetName());
            pstmt.setString(5, job.getCronExpression());
            pstmt.setString(6, job.getParameters() != null ? objectMapper.writeValueAsString(job.getParameters()) : null);
            pstmt.setBoolean(7, job.isEnabled());
            pstmt.setTimestamp(8, job.getLastExecutionTime() != null ? Timestamp.valueOf(job.getLastExecutionTime()) : null);
            pstmt.setTimestamp(9, job.getNextExecutionTime() != null ? Timestamp.valueOf(job.getNextExecutionTime()) : null);
            pstmt.setTimestamp(10, now);
            pstmt.setTimestamp(11, now);
            pstmt.executeUpdate();
            logger.info("Saved scheduled job: {}", job.getName());
        } catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("Failed to save scheduled job: " + job.getName(), e);
        }
    }

    public ScheduledJob getScheduledJob(String id) {
        String sql = "SELECT * FROM scheduled_jobs WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, id);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next()) {
                return mapResultSetToScheduledJob(rs);
            }
        } catch (SQLException e) {
            logger.error("Failed to get scheduled job: " + id, e);
        }
        return null;
    }

    public List<ScheduledJob> getAllScheduledJobs() {
        String sql = "SELECT * FROM scheduled_jobs ORDER BY name";
        List<ScheduledJob> jobs = new ArrayList<>();
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(sql);
            while (rs.next()) {
                jobs.add(mapResultSetToScheduledJob(rs));
            }
        } catch (SQLException e) {
            logger.error("Failed to get all scheduled jobs", e);
        }
        return jobs;
    }

    public void updateScheduledJob(ScheduledJob job) {
        String sql = """
            UPDATE scheduled_jobs 
            SET name = ?, type = ?, target_name = ?, cron_expression = ?, parameters = ?, enabled = ?, 
            last_execution_time = ?, next_execution_time = ?, updated_at = ?
            WHERE id = ?
        """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            Timestamp now = Timestamp.valueOf(LocalDateTime.now());
            pstmt.setString(1, job.getName());
            pstmt.setString(2, job.getType());
            pstmt.setString(3, job.getTargetName());
            pstmt.setString(4, job.getCronExpression());
            pstmt.setString(5, job.getParameters() != null ? objectMapper.writeValueAsString(job.getParameters()) : null);
            pstmt.setBoolean(6, job.isEnabled());
            pstmt.setTimestamp(7, job.getLastExecutionTime() != null ? Timestamp.valueOf(job.getLastExecutionTime()) : null);
            pstmt.setTimestamp(8, job.getNextExecutionTime() != null ? Timestamp.valueOf(job.getNextExecutionTime()) : null);
            pstmt.setTimestamp(9, now);
            pstmt.setString(10, job.getId());
            pstmt.executeUpdate();
            logger.info("Updated scheduled job: {}", job.getName());
        } catch (SQLException | com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("Failed to update scheduled job: " + job.getName(), e);
        }
    }

    public void deleteScheduledJob(String id) {
        String sql = "DELETE FROM scheduled_jobs WHERE id = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, id);
            pstmt.executeUpdate();
            logger.info("Deleted scheduled job: {}", id);
        } catch (SQLException e) {
            logger.error("Failed to delete scheduled job: " + id, e);
        }
    }

    private ScheduledJob mapResultSetToScheduledJob(ResultSet rs) throws SQLException {
        ScheduledJob job = new ScheduledJob();
        job.setId(rs.getString("id"));
        job.setName(rs.getString("name"));
        job.setType(rs.getString("type"));
        job.setTargetName(rs.getString("target_name"));
        job.setCronExpression(rs.getString("cron_expression"));
        try {
            String paramsJson = rs.getString("parameters");
            if (paramsJson != null) {
                job.setParameters(objectMapper.readValue(paramsJson, Map.class));
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            logger.error("Failed to parse parameters for scheduled job: " + job.getId(), e);
        }
        job.setEnabled(rs.getBoolean("enabled"));
        job.setLastExecutionTime(rs.getTimestamp("last_execution_time") != null ? rs.getTimestamp("last_execution_time").toLocalDateTime() : null);
        job.setNextExecutionTime(rs.getTimestamp("next_execution_time") != null ? rs.getTimestamp("next_execution_time").toLocalDateTime() : null);
        return job;
    }
}
