/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.stats;


import java.util.Collections;
import java.util.List;
import org.opensearch.performanceanalyzer.commons.stats.eval.Statistics;
import org.opensearch.performanceanalyzer.commons.stats.measurements.MeasurementSet;
import org.opensearch.performanceanalyzer.commons.stats.metrics.StatsType;

public enum PACollectorMetrics implements MeasurementSet {

    /** Tracks time taken by respective collectors to collect event metrics. */
    ADMISSION_CONTROL_COLLECTOR_EXECUTION_TIME(
            "AdmissionControlCollectorExecutionTime",
            "millis",
            StatsType.LATENCIES,
            Statistics.SUM),
    CACHE_CONFIG_METRICS_COLLECTOR_EXECUTION_TIME(
            "CacheConfigMetricsCollectorExecutionTime",
            "millis",
            StatsType.LATENCIES,
            Statistics.SUM),
    CIRCUIT_BREAKER_COLLECTOR_EXECUTION_TIME(
            "CircuitBreakerCollectorExecutionTime", "millis", StatsType.LATENCIES, Statistics.SUM),
    CLUSTER_APPLIER_SERVICE_STATS_COLLECTOR_EXECUTION_TIME(
            "ClusterApplierServiceStatsCollectorExecutionTime",
            "millis",
            StatsType.LATENCIES,
            Statistics.SUM),
    CLUSTER_MANAGER_SERVICE_EVENTS_METRICS_COLLECTOR_EXECUTION_TIME(
            "ClusterManagerServiceEventsMetricsCollectorExecutionTime",
            "millis",
            StatsType.LATENCIES,
            Statistics.SUM),
    CLUSTER_MANAGER_SERVICE_METRICS_COLLECTOR_EXECUTION_TIME(
            "ClusterManagerServiceMetricsCollectorExecutionTime",
            "millis",
            StatsType.LATENCIES,
            Statistics.SUM),
    CLUSTER_MANAGER_THROTTLING_COLLECTOR_EXECUTION_TIME(
            "ClusterManagerThrottlingCollectorExecutionTime",
            "millis",
            StatsType.LATENCIES,
            Statistics.SUM),
    ELECTION_TERM_COLLECTOR_EXECUTION_TIME(
            "ElectionTermCollectorExecutionTime", "millis", StatsType.LATENCIES, Statistics.SUM),
    FAULT_DETECTION_COLLECTOR_EXECUTION_TIME(
            "FaultDetectionCollectorExecutionTime", "millis", StatsType.LATENCIES, Statistics.SUM),
    NODE_DETAILS_COLLECTOR_EXECUTION_TIME(
            "NodeDetailsCollectorExecutionTime", "millis", StatsType.LATENCIES, Statistics.SUM),
    NODE_STATS_ALL_SHARDS_METRICS_COLLECTOR_EXECUTION_TIME(
            "NodeStatsAllShardsMetricsCollectorExecutionTime",
            "millis",
            StatsType.LATENCIES,
            Statistics.SUM),
    NODE_STATS_FIXED_SHARDS_METRICS_COLLECTOR_EXECUTION_TIME(
            "NodeStatsFixedShardsMetricsCollectorExecutionTime",
            "millis",
            StatsType.LATENCIES,
            Statistics.SUM),
    SHARD_INDEXING_PRESSURE_COLLECTOR_EXECUTION_TIME(
            "ShardIndexingPressureCollectorExecutionTime",
            "millis",
            StatsType.LATENCIES,
            Statistics.SUM),
    SHARD_STATE_COLLECTOR_EXECUTION_TIME(
            "ShardStateCollectorExecutionTime", "millis", StatsType.LATENCIES, Statistics.SUM),
    THREADPOOL_METRICS_COLLECTOR_EXECUTION_TIME(
            "ThreadPoolMetricsCollectorExecutionTime",
            "millis",
            StatsType.LATENCIES,
            Statistics.SUM),

    /** Tracks collector specific metrics - available/enabled/disabled and other params */
    ADMISSION_CONTROL_COLLECTOR_NOT_AVAILABLE("AdmissionControlCollectorNotAvailable"),

    CLUSTER_MANAGER_THROTTLING_COLLECTOR_NOT_AVAILABLE(
            "ClusterManagerThrottlingCollectorNotAvailable");

    /** What we want to appear as the metric name. */
    private String name;

    /**
     * The unit the measurement is in. This is not used for the statistics calculations but as an
     * information that will be dumped with the metrics.
     */
    private String unit;

    /** The type of the measurement, refer {@link StatsType} */
    private StatsType statsType;

    /**
     * Multiple statistics can be collected for each measurement like MAX, MIN and MEAN. This is a
     * collection of one or more such statistics.
     */
    private List<Statistics> statsList;

    PACollectorMetrics(String name) {
        this(name, "count", StatsType.STATS_DATA, Collections.singletonList(Statistics.COUNT));
    }

    PACollectorMetrics(String name, String unit, StatsType statsType, Statistics stats) {
        this(name, unit, statsType, Collections.singletonList(stats));
    }

    PACollectorMetrics(String name, String unit, StatsType statsType, List<Statistics> stats) {
        this.name = name;
        this.unit = unit;
        this.statsType = statsType;
        this.statsList = stats;
    }

    public String toString() {
        return new StringBuilder(name).append("-").append(unit).toString();
    }

    @Override
    public StatsType getStatsType() {
        return statsType;
    }

    @Override
    public List<Statistics> getStatsList() {
        return statsList;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getUnit() {
        return unit;
    }
}
