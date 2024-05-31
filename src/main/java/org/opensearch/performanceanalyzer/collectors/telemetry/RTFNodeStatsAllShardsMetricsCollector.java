/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode.NODESTATS_COLLECTION_ERROR;
import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatMetrics.NODE_STATS_ALL_SHARDS_METRICS_COLLECTOR_EXECUTION_TIME;

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
import org.opensearch.performanceanalyzer.collectors.NodeStatsAllShardsMetricsCollector;
import org.opensearch.performanceanalyzer.collectors.ValueCalculator;
import org.opensearch.performanceanalyzer.commons.collectors.MetricStatus;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.util.Utils;
import org.opensearch.telemetry.metrics.Counter;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;

public class RTFNodeStatsAllShardsMetricsCollector extends PerformanceAnalyzerMetricsCollector {
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(NodeStatsAllShardsMetricsCollector.class)
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

    public RTFNodeStatsAllShardsMetricsCollector() {
        super(
                SAMPLING_TIME_INTERVAL,
                "RTFNodeStatsMetricsCollector",
                NODE_STATS_ALL_SHARDS_METRICS_COLLECTOR_EXECUTION_TIME,
                NODESTATS_COLLECTION_ERROR);
        currentShards = new HashMap<>();
        prevPerShardStats = new HashMap<>();
        currentPerShardStats = new HashMap<>();
        this.metricsInitialised = false;
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
                    AllMetrics.ShardStatsValue.INDEXING_THROTTLE_TIME.toString(),
                    (shardStats) ->
                            shardStats
                                    .getStats()
                                    .getIndexing()
                                    .getTotal()
                                    .getThrottleTime()
                                    .millis(),
                    AllMetrics.ShardStatsValue.CACHE_QUERY_HIT.toString(),
                    (shardStats) -> shardStats.getStats().getQueryCache().getHitCount(),
                    AllMetrics.ShardStatsValue.CACHE_QUERY_MISS.toString(),
                    (shardStats) -> shardStats.getStats().getQueryCache().getMissCount(),
                    AllMetrics.ShardStatsValue.CACHE_QUERY_SIZE.toString(),
                    (shardStats) -> shardStats.getStats().getQueryCache().getMemorySizeInBytes(),
                    AllMetrics.ShardStatsValue.CACHE_FIELDDATA_EVICTION.toString(),
                    (shardStats) -> shardStats.getStats().getFieldData().getEvictions(),
                    AllMetrics.ShardStatsValue.CACHE_FIELDDATA_SIZE.toString(),
                    (shardStats) -> shardStats.getStats().getFieldData().getMemorySizeInBytes(),
                    AllMetrics.ShardStatsValue.CACHE_REQUEST_HIT.toString(),
                    (shardStats) -> shardStats.getStats().getRequestCache().getHitCount(),
                    AllMetrics.ShardStatsValue.CACHE_REQUEST_MISS.toString(),
                    (shardStats) -> shardStats.getStats().getRequestCache().getMissCount(),
                    AllMetrics.ShardStatsValue.CACHE_REQUEST_EVICTION.toString(),
                    (shardStats) -> shardStats.getStats().getRequestCache().getEvictions(),
                    AllMetrics.ShardStatsValue.CACHE_REQUEST_SIZE.toString(),
                    (shardStats) -> shardStats.getStats().getRequestCache().getMemorySizeInBytes());

    @Override
    public void collectMetrics(long startTime) {
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
                            AllMetrics.ShardStatsValue.Constants.QUEY_CACHE_HIT_COUNT_VALUE,
                            "CacheQueryHit Metrics",
                            "");

            cacheQueryMissMetrics =
                    metricsRegistry.createCounter(
                            AllMetrics.ShardStatsValue.Constants.QUERY_CACHE_MISS_COUNT_VALUE,
                            "CacheQueryMiss Metrics",
                            "");

            cacheQuerySizeMetrics =
                    metricsRegistry.createCounter(
                            AllMetrics.ShardStatsValue.Constants.QUERY_CACHE_IN_BYTES_VALUE,
                            "CacheQuerySize Metrics",
                            "");

            cacheFieldDataEvictionMetrics =
                    metricsRegistry.createCounter(
                            AllMetrics.ShardStatsValue.Constants.FIELDDATA_EVICTION_VALUE,
                            "CacheFieldDataEviction Metrics",
                            "");

            cacheFieldDataSizeMetrics =
                    metricsRegistry.createCounter(
                            AllMetrics.ShardStatsValue.Constants.FIELD_DATA_IN_BYTES_VALUE,
                            "CacheFieldDataSize Metrics",
                            "");

            cacheRequestHitMetrics =
                    metricsRegistry.createCounter(
                            AllMetrics.ShardStatsValue.Constants.REQUEST_CACHE_HIT_COUNT_VALUE,
                            "CacheRequestHit Metrics",
                            "");

            cacheRequestMissMetrics =
                    metricsRegistry.createCounter(
                            AllMetrics.ShardStatsValue.Constants.REQUEST_CACHE_MISS_COUNT_VALUE,
                            "CacheRequestMiss Metrics",
                            "");

            cacheRequestEvictionMetrics =
                    metricsRegistry.createCounter(
                            AllMetrics.ShardStatsValue.Constants.REQUEST_CACHE_EVICTION_VALUE,
                            "CacheRequestEviction Metrics",
                            "");

            cacheRequestSizeMetrics =
                    metricsRegistry.createCounter(
                            AllMetrics.ShardStatsValue.Constants.REQUEST_CACHE_IN_BYTES_VALUE,
                            "CacheRequestSize Metrics",
                            "");
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
            String IndexName,
            String ShardId) {
        Tags nodeStatsMetricsTag =
                Tags.create().addTag("index_name", IndexName).addTag("shard_id", ShardId);

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
            String IndexName,
            int ShardId) {

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

        recordMetrics(metrics, IndexName, String.valueOf(ShardId));
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

            this.queryCacheHitCount = calculate(AllMetrics.ShardStatsValue.CACHE_QUERY_HIT);
            this.queryCacheMissCount = calculate(AllMetrics.ShardStatsValue.CACHE_QUERY_MISS);
            this.queryCacheInBytes = calculate(AllMetrics.ShardStatsValue.CACHE_QUERY_SIZE);
            this.fieldDataEvictions =
                    calculate(AllMetrics.ShardStatsValue.CACHE_FIELDDATA_EVICTION);
            this.fieldDataInBytes = calculate(AllMetrics.ShardStatsValue.CACHE_FIELDDATA_SIZE);
            this.requestCacheHitCount = calculate(AllMetrics.ShardStatsValue.CACHE_REQUEST_HIT);
            this.requestCacheMissCount = calculate(AllMetrics.ShardStatsValue.CACHE_REQUEST_MISS);
            this.requestCacheEvictions =
                    calculate(AllMetrics.ShardStatsValue.CACHE_REQUEST_EVICTION);
            this.requestCacheInBytes = calculate(AllMetrics.ShardStatsValue.CACHE_REQUEST_SIZE);
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

        private long calculate(AllMetrics.ShardStatsValue nodeMetric) {
            return valueCalculators.get(nodeMetric.toString()).calculateValue(shardStats);
        }

        @JsonIgnore
        public ShardStats getShardStats() {
            return shardStats;
        }

        @JsonProperty(AllMetrics.ShardStatsValue.Constants.QUEY_CACHE_HIT_COUNT_VALUE)
        public long getQueryCacheHitCount() {
            return queryCacheHitCount;
        }

        @JsonProperty(AllMetrics.ShardStatsValue.Constants.QUERY_CACHE_MISS_COUNT_VALUE)
        public long getQueryCacheMissCount() {
            return queryCacheMissCount;
        }

        @JsonProperty(AllMetrics.ShardStatsValue.Constants.QUERY_CACHE_IN_BYTES_VALUE)
        public long getQueryCacheInBytes() {
            return queryCacheInBytes;
        }

        @JsonProperty(AllMetrics.ShardStatsValue.Constants.FIELDDATA_EVICTION_VALUE)
        public long getFieldDataEvictions() {
            return fieldDataEvictions;
        }

        @JsonProperty(AllMetrics.ShardStatsValue.Constants.FIELD_DATA_IN_BYTES_VALUE)
        public long getFieldDataInBytes() {
            return fieldDataInBytes;
        }

        @JsonProperty(AllMetrics.ShardStatsValue.Constants.REQUEST_CACHE_HIT_COUNT_VALUE)
        public long getRequestCacheHitCount() {
            return requestCacheHitCount;
        }

        @JsonProperty(AllMetrics.ShardStatsValue.Constants.REQUEST_CACHE_MISS_COUNT_VALUE)
        public long getRequestCacheMissCount() {
            return requestCacheMissCount;
        }

        @JsonProperty(AllMetrics.ShardStatsValue.Constants.REQUEST_CACHE_EVICTION_VALUE)
        public long getRequestCacheEvictions() {
            return requestCacheEvictions;
        }

        @JsonProperty(AllMetrics.ShardStatsValue.Constants.REQUEST_CACHE_IN_BYTES_VALUE)
        public long getRequestCacheInBytes() {
            return requestCacheInBytes;
        }
    }
}
