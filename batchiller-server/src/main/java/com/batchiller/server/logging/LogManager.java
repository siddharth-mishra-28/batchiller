package com.batchiller.server.logging;

import com.batchiller.server.config.BatchillerConfiguration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.TimeBasedRollingPolicy;
import ch.qos.logback.classic.spi.ILoggingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages logging for the Batchiller application, including creating job-specific log files
 * and cleaning up old log files based on retention policies.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class LogManager {
    private static final Logger logger = LoggerFactory.getLogger(LogManager.class);
    
    private final BatchillerConfiguration config;
    private final Path logsDirectory;
    private final ScheduledExecutorService cleanupScheduler;
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    
    public LogManager(BatchillerConfiguration config) {
        this.config = config;
        this.logsDirectory = Paths.get(config.getLogDirectory(), "executions");
        this.cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LogCleanup");
            t.setDaemon(true);
            return t;
        });
        
        initializeLogsDirectory();
        scheduleCleanup();
    }
    
    private void initializeLogsDirectory() {
        try {
            Files.createDirectories(logsDirectory);
        } catch (IOException e) {
            logger.error("Failed to create logs directory: {}", e.getMessage());
        }
    }
    
    private void scheduleCleanup() {
        cleanupScheduler.scheduleWithFixedDelay(
            this::cleanupOldLogs,
            1,
            24,
            TimeUnit.HOURS
        );
    }
    
    public String createLogFile(String executionId, String jobName) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String fileName = String.format("%s_%s_%s.log", jobName, executionId, timestamp);
        Path logFile = logsDirectory.resolve(fileName);
        return logFile.toAbsolutePath().toString();
    }
    
    public File getLogFile(String executionId) {
        try {
            return Files.list(logsDirectory)
                .filter(path -> path.getFileName().toString().contains(executionId))
                .map(Path::toFile)
                .findFirst()
                .orElse(null);
        } catch (IOException e) {
            logger.error("Failed to find log file: {}", e.getMessage());
            return null;
        }
    }
    
    public boolean isLogAvailable(String executionId) {
        return getLogFile(executionId) != null;
    }
    
    private void cleanupOldLogs() {
        try {
            int retentionDays = config.getLogRetentionDays();
            LocalDateTime cutoffDate = LocalDateTime.now().minus(retentionDays, ChronoUnit.DAYS);
            
            List<Path> deletedFiles = new ArrayList<>();
            
            Files.list(logsDirectory)
                .filter(path -> {
                    try {
                        LocalDateTime fileTime = LocalDateTime.ofInstant(
                            Files.getLastModifiedTime(path).toInstant(),
                            java.time.ZoneId.systemDefault()
                        );
                        return fileTime.isBefore(cutoffDate);
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        deletedFiles.add(path);
                    } catch (IOException e) {
                        logger.error("Failed to delete log file: {} - {}", path, e.getMessage());
                    }
                });
            
            if (!deletedFiles.isEmpty()) {
                logger.info("Cleaned up {} log files older than {} days", deletedFiles.size(), retentionDays);
            }
            
        } catch (IOException e) {
            logger.error("Error during log cleanup: {}", e.getMessage());
        }
    }
    
    public void shutdown() {
        cleanupScheduler.shutdown();
        try {
            if (!cleanupScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            cleanupScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public ch.qos.logback.classic.Logger getJobLogger(String executionId, String logFilePath) {
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        ch.qos.logback.classic.Logger jobLogger = lc.getLogger("job." + executionId);
        jobLogger.setAdditive(false); // Prevent logs from going to root logger

        // Check if appender already exists
        if (jobLogger.getAppender(executionId) != null) {
            return jobLogger;
        }

        PatternLayoutEncoder encoder = new PatternLayoutEncoder();
        encoder.setContext(lc);
        encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n");
        encoder.start();

        RollingFileAppender<ch.qos.logback.classic.spi.ILoggingEvent> fileAppender = new RollingFileAppender<>();
        fileAppender.setContext(lc);
        fileAppender.setName(executionId);
        fileAppender.setFile(logFilePath);

        TimeBasedRollingPolicy<ch.qos.logback.classic.spi.ILoggingEvent> rollingPolicy = new TimeBasedRollingPolicy<>();
        rollingPolicy.setContext(lc);
        rollingPolicy.setParent(fileAppender);
        rollingPolicy.setFileNamePattern(logFilePath + ".%d{yyyy-MM-dd}");
        rollingPolicy.setMaxHistory(config.getLogRetentionDays());
        rollingPolicy.start();

        fileAppender.setRollingPolicy(rollingPolicy);
        fileAppender.setEncoder(encoder);
        fileAppender.start();

        jobLogger.addAppender(fileAppender);
        return jobLogger;
    }
}
