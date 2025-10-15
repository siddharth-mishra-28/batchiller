package com.batchiller.server.loader;

import com.batchiller.api.BatchJob;
import com.batchiller.api.Pipeline;
import com.batchiller.server.config.BatchillerConfiguration;
import com.batchiller.server.engine.JobExecutionEngine;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dynamically loads and hot-swaps batch jobs from JAR files.
 * Scans a configurable directory for job JARs and automatically registers them.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class DynamicJobLoader {
    
    private static final Logger logger = LoggerFactory.getLogger(DynamicJobLoader.class);
    
    private final BatchillerConfiguration config;
    private final JobExecutionEngine engine;
    private final Map<String, LoadedJob> loadedJobs;
    private final Map<String, Long> jarTimestamps;
    private final ScheduledExecutorService scanner;
    private volatile boolean running;
    
    public DynamicJobLoader(BatchillerConfiguration config, JobExecutionEngine engine) {
        this.config = config;
        this.engine = engine;
        this.loadedJobs = new ConcurrentHashMap<>();
        this.jarTimestamps = new ConcurrentHashMap<>();
        this.scanner = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "JobLoaderScanner");
            t.setDaemon(true);
            return t;
        });
        this.running = false;
    }
    
    public void start() {
        if (!config.isJobHotswapEnabled()) {
            logger.info("Hot-swap is disabled. Skipping dynamic job loader.");
            return;
        }
        
        String jobsDir = getJobsDirectoryPath();
        logger.info("Starting dynamic job loader. Scanning directory: {}", jobsDir);
        
        File directory = new File(jobsDir);
        if (!directory.exists() && !directory.mkdirs()) {
            logger.error("Failed to create jobs directory: {}", jobsDir);
            return;
        }
        
        this.running = true;
        
        // Initial scan
        scanAndLoadJobs();
        
        // Schedule periodic scans
        int scanInterval = config.getJobScanIntervalSeconds();
        scanner.scheduleWithFixedDelay(
            this::scanAndLoadJobs,
            scanInterval,
            scanInterval,
            TimeUnit.SECONDS
        );
        
        logger.info("Dynamic job loader started. Scan interval: {}s", scanInterval);
    }
    
    public void stop() {
        logger.info("Stopping dynamic job loader...");
        running = false;
        scanner.shutdown();
        try {
            if (!scanner.awaitTermination(5, TimeUnit.SECONDS)) {
                scanner.shutdownNow();
            }
        } catch (InterruptedException e) {
            scanner.shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        // Cleanup loaded jobs
        loadedJobs.values().forEach(LoadedJob::close);
        loadedJobs.clear();
        jarTimestamps.clear();
        
        logger.info("Dynamic job loader stopped");
    }
    
    private void scanAndLoadJobs() {
        try {
            String jobsDir = getJobsDirectoryPath();
            File directory = new File(jobsDir);
            
            if (!directory.exists() || !directory.isDirectory()) {
                return;
            }
            
            File[] jarFiles = directory.listFiles((dir, name) -> name.toLowerCase().endsWith(".jar"));
            logger.debug("Found {} JAR files in {}", jarFiles != null ? jarFiles.length : 0, jobsDir);
            if (jarFiles == null || jarFiles.length == 0) {
                return;
            }
            
            Set<String> currentJars = new HashSet<>();
            
            for (File jarFile : jarFiles) {
                String jarPath = jarFile.getAbsolutePath();
                currentJars.add(jarPath);
                long lastModified = jarFile.lastModified();
                
                // Check if JAR is new or modified
                Long previousTimestamp = jarTimestamps.get(jarPath);
                if (previousTimestamp == null || previousTimestamp < lastModified) {
                    logger.info("Loading jobs from JAR: {}", jarFile.getName());
                    loadJobsFromJar(jarFile);
                    jarTimestamps.put(jarPath, lastModified);
                }
            }
            
            // Remove jobs from deleted JARs
            Set<String> deletedJars = new HashSet<>(jarTimestamps.keySet());
            deletedJars.removeAll(currentJars);
            
            for (String deletedJar : deletedJars) {
                logger.info("JAR removed, unloading jobs: {}", new File(deletedJar).getName());
                unloadJobsFromJar(deletedJar);
                jarTimestamps.remove(deletedJar);
            }
            
        } catch (Exception e) {
            logger.error("Error scanning jobs directory: {}", e.getMessage(), e);
        }
    }
    
    private void loadJobsFromJar(File jarFile) {
        URLClassLoader classLoader = null;
        
        try {
            URL jarUrl = jarFile.toURI().toURL();
            classLoader = new URLClassLoader(
                new URL[]{jarUrl},
                BatchJob.class.getClassLoader()
            );
            
            List<String> jobClasses = findJobClasses(jarFile);
            
            for (String className : jobClasses) {
                try {
                    Class<?> clazz = classLoader.loadClass(className);
                    
                    if (BatchJob.class.isAssignableFrom(clazz) && !clazz.isInterface()) {
                        BatchJob job = (BatchJob) clazz.getDeclaredConstructor().newInstance();
                        String jobName = job.getName();
                        
                        // Unload previous version if exists
                        LoadedJob previous = loadedJobs.get(jobName);
                        if (previous != null) {
                            engine.unregisterJob(jobName);
                            previous.close();
                            logger.info("Unloaded previous version of job: {}", jobName);
                        }
                        
                        // Load new version
                        loadedJobs.put(jobName, new LoadedJob(job, classLoader, jarFile.getAbsolutePath()));
                        engine.registerJob(job);
                        logger.info("Loaded job: {} from {}", jobName, jarFile.getName());
                    }
                    
                    if (Pipeline.class.isAssignableFrom(clazz) && !clazz.isInterface()) {
                        Pipeline pipeline = (Pipeline) clazz.getDeclaredConstructor().newInstance();
                        engine.registerPipeline(pipeline);
                        logger.info("Loaded pipeline: {} from {}", pipeline.getName(), jarFile.getName());
                    }
                    
                } catch (Exception e) {
                    logger.error("Failed to load class: {} - {}", className, e.getMessage());
                }
            }
            
        } catch (Exception e) {
            logger.error("Failed to load jobs from JAR: {} - {}", jarFile.getName(), e.getMessage());
            if (classLoader != null) {
                try {
                    classLoader.close();
                } catch (Exception ignored) {}
            }
        }
    }
    
    private void unloadJobsFromJar(String jarPath) {
        List<String> toRemove = new ArrayList<>();
        
        for (Map.Entry<String, LoadedJob> entry : loadedJobs.entrySet()) {
            if (entry.getValue().jarPath.equals(jarPath)) {
                toRemove.add(entry.getKey());
            }
        }
        
        for (String jobName : toRemove) {
            LoadedJob loaded = loadedJobs.remove(jobName);
            if (loaded != null) {
                engine.unregisterJob(jobName);
                loaded.close();
                logger.info("Unloaded job: {}", jobName);
            }
        }
    }
    
        private List<String> findJobClasses(File jarFile) throws Exception {
    
            List<String> classes = new ArrayList<>();
    
            
    
            try (JarFile jar = new JarFile(jarFile)) {
    
                Enumeration<JarEntry> entries = jar.entries();
    
                
    
                while (entries.hasMoreElements()) {
    
                    JarEntry entry = entries.nextElement();
    
                    String name = entry.getName();
    
                    
    
                    if (name.endsWith(".class")) {
    
                        String className = name.replace('/', '.').substring(0, name.length() - 6);
    
                        classes.add(className);
    
                    }
    
                }
    
            }
    
            
    
            return classes;
    
        }
    
    
    
                private String getJobsDirectoryPath() {
    
    
    
                    String jobsDir = config.getJobsDirectory();
    
    
    
                    if (isRelative(jobsDir)) {
    
    
    
                        try {
    
    
    
                            String jarPath = DynamicJobLoader.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
    
    
    
                            File jarFile = new File(jarPath);
    
    
    
                            String basePath = jarFile.getParent();
    
    
    
                            File jobsDirectory = new File(basePath, jobsDir);
    
    
    
                            if (!jobsDirectory.exists()) {
    
    
    
                                jobsDirectory.mkdirs();
    
    
    
                            }
    
    
    
                            return jobsDirectory.getAbsolutePath();
    
    
    
                        } catch (Exception e) {
    
    
    
                            logger.error("Failed to resolve jobs directory path relative to JAR location. Using current working directory.", e);
    
    
    
                            File jobsDirectory = new File(jobsDir);
    
    
    
                            if (!jobsDirectory.exists()) {
    
    
    
                                jobsDirectory.mkdirs();
    
    
    
                            }
    
    
    
                            return jobsDirectory.getAbsolutePath();
    
    
    
                        }
    
    
    
                    }
    
    
    
                    return jobsDir;
    
    
    
                }
    
    
    
        private boolean isRelative(String path) {
    
            File f = new File(path);
    
            return !f.isAbsolute();
    
        }
    
        
    
        private static class LoadedJob {
        final BatchJob job;
        final URLClassLoader classLoader;
        final String jarPath;
        
        LoadedJob(BatchJob job, URLClassLoader classLoader, String jarPath) {
            this.job = job;
            this.classLoader = classLoader;
            this.jarPath = jarPath;
        }
        
        void close() {
            try {
                if (classLoader != null) {
                    classLoader.close();
                }
            } catch (Exception e) {
                logger.error("Error closing class loader: {}", e.getMessage());
            }
        }
    }
}
