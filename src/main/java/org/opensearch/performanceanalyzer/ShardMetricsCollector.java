/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer;

import org.opensearch.performanceanalyzer.commons.metrics.RTFMetrics;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;

/**
 * A singleton collector for recording per-shard CPU and heap metrics in OpenSearch. This class
 * maintains two histograms:
 *
 * <ul>
 *   <li>CPU utilization histogram - tracks CPU usage per shard
 *   <li>Heap usage histogram - tracks heap memory allocation per shard
 * </ul>
 *
 * The metrics are recorded with tags for better categorization and analysis.
 */
public final class ShardMetricsCollector {
    /** Singleton instance of the ShardMetricsCollector */
    public static final ShardMetricsCollector INSTANCE = new ShardMetricsCollector();

    public static final String SHARD_CPU_UTILIZATION = "shard_cpu_utilization";
    public static final String SHARD_HEAP_ALLOCATED = "shard_heap_allocated";

    /** Histogram for tracking CPU utilization -- GETTER -- Gets the CPU utilization histogram. */
    private Histogram cpuUtilizationHistogram;

    /** Histogram for tracking heap usage -- GETTER -- Gets the heap usage histogram. */
    private Histogram heapUsedHistogram;

    /**
     * Private constructor that initializes the CPU and heap histograms. This is private to ensure
     * singleton pattern.
     */
    private ShardMetricsCollector() {
        this.cpuUtilizationHistogram = null;
        this.heapUsedHistogram = null;
    }

    /** Initialise metric histograms */
    public void initialize() {
        if (this.cpuUtilizationHistogram == null) {
            this.cpuUtilizationHistogram = createCpuUtilizationHistogram();
        }
        if (this.heapUsedHistogram == null) {
            this.heapUsedHistogram = createHeapUsedHistogram();
        }
    }

    /**
     * Creates a histogram for tracking CPU utilization.
     *
     * @return A histogram instance for CPU metrics, or null if metrics registry is unavailable
     */
    private Histogram createCpuUtilizationHistogram() {
        MetricsRegistry metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        if (metricsRegistry != null) {
            return metricsRegistry.createHistogram(
                    SHARD_CPU_UTILIZATION,
                    "CPU Utilization per shard for an operation",
                    RTFMetrics.MetricUnits.RATE.toString());
        }
        return null;
    }

    /**
     * Creates a histogram for tracking heap usage.
     *
     * @return A histogram instance for heap metrics, or null if metrics registry is unavailable
     */
    private Histogram createHeapUsedHistogram() {
        MetricsRegistry metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        if (metricsRegistry != null) {
            return metricsRegistry.createHistogram(
                    SHARD_HEAP_ALLOCATED,
                    "Heap Utilization per shard for an operation",
                    RTFMetrics.MetricUnits.BYTE.toString());
        }
        return null;
    }

    /**
     * Records a CPU utilization measurement with associated tags.
     *
     * @param cpuUtilization The CPU utilization value to record (as a percentage)
     * @param tags The tags to associate with this measurement (e.g., shard ID, operation type)
     */
    public void recordCpuUtilization(double cpuUtilization, Tags tags) {
        if (cpuUtilizationHistogram != null) {
            cpuUtilizationHistogram.record(cpuUtilization, tags);
        }
    }

    /**
     * Records a heap usage measurement with associated tags.
     *
     * @param heapBytes The heap usage value to record (in bytes)
     * @param tags The tags to associate with this measurement (e.g., shard ID, operation type)
     */
    public void recordHeapUsed(double heapBytes, Tags tags) {
        if (heapUsedHistogram != null) {
            heapUsedHistogram.record(heapBytes, tags);
        }
    }

    public Histogram getCpuUtilizationHistogram() {
        return cpuUtilizationHistogram;
    }

    public Histogram getHeapUsedHistogram() {
        return heapUsedHistogram;
    }
}
