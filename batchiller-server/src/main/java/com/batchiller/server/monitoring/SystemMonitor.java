package com.batchiller.server.monitoring;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * A class that monitors the system metrics.
 * 
 * @author Siddharth Mishra &lt;mishra.siddharth90@gmail.com&gt;
 * @version 1.0
 */
public class SystemMonitor {
    
    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(SystemMonitor.class);
    private final ThreadPoolExecutor executor;
    private final OperatingSystemMXBean osBean;
    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    
    public SystemMonitor(ThreadPoolExecutor executor) {
        this.executor = executor;
        this.osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
    }
    
    public Map<String, Object> getMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        double processCpuLoad = osBean.getProcessCpuLoad();
        metrics.put("cpu_usage", processCpuLoad >= 0 ? processCpuLoad * 100 : 0);
        
        double systemCpuLoad = osBean.getSystemCpuLoad();
        metrics.put("system_cpu_usage", systemCpuLoad >= 0 ? systemCpuLoad * 100 : 0);
        
        // System Memory
        long totalMemory = osBean.getTotalPhysicalMemorySize();
        long freeMemory = osBean.getFreePhysicalMemorySize();
        long usedMemory = totalMemory - freeMemory;
        double systemMemoryUsagePercent = (totalMemory > 0) ? (usedMemory * 100.0) / totalMemory : 0;
        metrics.put("system_total_memory", totalMemory);
        metrics.put("system_used_memory", usedMemory);
        metrics.put("system_free_memory", freeMemory);
        metrics.put("system_memory_usage_percent", systemMemoryUsagePercent);

        // Heap Memory
        long heapUsed = memoryBean.getHeapMemoryUsage().getUsed();
        long heapMax = memoryBean.getHeapMemoryUsage().getMax();
        double heapUsagePercent = (heapMax > 0) ? (heapUsed * 100.0) / heapMax : 0;
        metrics.put("heap_used", heapUsed);
        metrics.put("heap_max", heapMax);
        metrics.put("heap_usage_percent", heapUsagePercent);
        
        // For backward compatibility with the UI
        metrics.put("memory_usage_percent", heapUsagePercent);
        
        metrics.put("active_thread_count", executor.getActiveCount());
        metrics.put("pool_size", executor.getPoolSize());
        metrics.put("queue_size", executor.getQueue().size());
        metrics.put("completed_task_count", executor.getCompletedTaskCount());
        
        metrics.put("total_threads", threadBean.getThreadCount());
        metrics.put("daemon_threads", threadBean.getDaemonThreadCount());
        
        return metrics;
    }
}
