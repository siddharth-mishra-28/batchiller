package com.batchiller.server.cli;

import com.batchiller.api.JobExecutionInfo;
import com.batchiller.server.config.BatchillerConfiguration;
import com.batchiller.server.database.DatabaseManager;
import com.batchiller.server.engine.JobExecutionEngine;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.List;
import java.util.concurrent.Callable;

/**
 * Command-line interface for interacting with the Batchiller application.
 * Provides commands to trigger jobs/pipelines, view execution history, and check system status.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
@Command(name = "batchiller", mixinStandardHelpOptions = true, version = "1.0.0",
         description = "Batchiller CLI - Batch Pipeline Orchestration Platform")
public class BatchillerCLI implements Callable<Integer> {
    
    @Command(name = "trigger", description = "Trigger a job or pipeline")
    static class Trigger implements Callable<Integer> {
        @Parameters(index = "0", description = "Job or pipeline name")
        private String name;
        
        @Option(names = {"-p", "--pipeline"}, description = "Trigger as pipeline")
        private boolean pipeline;
        
        @Option(names = {"-u", "--url"}, description = "Server URL", defaultValue = "http://localhost:5000")
        private String serverUrl;
        
        @Override
        public Integer call() {
            try {
                String endpoint = pipeline ? 
                    String.format("%s/api/pipelines/%s/trigger", serverUrl, name) :
                    String.format("%s/api/jobs/%s/trigger", serverUrl, name);
                
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(endpoint))
                    .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                    .build();
                
                java.net.http.HttpResponse<String> response = client.send(request, 
                    java.net.http.HttpResponse.BodyHandlers.ofString());
                
                System.out.println("Response: " + response.body());
                return response.statusCode() == 200 ? 0 : 1;
            } catch (Exception e) {
                System.err.println("Error triggering " + (pipeline ? "pipeline" : "job") + ": " + e.getMessage());
                return 1;
            }
        }
    }
    
    @Command(name = "history", description = "View job execution history")
    static class History implements Callable<Integer> {
        @Parameters(index = "0", description = "Job name")
        private String jobName;
        
        @Option(names = {"-l", "--limit"}, description = "Limit number of results", defaultValue = "10")
        private int limit;
        
        @Override
        public Integer call() {
            BatchillerConfiguration config = new BatchillerConfiguration();
            DatabaseManager db = new DatabaseManager(config);
            
            List<JobExecutionInfo> history = db.getJobHistory(jobName, limit);
            
            System.out.println("\nJob History for: " + jobName);
            System.out.println("=".repeat(80));
            
            for (JobExecutionInfo info : history) {
                System.out.printf("%-36s | %-15s | %-20s | %dms%n",
                    info.getExecutionId(),
                    info.getStatus(),
                    info.getStartTime(),
                    info.getDurationMillis()
                );
            }
            
            db.close();
            return 0;
        }
    }
    
    @Command(name = "status", description = "Show system status")
    static class Status implements Callable<Integer> {
        @Option(names = {"-u", "--url"}, description = "Server URL", defaultValue = "http://localhost:5000")
        private String serverUrl;
        
        @Override
        public Integer call() {
            try {
                java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
                java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(serverUrl + "/api/metrics"))
                    .GET()
                    .build();
                
                java.net.http.HttpResponse<String> response = client.send(request, 
                    java.net.http.HttpResponse.BodyHandlers.ofString());
                
                if (response.statusCode() == 200) {
                    System.out.println("Batchiller System Status:");
                    System.out.println("  Server: Running on " + serverUrl);
                    System.out.println("  Metrics: " + response.body());
                    return 0;
                } else {
                    System.out.println("Server not responding");
                    return 1;
                }
            } catch (Exception e) {
                System.err.println("Error getting status: " + e.getMessage());
                System.out.println("  Server: Not reachable");
                return 1;
            }
        }
    }
    
    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }
    
    public static void main(String[] args) {
        CommandLine cmd = new CommandLine(new BatchillerCLI());
        cmd.addSubcommand("trigger", new Trigger());
        cmd.addSubcommand("history", new History());
        cmd.addSubcommand("status", new Status());
        
        int exitCode = cmd.execute(args);
        System.exit(exitCode);
    }
}
