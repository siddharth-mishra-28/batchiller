package com.batchiller.server.http;

import com.batchiller.api.*;
import com.batchiller.server.config.BatchillerConfiguration;
import com.batchiller.server.database.DatabaseManager;
import com.batchiller.server.engine.JobExecutionEngine;
import com.batchiller.server.logging.LogManager;
import com.batchiller.server.monitoring.SystemMonitor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.Methods;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/**
 * A class that provides an HTTP server for the Batchiller application.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class HttpServer {
    
    private static final Logger logger = LoggerFactory.getLogger(HttpServer.class);
    private final ObjectMapper objectMapper;
    private final BatchillerConfiguration config;
    private final JobExecutionEngine engine;
    private final DatabaseManager database;
    private final SystemMonitor monitor;
    private final LogManager logManager;
    private final com.batchiller.server.scheduler.JobScheduler scheduler;
    private Undertow server;
    
    public HttpServer(BatchillerConfiguration config, JobExecutionEngine engine, 
                     DatabaseManager database, SystemMonitor monitor, LogManager logManager, 
                     com.batchiller.server.scheduler.JobScheduler scheduler) {
        this.config = config;
        this.engine = engine;
        this.database = database;
        this.monitor = monitor;
        this.logManager = logManager;
        this.scheduler = scheduler;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.configure(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
    }
    
    public void start() {
        server = Undertow.builder()
            .addHttpListener(config.getServerPort(), config.getServerHost())
            .setHandler(this::handleRequest)
            .build();
        
        server.start();
        logger.info("HTTP Server started on {}:{}", config.getServerHost(), config.getServerPort());
    }
    
    private void handleRequest(HttpServerExchange exchange) {
        String path = exchange.getRequestPath();
        
        try {
            if (path.startsWith("/api/")) {
                handleApiRequest(exchange, path);
            } else {
                handleStaticResource(exchange, path);
            }
        } catch (Exception e) {
            logger.error("Error handling request: " + path, e);
            exchange.setStatusCode(500);
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
            sendJson(exchange, Map.of("error", e.getMessage()));
        }
    }
    
    private void handleApiRequest(HttpServerExchange exchange, String path) throws Exception {
        exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
        
        if (path.equals("/api/jobs") && exchange.getRequestMethod().equals(Methods.GET)) {
            sendJson(exchange, engine.getJobs().stream()
                .map(job -> Map.of(
                    "name", job.getName(),
                    "description", job.getDescription()
                ))
                .toList());
        } else if (path.equals("/api/pipelines") && exchange.getRequestMethod().equals(Methods.GET)) {
            sendJson(exchange, engine.getPipelines().stream()
                .map(pipeline -> Map.of(
                    "name", pipeline.getName(),
                    "description", pipeline.getDescription(),
                    "flow", pipeline.getFlow().name(),
                    "jobs", pipeline.getJobs().size()
                ))
                .toList());
        } else if (path.startsWith("/api/jobs/") && path.endsWith("/trigger") && exchange.getRequestMethod().equals(Methods.POST)) {
            String jobName = path.substring("/api/jobs/".length(), path.length() - "/trigger".length());
            
            CompletableFuture<JobResult> future = engine.executeJob(jobName, Map.of(), "WEB_UI", "MANUAL");
            String executionId = java.util.UUID.randomUUID().toString();
            
            future.thenAccept(result -> {
                logger.info("Job {} completed: {}", jobName, result.getMessage());
            }).exceptionally(e -> {
                logger.error("Job {} failed: {}", jobName, e.getMessage());
                return null;
            });
            
            sendJson(exchange, Map.of(
                "success", true,
                "message", "Job '" + jobName + "' has been queued for execution",
                "executionId", executionId
            ));
        } else if (path.startsWith("/api/pipelines/") && path.endsWith("/trigger") && exchange.getRequestMethod().equals(Methods.POST)) {
            String pipelineName = path.substring("/api/pipelines/".length(), path.length() - "/trigger".length());
            try {
                exchange.dispatch(); // Dispatch for asynchronous handling
                engine.executePipeline(pipelineName, "WEB_UI", "MANUAL")
                    .thenAccept(v -> {
                        sendJson(exchange, Map.of("success", true, "message", "Pipeline triggered"));
                        exchange.endExchange(); // End the exchange after sending response
                    })
                    .exceptionally(e -> {
                        sendJson(exchange, Map.of("success", false, "error", e.getMessage()));
                        exchange.endExchange(); // End the exchange after sending response
                        return null;
                    });
            } catch (Exception e) {
                logger.error("Failed to trigger pipeline synchronously: " + pipelineName, e);
                sendJson(exchange, Map.of("success", false, "error", e.getMessage()));
            }
        } else if (path.equals("/api/metrics") && exchange.getRequestMethod().equals(Methods.GET)) {
            sendJson(exchange, monitor.getMetrics());
        } else if (path.startsWith("/api/jobs/") && path.endsWith("/history") && exchange.getRequestMethod().equals(Methods.GET)) {
            String jobName = path.substring("/api/jobs/".length(), path.length() - "/history".length());
            sendJson(exchange, database.getJobHistory(jobName, config.getJobHistoryLimit()));
        } else if (path.equals("/api/executions") && exchange.getRequestMethod().equals(Methods.GET)) {
            sendJson(exchange, database.getAllExecutions(100));
        } else if (path.equals("/api/pipelines/designer/save") && exchange.getRequestMethod().equals(Methods.POST)) {
            exchange.getRequestReceiver().receiveFullString((exch, body) -> {
                try {
                    Map<String, Object> pipelineData = objectMapper.readValue(body, Map.class);
                    String id = pipelineData.containsKey("id") ? 
                        (String) pipelineData.get("id") : 
                        java.util.UUID.randomUUID().toString();
                    String name = (String) pipelineData.get("name");
                    String description = (String) pipelineData.getOrDefault("description", "");
                    String flowType = (String) pipelineData.getOrDefault("flowType", "SEQUENTIAL");
                    
                    boolean saved = database.savePipelineConfiguration(id, name, description, flowType, body);
                    
                    if (saved) {
                        logger.info("Saved pipeline configuration: {} (id: {})", name, id);
                        sendJson(exch, Map.of(
                            "success", true, 
                            "message", "Pipeline '" + name + "' saved successfully",
                            "id", id
                        ));
                    } else {
                        logger.error("Failed to save pipeline configuration: {}", name);
                        sendJson(exch, Map.of(
                            "success", false, 
                            "error", "Failed to save pipeline configuration"
                        ));
                    }
                } catch (Exception e) {
                    logger.error("Failed to save pipeline", e);
                    sendJson(exch, Map.of(
                        "success", false, 
                        "error", e.getMessage()
                    ));
                }
            });
        } else if (path.equals("/api/pipelines/designer/list") && exchange.getRequestMethod().equals(Methods.GET)) {
            sendJson(exchange, Map.of("pipelines", database.getAllPipelineConfigurations()));
        } else if (path.equals("/api/scheduled-jobs") && exchange.getRequestMethod().equals(Methods.GET)) {
            sendJson(exchange, scheduler.getAllScheduledJobs());
        } else if (path.equals("/api/scheduled-jobs") && exchange.getRequestMethod().equals(Methods.POST)) {
            exchange.getRequestReceiver().receiveFullString((exch, body) -> {
                try {
                    com.batchiller.server.scheduler.ScheduledJob scheduledJob = objectMapper.readValue(body, com.batchiller.server.scheduler.ScheduledJob.class);
                    if (scheduledJob.getId() == null || scheduledJob.getId().isEmpty()) {
                        scheduler.schedule(scheduledJob);
                    } else {
                        scheduler.updateSchedule(scheduledJob);
                    }
                    sendJson(exch, Map.of("success", true, "message", "Scheduled job saved successfully", "id", scheduledJob.getId()));
                } catch (Exception e) {
                    logger.error("Failed to save scheduled job", e);
                    sendJson(exch, Map.of("success", false, "error", e.getMessage()));
                }
            });
        } else if (path.startsWith("/api/scheduled-jobs/") && exchange.getRequestMethod().equals(Methods.DELETE)) {
            String scheduledJobId = path.substring("/api/scheduled-jobs/".length());
            try {
                scheduler.unschedule(scheduledJobId);
                sendJson(exchange, Map.of("success", true, "message", "Scheduled job deleted successfully"));
            } catch (Exception e) {
                logger.error("Failed to delete scheduled job: " + scheduledJobId, e);
                sendJson(exchange, Map.of("success", false, "error", e.getMessage()));
            }
        } else if (path.startsWith("/api/executions/") && path.endsWith("/log") && exchange.getRequestMethod().equals(Methods.GET)) {
            String executionId = path.substring("/api/executions/".length(), path.length() - "/log".length());
            File logFile = logManager.getLogFile(executionId);

            if (logFile != null && logFile.exists()) {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                exchange.getResponseHeaders().put(Headers.CONTENT_DISPOSITION,
                        "attachment; filename=\"execution-" + executionId + ".log\"");
                exchange.getResponseSender().send(Files.readString(logFile.toPath()));
            } else {
                exchange.setStatusCode(404);
                sendJson(exchange, Map.of("error", "Log file not available or has expired"));
            }
        } else {
            exchange.setStatusCode(404);
            sendJson(exchange, Map.of("error", "Not found"));
        }
    }
    
    private void handleStaticResource(HttpServerExchange exchange, String path) throws Exception {
        if (path.equals("/") || path.isEmpty()) {
            path = "/index.html";
        }
        
        Path staticPath = Paths.get("batchiller-server/src/main/resources/static" + path);
        if (Files.exists(staticPath)) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, getContentType(path));
            exchange.getResponseSender().send(Files.readString(staticPath, StandardCharsets.UTF_8));
        } else {
            InputStream resource = getClass().getResourceAsStream("/static" + path);
            if (resource != null) {
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, getContentType(path));
                exchange.getResponseSender().send(new String(resource.readAllBytes(), StandardCharsets.UTF_8));
            } else {
                exchange.setStatusCode(404);
                exchange.getResponseSender().send("Not Found");
            }
        }
    }
    
    private String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".js")) return "application/javascript";
        if (path.endsWith(".json")) return "application/json";
        return "text/plain";
    }
    
    private void sendJson(HttpServerExchange exchange, Object data) {
        try {
            String json = objectMapper.writeValueAsString(data);
            exchange.getResponseSender().send(json);
        } catch (Exception e) {
            logger.error("Failed to serialize JSON", e);
            exchange.setStatusCode(500);
            exchange.getResponseSender().send("{\"error\":\"Internal server error\"}");
        }
    }
    
    public void stop() {
        if (server != null) {
            server.stop();
            logger.info("HTTP Server stopped");
        }
    }
}
