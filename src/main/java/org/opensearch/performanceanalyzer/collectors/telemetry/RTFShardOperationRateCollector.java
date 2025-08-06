/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.index.shard.IndexShard;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.collectors.TelemetryCollector;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.RTFMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.RTFMetrics.MetricUnits;
import org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode;
import org.opensearch.performanceanalyzer.commons.stats.metrics.StatMetrics;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.util.Utils;
import org.opensearch.telemetry.metrics.Counter;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;

/**
 * This collector measures indexing and search rate per shard. The metric measurement is difference
 * between current and last window's operation. For example - if the last window had operation count
 * as 10, and now it changed to 12, then collector will publish 2 ops/interval.
 */
public class RTFShardOperationRateCollector extends PerformanceAnalyzerMetricsCollector
        implements TelemetryCollector {

    private static final Logger LOG = LogManager.getLogger(RTFShardOperationRateCollector.class);
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(RTFShardOperationRateCollector.class)
                    .samplingInterval;

    private Counter indexingRateCounter;
    private Counter searchRateCounter;

    private final Map<ShardId, Long> prevIndexingOps;
    private final Map<ShardId, Long> prevSearchOps;
    private long lastCollectionTimeInMillis;

    private MetricsRegistry metricsRegistry;
    private boolean metricsInitialized;
    private final PerformanceAnalyzerController controller;
    private final ConfigOverridesWrapper configOverridesWrapper;

    public RTFShardOperationRateCollector(
            PerformanceAnalyzerController controller,
            ConfigOverridesWrapper configOverridesWrapper) {
        super(
                SAMPLING_TIME_INTERVAL,
                "RTFShardOperationRateCollector",
                StatMetrics.RTF_SHARD_OPERATION_RATE_COLLECTOR_EXECUTION_TIME,
                StatExceptionCode.RTF_SHARD_OPERATION_RATE_COLLECTOR_ERROR);

        this.controller = controller;
        this.configOverridesWrapper = configOverridesWrapper;
        this.metricsInitialized = false;

        this.prevIndexingOps = new HashMap<>();
        this.prevSearchOps = new HashMap<>();
        this.lastCollectionTimeInMillis = System.currentTimeMillis();
    }

    @Override
    public void collectMetrics(long startTime) {
        if (controller.isCollectorDisabled(configOverridesWrapper, getCollectorName())) {
            LOG.info("RTFShardOperationRateCollector is disabled. Skipping collection.");
            return;
        }

        metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        if (metricsRegistry == null) {
            LOG.error("Could not get the instance of MetricsRegistry class");
            return;
        }

        initializeMetricsIfNeeded();
        LOG.debug("Executing collect metrics for RTFShardOperationRateCollector");

        // Get all shards
        Map<ShardId, IndexShard> currentShards = Utils.getShards();

        for (Map.Entry<ShardId, IndexShard> entry : currentShards.entrySet()) {
            ShardId shardId = entry.getKey();
            IndexShard shard = entry.getValue();

            try {
                long currentIndexingOps = shard.indexingStats().getTotal().getIndexCount();
                long currentSearchOps = shard.searchStats().getTotal().getQueryCount();

                if (prevIndexingOps.containsKey(shardId)) {
                    processIndexingOperations(shardId, currentIndexingOps);
                }

                if (prevSearchOps.containsKey(shardId)) {
                    processSearchOperations(shardId, currentSearchOps);
                }

                // Update previous values for next collection
                prevIndexingOps.put(shardId, currentIndexingOps);
                prevSearchOps.put(shardId, currentSearchOps);
            } catch (Exception e) {
                LOG.error(
                        "Error collecting indexing/search rate metrics for shard {}: {}",
                        shardId,
                        e.getMessage());
            }
        }
    }

    private void processIndexingOperations(ShardId shardId, long currentIndexingOps) {
        long indexingOpsDiff = Math.max(0, currentIndexingOps - prevIndexingOps.get(shardId));

        if (indexingOpsDiff > 0) {
            Tags tags = createTags(shardId);
            indexingRateCounter.add(indexingOpsDiff, tags);
        }
    }

    private void processSearchOperations(ShardId shardId, long currentSearchOps) {
        long searchOpsDiff = Math.max(0, currentSearchOps - prevSearchOps.get(shardId));

        if (searchOpsDiff > 0) {
            Tags tags = createTags(shardId);
            searchRateCounter.add(searchOpsDiff, tags);
        }
    }

    // attributes= {index_name="test", shard_id="0"}
    private Tags createTags(ShardId shardId) {
        return Tags.create()
                .addTag(RTFMetrics.CommonDimension.INDEX_NAME.toString(), shardId.getIndexName())
                .addTag(
                        RTFMetrics.CommonDimension.SHARD_ID.toString(),
                        String.valueOf(shardId.getId()));
    }

    private void initializeMetricsIfNeeded() {
        if (!metricsInitialized) {
            indexingRateCounter =
                    metricsRegistry.createCounter(
                            RTFMetrics.OperationsValue.Constants.INDEXING_RATE,
                            "Indexing operations per shard",
                            MetricUnits.RATE.toString());

            searchRateCounter =
                    metricsRegistry.createCounter(
                            RTFMetrics.OperationsValue.Constants.SEARCH_RATE,
                            "Search operations per shard",
                            MetricUnits.RATE.toString());

            metricsInitialized = true;
        }
    }
}
