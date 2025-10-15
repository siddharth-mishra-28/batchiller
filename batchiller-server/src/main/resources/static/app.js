let cpuChart = null;
let memoryChart = null;
let threadsChart = null;
let queueChart = null;
let trendsChart = null;

let allJobs = [];
let allPipelines = [];
let allExecutions = [];
let refreshInterval = 1000;
let metricsIntervalId = null;
let trendsIntervalId = null;
let allScheduledJobs = [];

function openScheduleModal(job = null) {
    const modal = document.getElementById('scheduleModal');
    modal.style.display = 'block';
    document.getElementById('scheduleForm').reset();
    document.getElementById('scheduleId').value = '';
    document.getElementById('scheduleEnabled').checked = true;
    document.getElementById('scheduleParameters').value = '{}';

    updateTargetOptions(); // Populate job/pipeline dropdown

    if (job) {
        document.getElementById('scheduleId').value = job.id;
        document.getElementById('scheduleName').value = job.name;
        document.getElementById('scheduleType').value = job.type;
        updateTargetOptions(); // Re-populate with correct type selected
        document.getElementById('scheduleTargetName').value = job.targetName;
        document.getElementById('scheduleCronExpression').value = job.cronExpression;
        document.getElementById('scheduleParameters').value = JSON.stringify(job.parameters, null, 2);
        document.getElementById('scheduleEnabled').checked = job.enabled;
    }
}

function closeScheduleModal() {
    const modal = document.getElementById('scheduleModal');
    modal.style.display = 'none';
}

// Close modal if user clicks outside of it
window.onclick = function(event) {
    const modal = document.getElementById('scheduleModal');
    if (event.target == modal) {
        modal.style.display = 'none';
    }
}

function initIndividualCharts() {
    const chartOptions = {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
            legend: {
                display: false
            }
        }
    };

    cpuChart = new Chart(document.getElementById('cpuChart'), {
        type: 'doughnut',
        data: {
            labels: ['Used', 'Free'],
            datasets: [{
                data: [0, 100],
                backgroundColor: ['rgba(75, 192, 192, 0.8)', 'rgba(200, 200, 200, 0.3)']
            }]
        },
        options: chartOptions
    });

    memoryChart = new Chart(document.getElementById('memoryChart'), {
        type: 'bar',
        data: {
            labels: ['Memory'],
            datasets: [{
                label: 'JVM Heap Usage',
                data: [0],
                backgroundColor: 'rgba(255, 99, 132, 0.8)',
            }, {
                label: 'System Memory Usage',
                data: [0],
                backgroundColor: 'rgba(54, 162, 235, 0.8)',
            }]
        },
        options: {
            ...chartOptions,
            indexAxis: 'y', // Make it a horizontal bar chart
            scales: {
                y: {
                    beginAtZero: true,
                    max: 100,
                    ticks: {
                        stepSize: 20
                    }
                },
                x: { // Add x-axis for horizontal bar chart
                    beginAtZero: true,
                    max: 100,
                    ticks: {
                        stepSize: 20
                    }
                }
            }
        }
    });

    threadsChart = new Chart(document.getElementById('threadsChart'), {
        type: 'bar',
        data: {
            labels: ['Active Threads'],
            datasets: [{
                label: 'Count',
                data: [0],
                backgroundColor: 'rgba(54, 162, 235, 0.8)'
            }]
        },
        options: {
            ...chartOptions,
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        stepSize: 1
                    }
                }
            },
            plugins: {
                legend: {
                    display: false
                }
            }
        }
    });

    queueChart = new Chart(document.getElementById('queueChart'), {
        type: 'bar',
        data: {
            labels: ['Queue Size'],
            datasets: [{
                label: 'Count',
                data: [0],
                backgroundColor: 'rgba(255, 206, 86, 0.8)'
            }]
        },
        options: {
            ...chartOptions,
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        stepSize: 1
                    }
                }
            },
            plugins: {
                legend: {
                    display: false
                }
            }
        }
    });
}

function initTrendsChart() {
    const ctx = document.getElementById('trendsChart').getContext('2d');
    trendsChart = new Chart(ctx, {
        type: 'bar',
        data: {
            labels: [],
            datasets: [
                {
                    label: 'Success',
                    data: [],
                    backgroundColor: 'rgba(75, 192, 192, 0.7)',
                },
                {
                    label: 'Failure',
                    data: [],
                    backgroundColor: 'rgba(255, 99, 132, 0.7)',
                }
            ]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            scales: {
                y: {
                    beginAtZero: true,
                    ticks: {
                        stepSize: 1
                    }
                }
            }
        }
    });
}

async function fetchMetrics() {
    try {
        const response = await fetch('/api/metrics');
        const metrics = await response.json();
        
        const cpuUsage = Math.round(metrics.cpu_usage || 0);
        const memoryUsage = metrics.heap_usage_percent || 0;
        const systemMemoryUsage = metrics.system_memory_usage_percent || 0;
        const activeThreads = metrics.active_thread_count || 0;
        const queueSize = metrics.queue_size || 0;
        
        if (cpuChart) {
            cpuChart.data.datasets[0].data = [cpuUsage, 100 - cpuUsage];
            cpuChart.update();
        }
        
        if (memoryChart) {
            memoryChart.data.datasets[0].data = [memoryUsage];
            memoryChart.data.datasets[1].data = [systemMemoryUsage];
            memoryChart.update();
        }
        
        if (threadsChart) {
            threadsChart.data.datasets[0].data = [activeThreads];
            threadsChart.update();
        }
        
        if (queueChart) {
            queueChart.data.datasets[0].data = [queueSize];
            queueChart.update();
        }
    } catch (error) {
        console.error('Failed to fetch metrics:', error);
    }
}

async function fetchJobs() {
    try {
        const response = await fetch('/api/jobs');
        const jobs = await response.json();
        allJobs = jobs;
        
        const jobFilter = document.getElementById('jobFilter');
        jobFilter.innerHTML = '<option value="all">All Jobs</option>' + 
            jobs.map(job => `<option value="${job.name}">${job.name}</option>`).join('');
        
        renderJobs();
    } catch (error) {
        console.error('Failed to fetch jobs:', error);
    }
}

function renderJobs() {
    const searchTerm = document.getElementById('jobSearch').value.toLowerCase();
    const filteredJobs = allJobs.filter(job => 
        job.name.toLowerCase().includes(searchTerm) || 
        job.description.toLowerCase().includes(searchTerm)
    );
    
    const jobsList = document.getElementById('jobsList');
    jobsList.innerHTML = filteredJobs.map(job => `
        <div class="list-item">
            <div>
                <h4>${job.name}</h4>
                <p>${job.description}</p>
            </div>
            <button class="btn" onclick="triggerJob('${job.name}')">Trigger</button>
        </div>
    `).join('');
}

async function fetchPipelines() {
    try {
        const response = await fetch('/api/pipelines');
        const pipelines = await response.json();
        allPipelines = pipelines;
        renderPipelines();
    } catch (error) {
        console.error('Failed to fetch pipelines:', error);
    }
}

function renderPipelines() {
    const searchTerm = document.getElementById('pipelineSearch').value.toLowerCase();
    const filteredPipelines = allPipelines.filter(pipeline => 
        pipeline.name.toLowerCase().includes(searchTerm) || 
        pipeline.description.toLowerCase().includes(searchTerm)
    );
    
    const pipelinesList = document.getElementById('pipelinesList');
    pipelinesList.innerHTML = filteredPipelines.map(pipeline => `
        <div class="list-item">
            <div>
                <h4>${pipeline.name}</h4>
                <p>${pipeline.description} (${pipeline.flow}, ${pipeline.jobs} jobs)</p>
            </div>
            <button class="btn" onclick="triggerPipeline('${pipeline.name}')">Trigger</button>
        </div>
    `).join('');
}

async function fetchExecutions() {
    try {
        const response = await fetch('/api/executions');
        const executions = await response.json();
        allExecutions = executions;
        
        renderExecutions();
        return executions;
    } catch (error) {
        console.error('Failed to fetch executions:', error);
        return [];
    }
}

function renderExecutions() {
    const searchTerm = document.getElementById('executionSearch').value.toLowerCase();
    const filteredExecutions = allExecutions.filter(exec => {
        const jobName = exec.jobName.toLowerCase();
        const startDate = new Date(exec.startTime).toLocaleString().toLowerCase();
        return jobName.includes(searchTerm) || startDate.includes(searchTerm);
    });
    
    const executionsList = document.getElementById('executionsList');
    executionsList.innerHTML = filteredExecutions.slice(0, 10).map(exec => `
        <div class="list-item">
            <div>
                <h4>${exec.jobName}</h4>
                <p>Started: ${new Date(exec.startTime).toLocaleString()}</p>
            </div>
            <div>
                <span class="status ${exec.status.toLowerCase()}">${exec.status}</span>
                ${exec.status === 'COMPLETED' || exec.status === 'FAILED' ? 
                    `<button class="btn-small" onclick="downloadLog('${exec.executionId}')">Download Log</button>` :
                    ''}
            </div>
        </div>
    `).join('');
}

function filterExecutions() {
    renderExecutions();
}

function updateTrendsChart() {
    if (!trendsChart || !allExecutions) {
        return;
    }
    
    const selectedJob = document.getElementById('jobFilter').value;
    const filteredExecutions = selectedJob === 'all' 
        ? allExecutions 
        : allExecutions.filter(exec => exec.jobName === selectedJob);
    
    const sevenDaysAgo = new Date(Date.now() - 7 * 24 * 60 * 60 * 1000);
    const dailyExecutions = filteredExecutions.filter(exec => 
        new Date(exec.startTime) > sevenDaysAgo
    );
    
    const labels = [];
    const dailyData = {};
    for (let i = 6; i >= 0; i--) {
        const d = new Date();
        d.setDate(d.getDate() - i);
        const label = d.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
        labels.push(label);
        dailyData[label] = { success: 0, failure: 0 };
    }
    
    dailyExecutions.forEach(exec => {
        const execDate = new Date(exec.startTime);
        const label = execDate.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
        
        if (dailyData[label] !== undefined) {
            if (exec.status === 'COMPLETED') {
                dailyData[label].success++;
            } else if (exec.status === 'FAILED') {
                dailyData[label].failure++;
            }
        }
    });
    
    trendsChart.data.labels = labels;
    trendsChart.data.datasets[0].data = labels.map(l => dailyData[l].success);
    trendsChart.data.datasets[1].data = labels.map(l => dailyData[l].failure);
    trendsChart.update();
    
    console.log('Trends chart updated with', dailyExecutions.length, 'executions in last 7 days');
}

async function downloadLog(executionId) {
    try {
        const response = await fetch(`/api/executions/${executionId}/log`);
        if (response.ok) {
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const a = document.createElement('a');
            a.href = url;
            a.download = `execution-${executionId}.log`;
            document.body.appendChild(a);
            a.click();
            window.URL.revokeObjectURL(url);
            document.body.removeChild(a);
            showToast(`Log file for execution ${executionId} downloaded successfully!`, 'success');
        } else {
            showToast('Log file not available or has expired', 'error');
        }
    } catch (error) {
        showToast('Failed to download log: ' + error.message, 'error');
    }
}

// Toast notification system
function showToast(message, type = 'info') {
    // Remove existing toast if any
    const existingToast = document.querySelector('.toast');
    if (existingToast) {
        existingToast.remove();
    }
    
    // Create toast element
    const toast = document.createElement('div');
    toast.className = `toast toast-${type}`;
    toast.innerHTML = `
        <div class="toast-content">
            <span class="toast-icon">${getToastIcon(type)}</span>
            <span class="toast-message">${message}</span>
            <button class="toast-close" onclick="this.parentElement.parentElement.remove()">×</button>
        </div>
    `;
    
    // Add to body
    document.body.appendChild(toast);
    
    // Auto remove after 5 seconds
    setTimeout(() => {
        if (toast && toast.parentElement) {
            toast.remove();
        }
    }, 5000);
    
    // Animate in
    setTimeout(() => toast.classList.add('toast-show'), 100);
}

function getToastIcon(type) {
    switch(type) {
        case 'success': return '✅';
        case 'error': return '❌';
        case 'warning': return '⚠️';
        default: return 'ℹ️';
    }
}

async function triggerJob(jobName) {
    try {
        const response = await fetch(`/api/jobs/${jobName}/trigger`, { method: 'POST' });
        const result = await response.json();
        if (result.success) {
            const message = result.executionId 
                ? `Job '${jobName}' triggered successfully! Execution ID: ${result.executionId}`
                : `Job '${jobName}' triggered successfully!`;
            showToast(message, 'success');
        } else {
            showToast('Failed to trigger job: ' + (result.error || result.message), 'error');
        }
        setTimeout(() => {
            fetchExecutions();
            fetchMetrics();
        }, 500);
    } catch (error) {
        showToast('Failed to trigger job: ' + error.message, 'error');
    }
}

async function triggerPipeline(pipelineName) {
    try {
        const response = await fetch(`/api/pipelines/${pipelineName}/trigger`, { method: 'POST' });
        const result = await response.json();
        if (result.success) {
            showToast(`Pipeline '${pipelineName}' triggered successfully!`, 'success');
        } else {
            showToast('Failed to trigger pipeline: ' + (result.error || result.message), 'error');
        }
        setTimeout(() => {
            fetchExecutions();
            fetchMetrics();
        }, 500);
    } catch (error) {
        showToast('Failed to trigger pipeline: ' + error.message, 'error');
    }
}

async function triggerPipeline(pipelineName) {
    try {
        const response = await fetch(`/api/pipelines/${pipelineName}/trigger`, { method: 'POST' });
        const result = await response.json();
        if (result.success) {
            showToast(`Pipeline '${pipelineName}' triggered successfully!`, 'success');
        } else {
            showToast('Failed to trigger pipeline: ' + (result.error || result.message), 'error');
        }
        setTimeout(() => {
            fetchExecutions();
            fetchMetrics();
        }, 500);
    } catch (error) {
        showToast('Failed to trigger pipeline: ' + error.message, 'error');
    }
}

async function fetchScheduledJobs() {
    try {
        const response = await fetch('/api/scheduled-jobs');
        allScheduledJobs = await response.json();
        renderScheduledJobs();
    } catch (error) {
        console.error('Failed to fetch scheduled jobs:', error);
    }
}

function renderScheduledJobs() {
    const scheduledJobsList = document.getElementById('scheduledJobsList');
    scheduledJobsList.innerHTML = allScheduledJobs.map(job => `
        <div class="list-item">
            <div>
                <h4>${job.name} (${job.type})</h4>
                <p>Target: ${job.targetName} | Cron: ${job.cronExpression} | Enabled: ${job.enabled ? 'Yes' : 'No'}</p>
                <p>Last Run: ${job.lastExecutionTime ? new Date(job.lastExecutionTime).toLocaleString() : 'N/A'} | Next Run: ${job.nextExecutionTime ? new Date(job.nextExecutionTime).toLocaleString() : 'N/A'}</p>
            </div>
            <div>
                <button class="btn-small" onclick="editScheduledJob('${job.id}')">Edit</button>
                <button class="btn-small" onclick="deleteScheduledJob('${job.id}')">Delete</button>
            </div>
        </div>
    `).join('');
}

async function submitScheduleForm(event) {
    event.preventDefault();
    const id = document.getElementById('scheduleId').value;
    const name = document.getElementById('scheduleName').value;
    const type = document.getElementById('scheduleType').value;
    const targetName = document.getElementById('scheduleTargetName').value;
    const cronExpression = document.getElementById('scheduleCronExpression').value;
    const parameters = document.getElementById('scheduleParameters').value;
    const enabled = document.getElementById('scheduleEnabled').checked;

    try {
        const parsedParameters = parameters ? JSON.parse(parameters) : {};

        const scheduleData = {
            id: id || null,
            name,
            type,
            targetName,
            cronExpression,
            parameters: parsedParameters,
            enabled
        };

        const response = await fetch('/api/scheduled-jobs', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify(scheduleData)
        });
        const result = await response.json();

        if (result.success) {
            showToast(`Schedule saved successfully!`, 'success');
            closeScheduleModal();
            fetchScheduledJobs();
        } else {
            showToast('Failed to save schedule: ' + (result.error || result.message), 'error');
        }
    } catch (error) {
        showToast('Failed to save schedule: ' + error.message, 'error');
    }
}

function editScheduledJob(id) {
    const job = allScheduledJobs.find(j => j.id === id);
    if (job) {
        openScheduleModal(job);
    }
}

async function deleteScheduledJob(id) {
    if (!confirm('Are you sure you want to delete this scheduled job?')) {
        return;
    }
    try {
        const response = await fetch(`/api/scheduled-jobs/${id}`, {
            method: 'DELETE'
        });
        const result = await response.json();

        if (result.success) {
            showToast(`Schedule deleted successfully!`, 'success');
            fetchScheduledJobs();
        } else {
            showToast('Failed to delete schedule: ' + (result.error || result.message), 'error');
        }
    } catch (error) {
        showToast('Failed to delete schedule: ' + error.message, 'error');
    }
}

function updateTargetOptions() {
    const scheduleType = document.getElementById('scheduleType').value;
    const scheduleTargetNameSelect = document.getElementById('scheduleTargetName');
    scheduleTargetNameSelect.innerHTML = ''; // Clear existing options

    let options = [];
    if (scheduleType === 'JOB') {
        options = allJobs.map(job => `<option value="${job.name}">${job.name}</option>`);
    } else if (scheduleType === 'PIPELINE') {
        options = allPipelines.map(pipeline => `<option value="${pipeline.name}">${pipeline.name}</option>`);
    }
    scheduleTargetNameSelect.innerHTML = options.join('');
}

function filterJobs() {
    renderJobs();
}

function filterPipelines() {
    renderPipelines();
}

function updateRefreshInterval() {
    const interval = parseInt(document.getElementById('refreshInterval').value) * 1000;
    refreshInterval = interval;
    
    if (metricsIntervalId) {
        clearInterval(metricsIntervalId);
    }
    if (trendsIntervalId) {
        clearInterval(trendsIntervalId);
    }
    
    metricsIntervalId = setInterval(fetchMetrics, refreshInterval);
    trendsIntervalId = setInterval(() => {
        fetchExecutions().then(() => updateTrendsChart());
    }, Math.max(refreshInterval, 5000));
    
    showToast(`Refresh interval updated to ${interval/1000} seconds`, 'success');
}

window.addEventListener('DOMContentLoaded', () => {
    initIndividualCharts();
    initTrendsChart();
    
    // Initial data fetch
    Promise.all([
        fetchJobs(),
        fetchPipelines(),
        fetchExecutions(),
        fetchMetrics(),
        fetchScheduledJobs()
    ]).then(() => {
        // Update trends chart after executions are loaded
        updateTrendsChart();
    });
    
    // Set up intervals
    metricsIntervalId = setInterval(fetchMetrics, refreshInterval);
    trendsIntervalId = setInterval(() => {
        fetchExecutions().then(() => updateTrendsChart());
    }, Math.max(refreshInterval, 5000));

    document.getElementById('scheduleForm').addEventListener('submit', submitScheduleForm);
});
