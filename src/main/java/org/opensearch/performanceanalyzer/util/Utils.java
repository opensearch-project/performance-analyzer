/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.util;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.admin.indices.stats.CommonStats;
import org.opensearch.action.admin.indices.stats.CommonStatsFlags;
import org.opensearch.action.admin.indices.stats.IndexShardStats;
import org.opensearch.action.admin.indices.stats.ShardStats;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.index.IndexService;
import org.opensearch.index.shard.IndexShard;
import org.opensearch.index.shard.IndexShardState;
import org.opensearch.indices.IndicesService;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.collectors.*;
import org.opensearch.performanceanalyzer.collectors.telemetry.*;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.stats.ServiceMetrics;

public class Utils {
    private static final Logger LOG = LogManager.getLogger(Utils.class);

    public static void configureMetrics() {
        ServiceMetrics.initStatsReporter();
        MetricsConfiguration.MetricConfig cdefault = MetricsConfiguration.cdefault;
        MetricsConfiguration.CONFIG_MAP.put(AdmissionControlMetricsCollector.class, cdefault);
        MetricsConfiguration.CONFIG_MAP.put(CacheConfigMetricsCollector.class, cdefault);
        MetricsConfiguration.CONFIG_MAP.put(CircuitBreakerCollector.class, cdefault);
        MetricsConfiguration.CONFIG_MAP.put(ThreadPoolMetricsCollector.class, cdefault);
        MetricsConfiguration.CONFIG_MAP.put(NodeDetailsCollector.class, cdefault);
        MetricsConfiguration.CONFIG_MAP.put(NodeStatsAllShardsMetricsCollector.class, cdefault);
        MetricsConfiguration.CONFIG_MAP.put(
                ClusterManagerServiceEventMetrics.class,
                new MetricsConfiguration.MetricConfig(1000, 0));
        MetricsConfiguration.CONFIG_MAP.put(ClusterManagerServiceMetrics.class, cdefault);
        MetricsConfiguration.CONFIG_MAP.put(FaultDetectionMetricsCollector.class, cdefault);
        MetricsConfiguration.CONFIG_MAP.put(ShardStateCollector.class, cdefault);
        MetricsConfiguration.CONFIG_MAP.put(ClusterApplierServiceStatsCollector.class, cdefault);
        MetricsConfiguration.CONFIG_MAP.put(SearchBackPressureStatsCollector.class, cdefault);
        MetricsConfiguration.CONFIG_MAP.put(ElectionTermCollector.class, cdefault);
        MetricsConfiguration.CONFIG_MAP.put(ShardIndexingPressureMetricsCollector.class, cdefault);
        MetricsConfiguration.CONFIG_MAP.put(RTFDisksCollector.class, cdefault);
        MetricsConfiguration.CONFIG_MAP.put(RTFHeapMetricsCollector.class, cdefault);
        MetricsConfiguration.CONFIG_MAP.put(
                RTFShardOperationRateCollector.class,
                new MetricsConfiguration.MetricConfig(5000, 0));
        MetricsConfiguration.CONFIG_MAP.put(
                RTFNodeStatsAllShardsMetricsCollector.class,
                new MetricsConfiguration.MetricConfig(60000, 0));
        MetricsConfiguration.CONFIG_MAP.put(RTFThreadPoolMetricsCollector.class, cdefault);
        MetricsConfiguration.CONFIG_MAP.put(
                RTFCacheConfigMetricsCollector.class,
                new MetricsConfiguration.MetricConfig(60000, 0));
    }

    // These methods are utility functions for the Node Stat Metrics Collectors. These methods are
    // used by both the all
    // shards collector and the few shards collector.

    /**
     * This function is copied directly from IndicesService.java in opensearch as the original
     * function is not public we need to collect stats per shard based instead of calling the stat()
     * function to fetch all at once(which increases cpu usage on data nodes dramatically).
     *
     * @param indicesService Indices Services which keeps tracks of the indexes on the node
     * @param indexShard Shard to fetch the metrics for
     * @param flags The Metrics Buckets which needs to be fetched.
     * @return stats given in the flags param for the shard given in the indexShard param.
     */
    public static IndexShardStats indexShardStats(
            final IndicesService indicesService,
            final IndexShard indexShard,
            final CommonStatsFlags flags) {
        if (indexShard.routingEntry() == null) {
            return null;
        }

        return new IndexShardStats(
                indexShard.shardId(),
                new ShardStats[] {
                    new ShardStats(
                            indexShard.routingEntry(),
                            indexShard.shardPath(),
                            new CommonStats(
                                    indicesService.getIndicesQueryCache(), indexShard, flags),
                            null,
                            null,
                            null,
                            null)
                });
    }

    public static HashMap<ShardId, IndexShard> getShards() {
        HashMap<ShardId, IndexShard> shards = new HashMap<>();
        Iterator<IndexService> indexServices =
                OpenSearchResources.INSTANCE.getIndicesService().iterator();
        while (indexServices.hasNext()) {
            Iterator<IndexShard> indexShards = indexServices.next().iterator();
            while (indexShards.hasNext()) {
                IndexShard shard = indexShards.next();
                shards.put(shard.shardId(), shard);
            }
        }
        return shards;
    }

    public static final EnumSet<IndexShardState> CAN_WRITE_INDEX_BUFFER_STATES =
            EnumSet.of(
                    IndexShardState.RECOVERING,
                    IndexShardState.POST_RECOVERY,
                    IndexShardState.STARTED);

    /**
     * CPU Utilization is the time spend in CPU cycles divide by the total time cpu available time.
     * Total cpu available time would be the multiplication of num of processors and the process
     * time. It also takes into account the cpuShareFactor in case some adjustments are needed.
     *
     * @param numProcessors
     * @param totalOperationTime
     * @param cpuUsageTime
     * @param cpuShareFactor
     * @return
     */
    public static double calculateCPUUtilization(
            int numProcessors, long totalOperationTime, long cpuUsageTime, double cpuShareFactor) {
        LOG.debug(
                "Performance Analyzer CPUUtilization calculation with numProcessors: {}",
                numProcessors);
        LOG.debug(
                "Performance Analyzer CPUUtilization calculation with cpuShareFactor {}",
                cpuShareFactor);
        LOG.debug(
                "Performance Analyzer CPUUtilization calculation with totalCpuTime {}",
                cpuUsageTime);
        LOG.debug(
                "Performance Analyzer CPUUtilization calculation with totalOperationTime {}",
                totalOperationTime);
        if (totalOperationTime == 0l || cpuUsageTime == 0l || numProcessors == 0) {
            return 0.0d;
        }
        double totalAvailableCPUTime = Double.valueOf(totalOperationTime * numProcessors);
        double cpuUtil = cpuShareFactor * (cpuUsageTime / totalAvailableCPUTime);
        LOG.debug("Performance Analyzer CPUUtilization calculation with cpuUtil {}", cpuUtil);
        return cpuUtil;
    }
}
