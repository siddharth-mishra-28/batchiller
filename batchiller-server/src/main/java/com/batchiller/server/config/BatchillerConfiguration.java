package com.batchiller.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

/**
 * A class that provides configuration for the Batchiller application.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class BatchillerConfiguration {
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(BatchillerConfiguration.class);
    private final Properties properties;
    
    public BatchillerConfiguration() {
        this.properties = new Properties();
        loadConfiguration();
    }
    
    private void loadConfiguration() {
        Path configPath = Paths.get("batchiller.properties");
        
        if (Files.exists(configPath)) {
            try (InputStream input = Files.newInputStream(configPath)) {
                properties.load(input);
                logger.info("Loaded configuration from batchiller.properties");
            } catch (IOException e) {
                logger.error("Failed to load batchiller.properties: " + e.getMessage());
            }
        } else {
            try (InputStream input = getClass().getClassLoader().getResourceAsStream("application.properties")) {
                if (input != null) {
                    properties.load(input);
                    logger.info("Loaded configuration from application.properties");
                }
            } catch (IOException e) {
                logger.error("Failed to load application.properties: " + e.getMessage());
            }
        }
        
        setDefaults();
    }
    
    private void setDefaults() {
        properties.putIfAbsent("server.port", "5000");
        properties.putIfAbsent("server.host", "0.0.0.0");
        
        properties.putIfAbsent("db.type", "h2");
        properties.putIfAbsent("db.h2.path", "./data/batchiller");
        properties.putIfAbsent("db.mysql.url", "jdbc:mysql://localhost:3306/batchiller");
        properties.putIfAbsent("db.mysql.username", "root");
        properties.putIfAbsent("db.mysql.password", "");
        
        properties.putIfAbsent("executor.core.pool.size", "10");
        properties.putIfAbsent("executor.max.pool.size", "50");
        properties.putIfAbsent("executor.queue.capacity", "100");
        properties.putIfAbsent("executor.keep.alive.seconds", "60");
        
        properties.putIfAbsent("log.retention.days", "30");
        properties.putIfAbsent("job.history.limit", "100");
        properties.putIfAbsent("log.directory", "./logs");
        
        properties.putIfAbsent("jobs.directory", "./jobs");
        properties.putIfAbsent("jobs.scan.interval.seconds", "10");
        properties.putIfAbsent("jobs.hotswap.enabled", "true");
    }
    
    public String get(String key) {
        String value = System.getenv(key.toUpperCase().replace('.', '_'));
        if (value != null) {
            return value;
        }
        return properties.getProperty(key);
    }
    
    public String get(String key, String defaultValue) {
        String value = get(key);
        return value != null ? value : defaultValue;
    }
    
    public int getInt(String key, int defaultValue) {
        String value = get(key);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return defaultValue;
            }
        }
        return defaultValue;
    }
    
    public int getServerPort() {
        return getInt("server.port", 5000);
    }
    
    public String getServerHost() {
        return get("server.host", "0.0.0.0");
    }
    
    public String getDatabaseType() {
        return get("db.type", "h2");
    }
    
    public String getDatabaseUrl() {
        if ("h2".equals(getDatabaseType())) {
            return "jdbc:h2:" + get("db.h2.path", "./data/batchiller");
        } else {
            return get("db.mysql.url", "jdbc:mysql://localhost:3306/batchiller");
        }
    }
    
    public String getDatabaseUsername() {
        if ("h2".equals(getDatabaseType())) {
            return "sa";
        } else {
            return get("db.mysql.username", "root");
        }
    }
    
    public String getDatabasePassword() {
        if ("h2".equals(getDatabaseType())) {
            return "";
        } else {
            return get("db.mysql.password", "");
        }
    }
    
    public int getCorePoolSize() {
        return getInt("executor.core.pool.size", 10);
    }
    
    public int getMaxPoolSize() {
        return getInt("executor.max.pool.size", 50);
    }
    
    public int getQueueCapacity() {
        return getInt("executor.queue.capacity", 100);
    }
    
    public int getLogRetentionDays() {
        return getInt("log.retention.days", 30);
    }
    
    public int getJobHistoryLimit() {
        return getInt("job.history.limit", 100);
    }
    
    public String getLogDirectory() {
        return get("log.directory", "./logs");
    }
    
    public int getKeepAliveSeconds() {
        return getInt("executor.keep.alive.seconds", 60);
    }
    
    public String getJobsDirectory() {
        return get("jobs.directory", "./jobs");
    }
    
    public int getJobScanIntervalSeconds() {
        return getInt("jobs.scan.interval.seconds", 10);
    }
    
    public boolean isJobHotswapEnabled() {
        String value = get("jobs.hotswap.enabled", "true");
        return Boolean.parseBoolean(value);
    }
}
