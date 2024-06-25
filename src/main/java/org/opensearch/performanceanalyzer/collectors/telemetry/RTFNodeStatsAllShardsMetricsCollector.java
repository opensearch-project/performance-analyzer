/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode.RTF_NODESTATS_COLLECTION_ERROR;
import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatMetrics.RTF_NODE_STATS_ALL_SHARDS_METRICS_COLLECTOR_EXECUTION_TIME;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.admin.indices.stats.CommonStatsFlags;
import org.opensearch.action.admin.indices.stats.IndexShardStats;
import org.opensearch.action.admin.indices.stats.ShardStats;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.index.shard.IndexShard;
import org.opensearch.indices.IndicesService;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.collectors.ValueCalculator;
import org.opensearch.performanceanalyzer.commons.collectors.MetricStatus;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.collectors.TelemetryCollector;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.RTFMetrics;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.util.Utils;
import org.opensearch.telemetry.metrics.Counter;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;

public class RTFNodeStatsAllShardsMetricsCollector extends PerformanceAnalyzerMetricsCollector
        implements TelemetryCollector {
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(RTFNodeStatsAllShardsMetricsCollector.class)
                    .samplingInterval;
    private static final Logger LOG =
            LogManager.getLogger(RTFNodeStatsAllShardsMetricsCollector.class);
    private Map<ShardId, IndexShard> currentShards;
    private Map<ShardId, ShardStats> currentPerShardStats;
    private Map<ShardId, ShardStats> prevPerShardStats;
    private MetricsRegistry metricsRegistry;
    private Counter cacheQueryHitMetrics;
    private Counter cacheQueryMissMetrics;
    private Counter cacheQuerySizeMetrics;
    private Counter cacheFieldDataEvictionMetrics;
    private Counter cacheFieldDataSizeMetrics;
    private Counter cacheRequestHitMetrics;
    private Counter cacheRequestMissMetrics;
    private Counter cacheRequestEvictionMetrics;
    private Counter cacheRequestSizeMetrics;
    private boolean metricsInitialised;
    private PerformanceAnalyzerController performanceAnalyzerController;
    private ConfigOverridesWrapper configOverridesWrapper;

    public RTFNodeStatsAllShardsMetricsCollector(
            PerformanceAnalyzerController performanceAnalyzerController,
            ConfigOverridesWrapper configOverridesWrapper) {
        super(
                SAMPLING_TIME_INTERVAL,
                "RTFNodeStatsMetricsCollector",
                RTF_NODE_STATS_ALL_SHARDS_METRICS_COLLECTOR_EXECUTION_TIME,
                RTF_NODESTATS_COLLECTION_ERROR);
        currentShards = new HashMap<>();
        prevPerShardStats = new HashMap<>();
        currentPerShardStats = new HashMap<>();
        this.metricsInitialised = false;
        this.performanceAnalyzerController = performanceAnalyzerController;
        this.configOverridesWrapper = configOverridesWrapper;
    }

    private void populateCurrentShards() {
        if (!currentShards.isEmpty()) {
            prevPerShardStats.putAll(currentPerShardStats);
            currentPerShardStats.clear();
        }
        currentShards.clear();
        currentShards = Utils.getShards();
    }

    private static final ImmutableMap<String, ValueCalculator> valueCalculators =
            ImmutableMap.of(
                    RTFMetrics.ShardStatsValue.INDEXING_THROTTLE_TIME.toString(),
                    (shardStats) ->
                            shardStats
                                    .getStats()
                                    .getIndexing()
                                    .getTotal()
                                    .getThrottleTime()
                                    .millis(),
                    RTFMetrics.ShardStatsValue.CACHE_QUERY_HIT.toString(),
                    (shardStats) -> shardStats.getStats().getQueryCache().getHitCount(),
                    RTFMetrics.ShardStatsValue.CACHE_QUERY_MISS.toString(),
                    (shardStats) -> shardStats.getStats().getQueryCache().getMissCount(),
                    RTFMetrics.ShardStatsValue.CACHE_QUERY_SIZE.toString(),
                    (shardStats) -> shardStats.getStats().getQueryCache().getMemorySizeInBytes(),
                    RTFMetrics.ShardStatsValue.CACHE_FIELDDATA_EVICTION.toString(),
                    (shardStats) -> shardStats.getStats().getFieldData().getEvictions(),
                    RTFMetrics.ShardStatsValue.CACHE_FIELDDATA_SIZE.toString(),
                    (shardStats) -> shardStats.getStats().getFieldData().getMemorySizeInBytes(),
                    RTFMetrics.ShardStatsValue.CACHE_REQUEST_HIT.toString(),
                    (shardStats) -> shardStats.getStats().getRequestCache().getHitCount(),
                    RTFMetrics.ShardStatsValue.CACHE_REQUEST_MISS.toString(),
                    (shardStats) -> shardStats.getStats().getRequestCache().getMissCount(),
                    RTFMetrics.ShardStatsValue.CACHE_REQUEST_EVICTION.toString(),
                    (shardStats) -> shardStats.getStats().getRequestCache().getEvictions(),
                    RTFMetrics.ShardStatsValue.CACHE_REQUEST_SIZE.toString(),
                    (shardStats) -> shardStats.getStats().getRequestCache().getMemorySizeInBytes());

    @Override
    public void collectMetrics(long startTime) {
        if (performanceAnalyzerController.isCollectorDisabled(
                configOverridesWrapper, getCollectorName())) {
            LOG.info("RTFDisksCollector is disabled. Skipping collection.");
            return;
        }
        IndicesService indicesService = OpenSearchResources.INSTANCE.getIndicesService();
        if (indicesService == null) {
            return;
        }

        metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        if (metricsRegistry == null) {
            LOG.error("could not get the instance of MetricsRegistry class");
            return;
        }

        LOG.debug("Executing collect metrics for RTFNodeStatsAllShardsMetricsCollector");
        initialiseMetricsIfNeeded();
        populateCurrentShards();
        populatePerShardStats(indicesService);

        for (Map.Entry currentShard : currentPerShardStats.entrySet()) {
            ShardId shardId = (ShardId) currentShard.getKey();
            ShardStats currentShardStats = (ShardStats) currentShard.getValue();
            if (prevPerShardStats.size() == 0) {
                // Populating value for the first run.
                recordMetrics(
                        new NodeStatsMetricsAllShardsPerCollectionStatus(currentShardStats),
                        shardId.getIndexName(),
                        String.valueOf(shardId.id()));
                continue;
            }
            ShardStats prevShardStats = prevPerShardStats.get(shardId);
            if (prevShardStats == null) {
                // Populate value for shards which are new and were not present in the previous
                // run.
                recordMetrics(
                        new NodeStatsMetricsAllShardsPerCollectionStatus(currentShardStats),
                        shardId.getIndexName(),
                        String.valueOf(shardId.id()));
                continue;
            }
            NodeStatsMetricsAllShardsPerCollectionStatus prevValue =
                    new NodeStatsMetricsAllShardsPerCollectionStatus(prevShardStats);
            NodeStatsMetricsAllShardsPerCollectionStatus currValue =
                    new NodeStatsMetricsAllShardsPerCollectionStatus(currentShardStats);
            populateDiffMetricValue(prevValue, currValue, shardId.getIndexName(), shardId.id());
        }
    }

    private void initialiseMetricsIfNeeded() {
        if (metricsInitialised == false) {
            cacheQueryHitMetrics =
                    metricsRegistry.createCounter(
                            RTFMetrics.ShardStatsValue.Constants.QUEY_CACHE_HIT_COUNT_VALUE,
                            "CacheQueryHit Metrics",
                            RTFMetrics.MetricUnits.COUNT.toString());

            cacheQueryMissMetrics =
                    metricsRegistry.createCounter(
                            RTFMetrics.ShardStatsValue.Constants.QUERY_CACHE_MISS_COUNT_VALUE,
                            "CacheQueryMiss Metrics",
                            RTFMetrics.MetricUnits.COUNT.toString());

            cacheQuerySizeMetrics =
                    metricsRegistry.createCounter(
                            RTFMetrics.ShardStatsValue.Constants.QUERY_CACHE_IN_BYTES_VALUE,
                            "CacheQuerySize Metrics",
                            RTFMetrics.MetricUnits.BYTE.toString());

            cacheFieldDataEvictionMetrics =
                    metricsRegistry.createCounter(
                            RTFMetrics.ShardStatsValue.Constants.FIELDDATA_EVICTION_VALUE,
                            "CacheFieldDataEviction Metrics",
                            RTFMetrics.MetricUnits.COUNT.toString());

            cacheFieldDataSizeMetrics =
                    metricsRegistry.createCounter(
                            RTFMetrics.ShardStatsValue.Constants.FIELD_DATA_IN_BYTES_VALUE,
                            "CacheFieldDataSize Metrics",
                            RTFMetrics.MetricUnits.BYTE.toString());

            cacheRequestHitMetrics =
                    metricsRegistry.createCounter(
                            RTFMetrics.ShardStatsValue.Constants.REQUEST_CACHE_HIT_COUNT_VALUE,
                            "CacheRequestHit Metrics",
                            RTFMetrics.MetricUnits.COUNT.toString());

            cacheRequestMissMetrics =
                    metricsRegistry.createCounter(
                            RTFMetrics.ShardStatsValue.Constants.REQUEST_CACHE_MISS_COUNT_VALUE,
                            "CacheRequestMiss Metrics",
                            RTFMetrics.MetricUnits.COUNT.toString());

            cacheRequestEvictionMetrics =
                    metricsRegistry.createCounter(
                            RTFMetrics.ShardStatsValue.Constants.REQUEST_CACHE_EVICTION_VALUE,
                            "CacheRequestEviction Metrics",
                            RTFMetrics.MetricUnits.COUNT.toString());

            cacheRequestSizeMetrics =
                    metricsRegistry.createCounter(
                            RTFMetrics.ShardStatsValue.Constants.REQUEST_CACHE_IN_BYTES_VALUE,
                            "CacheRequestSize Metrics",
                            RTFMetrics.MetricUnits.BYTE.toString());
            metricsInitialised = true;
        }
    }

    public void populatePerShardStats(IndicesService indicesService) {
        // Populate the shard stats per shard.
        for (Map.Entry currentShard : currentShards.entrySet()) {
            IndexShard currentIndexShard = (IndexShard) currentShard.getValue();
            IndexShardStats currentIndexShardStats =
                    Utils.indexShardStats(
                            indicesService,
                            currentIndexShard,
                            new CommonStatsFlags(
                                    CommonStatsFlags.Flag.QueryCache,
                                    CommonStatsFlags.Flag.FieldData,
                                    CommonStatsFlags.Flag.RequestCache));
            for (ShardStats shardStats : currentIndexShardStats.getShards()) {
                currentPerShardStats.put(currentIndexShardStats.getShardId(), shardStats);
            }
        }
    }

    private void recordMetrics(
            NodeStatsMetricsAllShardsPerCollectionStatus metrics,
            String indexName,
            String shardId) {
        Tags nodeStatsMetricsTag =
                Tags.create()
                        .addTag(RTFMetrics.CommonDimension.INDEX_NAME.toString(), indexName)
                        .addTag(RTFMetrics.CommonDimension.SHARD_ID.toString(), shardId);

        cacheQueryMissMetrics.add(metrics.getQueryCacheMissCount(), nodeStatsMetricsTag);
        cacheQuerySizeMetrics.add(metrics.getQueryCacheInBytes(), nodeStatsMetricsTag);
        cacheQueryHitMetrics.add(metrics.getQueryCacheHitCount(), nodeStatsMetricsTag);

        cacheFieldDataEvictionMetrics.add(metrics.getFieldDataEvictions(), nodeStatsMetricsTag);
        cacheFieldDataSizeMetrics.add(metrics.getFieldDataInBytes(), nodeStatsMetricsTag);

        cacheRequestEvictionMetrics.add(metrics.getRequestCacheEvictions(), nodeStatsMetricsTag);
        cacheRequestHitMetrics.add(metrics.getRequestCacheHitCount(), nodeStatsMetricsTag);
        cacheRequestMissMetrics.add(metrics.getRequestCacheMissCount(), nodeStatsMetricsTag);
        cacheRequestSizeMetrics.add(metrics.getRequestCacheInBytes(), nodeStatsMetricsTag);
    }

    public void populateDiffMetricValue(
            NodeStatsMetricsAllShardsPerCollectionStatus prevValue,
            NodeStatsMetricsAllShardsPerCollectionStatus currValue,
            String indexName,
            int shardId) {

        NodeStatsMetricsAllShardsPerCollectionStatus metrics =
                new NodeStatsMetricsAllShardsPerCollectionStatus(
                        Math.max((currValue.queryCacheHitCount - prevValue.queryCacheHitCount), 0),
                        Math.max(
                                (currValue.queryCacheMissCount - prevValue.queryCacheMissCount), 0),
                        currValue.queryCacheInBytes,
                        Math.max((currValue.fieldDataEvictions - prevValue.fieldDataEvictions), 0),
                        currValue.fieldDataInBytes,
                        Math.max(
                                (currValue.requestCacheHitCount - prevValue.requestCacheHitCount),
                                0),
                        Math.max(
                                (currValue.requestCacheMissCount - prevValue.requestCacheMissCount),
                                0),
                        Math.max(
                                (currValue.requestCacheEvictions - prevValue.requestCacheEvictions),
                                0),
                        currValue.requestCacheInBytes);

        recordMetrics(metrics, indexName, String.valueOf(shardId));
    }

    public static class NodeStatsMetricsAllShardsPerCollectionStatus extends MetricStatus {

        @JsonIgnore private ShardStats shardStats;

        private final long queryCacheHitCount;
        private final long queryCacheMissCount;
        private final long queryCacheInBytes;
        private final long fieldDataEvictions;
        private final long fieldDataInBytes;
        private final long requestCacheHitCount;
        private final long requestCacheMissCount;
        private final long requestCacheEvictions;
        private final long requestCacheInBytes;

        public NodeStatsMetricsAllShardsPerCollectionStatus(ShardStats shardStats) {
            super();
            this.shardStats = shardStats;

            this.queryCacheHitCount = calculate(RTFMetrics.ShardStatsValue.CACHE_QUERY_HIT);
            this.queryCacheMissCount = calculate(RTFMetrics.ShardStatsValue.CACHE_QUERY_MISS);
            this.queryCacheInBytes = calculate(RTFMetrics.ShardStatsValue.CACHE_QUERY_SIZE);
            this.fieldDataEvictions =
                    calculate(RTFMetrics.ShardStatsValue.CACHE_FIELDDATA_EVICTION);
            this.fieldDataInBytes = calculate(RTFMetrics.ShardStatsValue.CACHE_FIELDDATA_SIZE);
            this.requestCacheHitCount = calculate(RTFMetrics.ShardStatsValue.CACHE_REQUEST_HIT);
            this.requestCacheMissCount = calculate(RTFMetrics.ShardStatsValue.CACHE_REQUEST_MISS);
            this.requestCacheEvictions =
                    calculate(RTFMetrics.ShardStatsValue.CACHE_REQUEST_EVICTION);
            this.requestCacheInBytes = calculate(RTFMetrics.ShardStatsValue.CACHE_REQUEST_SIZE);
        }

        @SuppressWarnings("checkstyle:parameternumber")
        public NodeStatsMetricsAllShardsPerCollectionStatus(
                long queryCacheHitCount,
                long queryCacheMissCount,
                long queryCacheInBytes,
                long fieldDataEvictions,
                long fieldDataInBytes,
                long requestCacheHitCount,
                long requestCacheMissCount,
                long requestCacheEvictions,
                long requestCacheInBytes) {
            super();
            this.shardStats = null;

            this.queryCacheHitCount = queryCacheHitCount;
            this.queryCacheMissCount = queryCacheMissCount;
            this.queryCacheInBytes = queryCacheInBytes;
            this.fieldDataEvictions = fieldDataEvictions;
            this.fieldDataInBytes = fieldDataInBytes;
            this.requestCacheHitCount = requestCacheHitCount;
            this.requestCacheMissCount = requestCacheMissCount;
            this.requestCacheEvictions = requestCacheEvictions;
            this.requestCacheInBytes = requestCacheInBytes;
        }

        private long calculate(RTFMetrics.ShardStatsValue nodeMetric) {
            return valueCalculators.get(nodeMetric.toString()).calculateValue(shardStats);
        }

        @JsonIgnore
        public ShardStats getShardStats() {
            return shardStats;
        }

        @JsonProperty(RTFMetrics.ShardStatsValue.Constants.QUEY_CACHE_HIT_COUNT_VALUE)
        public long getQueryCacheHitCount() {
            return queryCacheHitCount;
        }

        @JsonProperty(RTFMetrics.ShardStatsValue.Constants.QUERY_CACHE_MISS_COUNT_VALUE)
        public long getQueryCacheMissCount() {
            return queryCacheMissCount;
        }

        @JsonProperty(RTFMetrics.ShardStatsValue.Constants.QUERY_CACHE_IN_BYTES_VALUE)
        public long getQueryCacheInBytes() {
            return queryCacheInBytes;
        }

        @JsonProperty(RTFMetrics.ShardStatsValue.Constants.FIELDDATA_EVICTION_VALUE)
        public long getFieldDataEvictions() {
            return fieldDataEvictions;
        }

        @JsonProperty(RTFMetrics.ShardStatsValue.Constants.FIELD_DATA_IN_BYTES_VALUE)
        public long getFieldDataInBytes() {
            return fieldDataInBytes;
        }

        @JsonProperty(RTFMetrics.ShardStatsValue.Constants.REQUEST_CACHE_HIT_COUNT_VALUE)
        public long getRequestCacheHitCount() {
            return requestCacheHitCount;
        }

        @JsonProperty(RTFMetrics.ShardStatsValue.Constants.REQUEST_CACHE_MISS_COUNT_VALUE)
        public long getRequestCacheMissCount() {
            return requestCacheMissCount;
        }

        @JsonProperty(RTFMetrics.ShardStatsValue.Constants.REQUEST_CACHE_EVICTION_VALUE)
        public long getRequestCacheEvictions() {
            return requestCacheEvictions;
        }

        @JsonProperty(RTFMetrics.ShardStatsValue.Constants.REQUEST_CACHE_IN_BYTES_VALUE)
        public long getRequestCacheInBytes() {
            return requestCacheInBytes;
        }
    }
}
