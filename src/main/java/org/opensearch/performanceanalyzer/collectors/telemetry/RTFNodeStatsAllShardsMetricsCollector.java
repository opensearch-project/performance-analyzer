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
    private HashMap<ShardId, IndexShard> currentShards;
    private HashMap<ShardId, ShardStats> currentPerShardStats;
    private HashMap<ShardId, ShardStats> prevPerShardStats;
    private MetricsRegistry metricsRegistry;
    private Counter CacheQueryHitMetrics;
    private Counter CacheQueryMissMetrics;
    private Counter CacheQuerySizeMetrics;
    private Counter CacheFieldDataEvictionMetrics;
    private Counter CacheFieldDataSizeMetrics;
    private Counter CacheRequestHitMetrics;
    private Counter CacheRequestMissMetrics;
    private Counter CacheRequestEvictionMetrics;
    private Counter CacheRequestSizeMetrics;

    public RTFNodeStatsAllShardsMetricsCollector() {
        super(
                SAMPLING_TIME_INTERVAL,
                "RTFNodeStatsMetricsCollector",
                NODE_STATS_ALL_SHARDS_METRICS_COLLECTOR_EXECUTION_TIME,
                NODESTATS_COLLECTION_ERROR);
        currentShards = new HashMap<>();
        prevPerShardStats = new HashMap<>();
        currentPerShardStats = new HashMap<>();
    }

    private void populateCurrentShards() {
        if (!currentShards.isEmpty()) {
            prevPerShardStats.putAll(currentPerShardStats);
            currentPerShardStats.clear();
        }
        currentShards.clear();
        currentShards = Utils.getShards();
    }

    private static final Map<String, ValueCalculator> maps =
            new HashMap<String, ValueCalculator>() {
                {
                    put(
                            AllMetrics.ShardStatsValue.INDEXING_THROTTLE_TIME.toString(),
                            (shardStats) ->
                                    shardStats
                                            .getStats()
                                            .getIndexing()
                                            .getTotal()
                                            .getThrottleTime()
                                            .millis());

                    put(
                            AllMetrics.ShardStatsValue.CACHE_QUERY_HIT.toString(),
                            (shardStats) -> shardStats.getStats().getQueryCache().getHitCount());
                    put(
                            AllMetrics.ShardStatsValue.CACHE_QUERY_MISS.toString(),
                            (shardStats) -> shardStats.getStats().getQueryCache().getMissCount());
                    put(
                            AllMetrics.ShardStatsValue.CACHE_QUERY_SIZE.toString(),
                            (shardStats) ->
                                    shardStats.getStats().getQueryCache().getMemorySizeInBytes());

                    put(
                            AllMetrics.ShardStatsValue.CACHE_FIELDDATA_EVICTION.toString(),
                            (shardStats) -> shardStats.getStats().getFieldData().getEvictions());
                    put(
                            AllMetrics.ShardStatsValue.CACHE_FIELDDATA_SIZE.toString(),
                            (shardStats) ->
                                    shardStats.getStats().getFieldData().getMemorySizeInBytes());

                    put(
                            AllMetrics.ShardStatsValue.CACHE_REQUEST_HIT.toString(),
                            (shardStats) -> shardStats.getStats().getRequestCache().getHitCount());
                    put(
                            AllMetrics.ShardStatsValue.CACHE_REQUEST_MISS.toString(),
                            (shardStats) -> shardStats.getStats().getRequestCache().getMissCount());
                    put(
                            AllMetrics.ShardStatsValue.CACHE_REQUEST_EVICTION.toString(),
                            (shardStats) -> shardStats.getStats().getRequestCache().getEvictions());
                    put(
                            AllMetrics.ShardStatsValue.CACHE_REQUEST_SIZE.toString(),
                            (shardStats) ->
                                    shardStats.getStats().getRequestCache().getMemorySizeInBytes());
                }
            };

    private static final ImmutableMap<String, ValueCalculator> valueCalculators =
            ImmutableMap.copyOf(maps);

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

        LOG.info("Executing collect metrics for RTFNodeStatsAllShardsMetricsCollector");
        initialiseMetrics();
        populateCurrentShards();
        populatePerShardStats(indicesService);

        for (HashMap.Entry currentShard : currentPerShardStats.entrySet()) {
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

    private void initialiseMetrics() {
        CacheQueryHitMetrics =
                metricsRegistry.createCounter(
                        AllMetrics.ShardStatsValue.Constants.QUEY_CACHE_HIT_COUNT_VALUE,
                        "CacheQueryHit Metrics",
                        "1");

        CacheQueryMissMetrics =
                metricsRegistry.createCounter(
                        AllMetrics.ShardStatsValue.Constants.QUERY_CACHE_MISS_COUNT_VALUE,
                        "CacheQueryMiss Metrics",
                        "1");

        CacheQuerySizeMetrics =
                metricsRegistry.createCounter(
                        AllMetrics.ShardStatsValue.Constants.QUERY_CACHE_IN_BYTES_VALUE,
                        "CacheQuerySize Metrics",
                        "1");

        CacheFieldDataEvictionMetrics =
                metricsRegistry.createCounter(
                        AllMetrics.ShardStatsValue.Constants.FIELDDATA_EVICTION_VALUE,
                        "CacheFieldDataEviction Metrics",
                        "1");

        CacheFieldDataSizeMetrics =
                metricsRegistry.createCounter(
                        AllMetrics.ShardStatsValue.Constants.FIELD_DATA_IN_BYTES_VALUE,
                        "CacheFieldDataSize Metrics",
                        "1");

        CacheRequestHitMetrics =
                metricsRegistry.createCounter(
                        AllMetrics.ShardStatsValue.Constants.REQUEST_CACHE_HIT_COUNT_VALUE,
                        "CacheRequestHit Metrics",
                        "1");

        CacheRequestMissMetrics =
                metricsRegistry.createCounter(
                        AllMetrics.ShardStatsValue.Constants.REQUEST_CACHE_MISS_COUNT_VALUE,
                        "CacheRequestMiss Metrics",
                        "1");

        CacheRequestEvictionMetrics =
                metricsRegistry.createCounter(
                        AllMetrics.ShardStatsValue.Constants.REQUEST_CACHE_EVICTION_VALUE,
                        "CacheRequestEviction Metrics",
                        "1");

        CacheRequestSizeMetrics =
                metricsRegistry.createCounter(
                        AllMetrics.ShardStatsValue.Constants.REQUEST_CACHE_IN_BYTES_VALUE,
                        "CacheRequestSize Metrics",
                        "1");
    }

    public void populatePerShardStats(IndicesService indicesService) {
        // Populate the shard stats per shard.
        for (HashMap.Entry currentShard : currentShards.entrySet()) {
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
        Tags NodeStatsMetricsTag =
                Tags.create().addTag("index_name", IndexName).addTag("shard_id", ShardId);

        CacheQueryMissMetrics.add(metrics.getQueryCacheMissCount(), NodeStatsMetricsTag);
        CacheQuerySizeMetrics.add(metrics.getQueryCacheInBytes(), NodeStatsMetricsTag);
        CacheQueryHitMetrics.add(metrics.getQueryCacheHitCount(), NodeStatsMetricsTag);

        CacheFieldDataEvictionMetrics.add(metrics.getFieldDataEvictions(), NodeStatsMetricsTag);
        CacheFieldDataSizeMetrics.add(metrics.getFieldDataInBytes(), NodeStatsMetricsTag);

        CacheRequestEvictionMetrics.add(metrics.getRequestCacheEvictions(), NodeStatsMetricsTag);
        CacheRequestHitMetrics.add(metrics.getRequestCacheHitCount(), NodeStatsMetricsTag);
        CacheRequestMissMetrics.add(metrics.getRequestCacheMissCount(), NodeStatsMetricsTag);
        CacheRequestSizeMetrics.add(metrics.getRequestCacheInBytes(), NodeStatsMetricsTag);
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
