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
public class RTFShardOperationCollector extends PerformanceAnalyzerMetricsCollector
        implements TelemetryCollector {

    private static final Logger LOG = LogManager.getLogger(RTFShardOperationCollector.class);
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(RTFShardOperationCollector.class).samplingInterval;

    private Counter indexingRateCounter;
    private Counter searchRateCounter;

    private Map<ShardId, ShardOperation> previousIndexOps;
    private final long lastCollectionTimeInMillis;

    private MetricsRegistry metricsRegistry;
    private boolean metricsInitialized;
    private final PerformanceAnalyzerController controller;
    private final ConfigOverridesWrapper configOverridesWrapper;

    public RTFShardOperationCollector(
            PerformanceAnalyzerController controller,
            ConfigOverridesWrapper configOverridesWrapper) {
        super(
                SAMPLING_TIME_INTERVAL,
                "RTFShardOperationCollector",
                StatMetrics.RTF_SHARD_OPERATION_COLLECTOR_EXECUTION_TIME,
                StatExceptionCode.RTF_SHARD_OPERATION_COLLECTOR_ERROR);

        this.controller = controller;
        this.configOverridesWrapper = configOverridesWrapper;
        this.metricsInitialized = false;
        this.previousIndexOps = new HashMap<>();
        this.lastCollectionTimeInMillis = System.currentTimeMillis();
    }

    @Override
    public void collectMetrics(long startTime) {
        if (controller.isCollectorDisabled(configOverridesWrapper, getCollectorName())) {
            LOG.info("RTFShardOperationCollector is disabled. Skipping collection.");
            return;
        }

        metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        if (metricsRegistry == null) {
            LOG.error("Could not get the instance of MetricsRegistry class");
            return;
        }

        initializeMetricsIfNeeded();
        LOG.debug("Executing collect metrics for RTFShardOperationCollector");

        // Get all shards
        Map<ShardId, IndexShard> currentShards = Utils.getShards();
        Map<ShardId, ShardOperation> currentIndexOpsMap = new HashMap<>();

        for (Map.Entry<ShardId, IndexShard> entry : currentShards.entrySet()) {
            ShardId shardId = entry.getKey();
            IndexShard shard = entry.getValue();

            try {
                long currentIndexingOps = shard.indexingStats().getTotal().getIndexCount();
                long currentSearchOps = shard.searchStats().getTotal().getQueryCount();

                if (previousIndexOps.containsKey(shardId)) {
                    long prevIndexingOps = previousIndexOps.get(shardId).indexOps();
                    long prevSearchOps = previousIndexOps.get(shardId).searchOps();
                    processOperations(
                            prevIndexingOps,
                            prevSearchOps,
                            currentIndexingOps,
                            currentSearchOps,
                            shardId);
                } else {
                    processOperations(0, 0, currentIndexingOps, currentSearchOps, shardId);
                }
                currentIndexOpsMap.put(
                        shardId, new ShardOperation(currentIndexingOps, currentSearchOps));
            } catch (Exception e) {
                LOG.error(
                        "Error collecting indexing/search rate metrics for shard {}: {}",
                        shardId,
                        e.getMessage());
            }
        }

        // Update previous values for next collection
        this.previousIndexOps = currentIndexOpsMap;
    }

    private void processOperations(
            long prevIndexingOps,
            long prevSearchOps,
            long currentIndexingOps,
            long currentSearchOps,
            ShardId shardId) {
        long indexingOpsDiff = Math.max(0, currentIndexingOps - prevIndexingOps);
        long searchOpsDiff = Math.max(0, currentSearchOps - prevSearchOps);

        if (indexingOpsDiff > 0) {
            Tags tags = createTags(shardId);
            indexingRateCounter.add(indexingOpsDiff, tags);
        }

        if (searchOpsDiff > 0) {
            Tags tags = createTags(shardId);
            searchRateCounter.add(searchOpsDiff, tags);
        }
    }

    // attributes= {index_name="test", shard_id="0"}
    private Tags createTags(ShardId shardId) {
        Tags shardOperationsMetricsTag =
                Tags.create()
                        .addTag(
                                RTFMetrics.CommonDimension.INDEX_NAME.toString(),
                                shardId.getIndexName())
                        .addTag(
                                RTFMetrics.CommonDimension.SHARD_ID.toString(),
                                String.valueOf(shardId.getId()));

        if (shardId.getIndex() != null) {
            shardOperationsMetricsTag.addTag(
                    RTFMetrics.CommonDimension.INDEX_UUID.toString(), shardId.getIndex().getUUID());
        }
        return shardOperationsMetricsTag;
    }

    private void initializeMetricsIfNeeded() {
        if (!metricsInitialized) {
            indexingRateCounter =
                    metricsRegistry.createCounter(
                            RTFMetrics.ShardOperationsValue.Constants.SHARD_INDEXING_RATE,
                            "Indexing operations per shard",
                            MetricUnits.RATE.toString());

            searchRateCounter =
                    metricsRegistry.createCounter(
                            RTFMetrics.ShardOperationsValue.Constants.SHARD_SEARCH_RATE,
                            "Search operations per shard",
                            MetricUnits.RATE.toString());

            metricsInitialized = true;
        }
    }

    /**
     * Stores the index and search operations for a shard.
     *
     * @param indexOps count of index operations.
     * @param searchOps count of search operations
     */
    private record ShardOperation(long indexOps, long searchOps) {}
}
