/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.admin.indices.stats.CommonStatsFlags;
import org.opensearch.action.admin.indices.stats.IndexShardStats;
import org.opensearch.action.admin.indices.stats.ShardStats;
import org.opensearch.index.shard.IndexShard;
import org.opensearch.index.shard.ShardId;
import org.opensearch.indices.IndicesService;
import org.opensearch.indices.NodeIndicesStats;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.PerformanceAnalyzerApp;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.ShardStatsValue;
import org.opensearch.performanceanalyzer.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.rca.framework.metrics.ExceptionsAndErrors;
import org.opensearch.performanceanalyzer.util.Utils;

/**
 * This collector collects metrics for fixed number of shards on a node in a single run. These
 * metrics are heavy weight metrics which have performance impacts on the performance of the node.
 * The number of shards is set via a cluster settings api. The parameter to set is
 * shardsPerCollection. The metrics will be populated for these many shards in a single run.
 */
@SuppressWarnings("unchecked")
public class NodeStatsFixedShardsMetricsCollector extends PerformanceAnalyzerMetricsCollector
        implements MetricsProcessor {
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(NodeStatsAllShardsMetricsCollector.class)
                    .samplingInterval;
    private static final int KEYS_PATH_LENGTH = 2;
    private static final Logger LOG =
            LogManager.getLogger(NodeStatsFixedShardsMetricsCollector.class);
    private HashMap<ShardId, IndexShard> currentShards;
    private Iterator<HashMap.Entry<ShardId, IndexShard>> currentShardsIter;
    private final PerformanceAnalyzerController controller;

    public NodeStatsFixedShardsMetricsCollector(final PerformanceAnalyzerController controller) {
        super(SAMPLING_TIME_INTERVAL, "NodeStatsMetrics");
        currentShards = new HashMap<>();
        currentShardsIter = currentShards.entrySet().iterator();
        this.controller = controller;
    }

    private void populateCurrentShards() {
        currentShards.clear();
        currentShards = Utils.getShards();
        currentShardsIter = currentShards.entrySet().iterator();
    }

    private Map<String, ValueCalculator> valueCalculators =
            new HashMap<String, ValueCalculator>() {
                {
                    put(
                            ShardStatsValue.INDEXING_THROTTLE_TIME.toString(),
                            (shardStats) ->
                                    shardStats
                                            .getStats()
                                            .getIndexing()
                                            .getTotal()
                                            .getThrottleTime()
                                            .millis());

                    put(
                            ShardStatsValue.REFRESH_EVENT.toString(),
                            (shardStats) -> shardStats.getStats().getRefresh().getTotal());
                    put(
                            ShardStatsValue.REFRESH_TIME.toString(),
                            (shardStats) ->
                                    shardStats.getStats().getRefresh().getTotalTimeInMillis());

                    put(
                            ShardStatsValue.FLUSH_EVENT.toString(),
                            (shardStats) -> shardStats.getStats().getFlush().getTotal());
                    put(
                            ShardStatsValue.FLUSH_TIME.toString(),
                            (shardStats) ->
                                    shardStats.getStats().getFlush().getTotalTimeInMillis());

                    put(
                            ShardStatsValue.MERGE_EVENT.toString(),
                            (shardStats) -> shardStats.getStats().getMerge().getTotal());
                    put(
                            ShardStatsValue.MERGE_TIME.toString(),
                            (shardStats) ->
                                    shardStats.getStats().getMerge().getTotalTimeInMillis());
                    put(
                            ShardStatsValue.MERGE_CURRENT_EVENT.toString(),
                            (shardStats) -> shardStats.getStats().getMerge().getCurrent());

                    put(
                            ShardStatsValue.SEGMENTS_TOTAL.toString(),
                            (shardStats) -> shardStats.getStats().getSegments().getCount());
                    put(
                            ShardStatsValue.INDEX_WRITER_MEMORY.toString(),
                            (shardStats) ->
                                    shardStats
                                            .getStats()
                                            .getSegments()
                                            .getIndexWriterMemoryInBytes());
                    put(
                            ShardStatsValue.VERSION_MAP_MEMORY.toString(),
                            (shardStats) ->
                                    shardStats
                                            .getStats()
                                            .getSegments()
                                            .getVersionMapMemoryInBytes());
                    put(
                            ShardStatsValue.BITSET_MEMORY.toString(),
                            (shardStats) ->
                                    shardStats.getStats().getSegments().getBitsetMemoryInBytes());

                    put(
                            ShardStatsValue.INDEXING_BUFFER.toString(),
                            (shardStats) -> getIndexBufferBytes(shardStats));
                    put(
                            ShardStatsValue.SHARD_SIZE_IN_BYTES.toString(),
                            (shardStats) -> shardStats.getStats().getStore().getSizeInBytes());
                }
            };

    private long getIndexBufferBytes(ShardStats shardStats) {
        IndexShard shard = currentShards.get(shardStats.getShardRouting().shardId());

        if (shard == null) {
            return 0;
        }

        return Utils.CAN_WRITE_INDEX_BUFFER_STATES.contains(shard.state())
                ? shard.getWritingBytes() + shard.getIndexBufferRAMBytesUsed()
                : 0;
    }

    @Override
    public String getMetricsPath(long startTime, String... keysPath) {
        // throw exception if keysPath.length is not equal to 2 (Keys should be Index Name, and
        // ShardId)
        if (keysPath.length != KEYS_PATH_LENGTH) {
            throw new RuntimeException("keys length should be " + KEYS_PATH_LENGTH);
        }

        return PerformanceAnalyzerMetrics.generatePath(
                startTime, PerformanceAnalyzerMetrics.sIndicesPath, keysPath[0], keysPath[1]);
    }

    @Override
    public void collectMetrics(long startTime) {
        IndicesService indicesService = OpenSearchResources.INSTANCE.getIndicesService();

        if (indicesService == null) {
            return;
        }

        try {
            // reach the end of current shardId list. retrieve new shard list from IndexService
            if (!currentShardsIter.hasNext()) {
                populateCurrentShards();
            }
            for (int i = 0; i < controller.getNodeStatsShardsPerCollection(); i++) {
                if (!currentShardsIter.hasNext()) {
                    break;
                }
                IndexShard currentIndexShard = currentShardsIter.next().getValue();
                IndexShardStats currentIndexShardStats =
                        Utils.indexShardStats(
                                indicesService,
                                currentIndexShard,
                                new CommonStatsFlags(
                                        CommonStatsFlags.Flag.Segments,
                                        CommonStatsFlags.Flag.Store,
                                        CommonStatsFlags.Flag.Indexing,
                                        CommonStatsFlags.Flag.Merge,
                                        CommonStatsFlags.Flag.Flush,
                                        CommonStatsFlags.Flag.Refresh,
                                        CommonStatsFlags.Flag.Recovery));
                for (ShardStats shardStats : currentIndexShardStats.getShards()) {
                    StringBuilder value = new StringBuilder();

                    value.append(PerformanceAnalyzerMetrics.getJsonCurrentMilliSeconds());
                    // - go through the list of metrics to be collected and emit
                    value.append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                            .append(
                                    new NodeStatsMetricsFixedShardsPerCollectionStatus(shardStats)
                                            .serialize());

                    saveMetricValues(
                            value.toString(),
                            startTime,
                            currentIndexShardStats.getShardId().getIndexName(),
                            String.valueOf(currentIndexShardStats.getShardId().id()));
                }
            }
        } catch (Exception ex) {
            LOG.debug(
                    "Exception in Collecting NodesStats Metrics: {} for startTime {} with ExceptionCode: {}",
                    () -> ex.toString(),
                    () -> startTime,
                    () -> StatExceptionCode.NODESTATS_COLLECTION_ERROR.toString());
            PerformanceAnalyzerApp.ERRORS_AND_EXCEPTIONS_AGGREGATOR.updateStat(
                    ExceptionsAndErrors.NODESTATS_COLLECTION_ERROR, "", 1);
        }
    }

    // - Separated to have a unit test; and catch any code changes around this field
    Field getNodeIndicesStatsByShardField() throws Exception {
        Field field = NodeIndicesStats.class.getDeclaredField("statsByShard");
        field.setAccessible(true);
        return field;
    }

    public class NodeStatsMetricsFixedShardsPerCollectionStatus extends MetricStatus {

        @JsonIgnore private ShardStats shardStats;

        private final long indexingThrottleTime;
        private final long refreshCount;
        private final long refreshTime;
        private final long flushCount;
        private final long flushTime;
        private final long mergeCount;
        private final long mergeTime;
        private final long mergeCurrent;
        private final long indexBufferBytes;
        private final long segmentCount;
        private final long indexWriterMemory;
        private final long versionMapMemory;
        private final long bitsetMemory;
        private final long shardSizeInBytes;

        public NodeStatsMetricsFixedShardsPerCollectionStatus(ShardStats shardStats) {
            super();
            this.shardStats = shardStats;

            this.indexingThrottleTime = calculate(ShardStatsValue.INDEXING_THROTTLE_TIME);
            this.refreshCount = calculate(ShardStatsValue.REFRESH_EVENT);
            this.refreshTime = calculate(ShardStatsValue.REFRESH_TIME);
            this.flushCount = calculate(ShardStatsValue.FLUSH_EVENT);
            this.flushTime = calculate(ShardStatsValue.FLUSH_TIME);
            this.mergeCount = calculate(ShardStatsValue.MERGE_EVENT);
            this.mergeTime = calculate(ShardStatsValue.MERGE_TIME);
            this.mergeCurrent = calculate(ShardStatsValue.MERGE_CURRENT_EVENT);
            this.indexBufferBytes = calculate(ShardStatsValue.INDEXING_BUFFER);
            this.segmentCount = calculate(ShardStatsValue.SEGMENTS_TOTAL);
            this.indexWriterMemory = calculate(ShardStatsValue.INDEX_WRITER_MEMORY);
            this.versionMapMemory = calculate(ShardStatsValue.VERSION_MAP_MEMORY);
            this.bitsetMemory = calculate(ShardStatsValue.BITSET_MEMORY);
            this.shardSizeInBytes = calculate(ShardStatsValue.SHARD_SIZE_IN_BYTES);
        }

        private long calculate(ShardStatsValue nodeMetric) {
            return valueCalculators.get(nodeMetric.toString()).calculateValue(shardStats);
        }

        @JsonProperty(ShardStatsValue.Constants.INDEXING_THROTTLE_TIME_VALUE)
        public long getIndexingThrottleTime() {
            return indexingThrottleTime;
        }

        @JsonProperty(ShardStatsValue.Constants.REFRESH_COUNT_VALUE)
        public long getRefreshCount() {
            return refreshCount;
        }

        @JsonProperty(ShardStatsValue.Constants.REFRESH_TIME_VALUE)
        public long getRefreshTime() {
            return refreshTime;
        }

        @JsonProperty(ShardStatsValue.Constants.FLUSH_COUNT_VALUE)
        public long getFlushCount() {
            return flushCount;
        }

        @JsonProperty(ShardStatsValue.Constants.FLUSH_TIME_VALUE)
        public long getFlushTime() {
            return flushTime;
        }

        @JsonProperty(ShardStatsValue.Constants.MERGE_COUNT_VALUE)
        public long getMergeCount() {
            return mergeCount;
        }

        @JsonProperty(ShardStatsValue.Constants.MERGE_TIME_VALUE)
        public long getMergeTime() {
            return mergeTime;
        }

        @JsonProperty(ShardStatsValue.Constants.MERGE_CURRENT_VALUE)
        public long getMergeCurrent() {
            return mergeCurrent;
        }

        @JsonIgnore
        public ShardStats getShardStats() {
            return shardStats;
        }

        @JsonProperty(ShardStatsValue.Constants.INDEX_BUFFER_BYTES_VALUE)
        public long getIndexBufferBytes() {
            return indexBufferBytes;
        }

        @JsonProperty(ShardStatsValue.Constants.SEGMENTS_COUNT_VALUE)
        public long getSegmentCount() {
            return segmentCount;
        }

        @JsonProperty(ShardStatsValue.Constants.INDEX_WRITER_MEMORY_VALUE)
        public long getIndexWriterMemory() {
            return indexWriterMemory;
        }

        @JsonProperty(ShardStatsValue.Constants.VERSION_MAP_MEMORY_VALUE)
        public long getVersionMapMemory() {
            return versionMapMemory;
        }

        @JsonProperty(ShardStatsValue.Constants.BITSET_MEMORY_VALUE)
        public long getBitsetMemory() {
            return bitsetMemory;
        }

        @JsonProperty(ShardStatsValue.Constants.SHARD_SIZE_IN_BYTES_VALUE)
        public long getShardSizeInBytes() {
            return shardSizeInBytes;
        }
    }
}
