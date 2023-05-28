/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.ShardType.SHARD_PRIMARY;
import static org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.ShardType.SHARD_REPLICA;
import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode.SHARD_STATE_COLLECTOR_ERROR;
import static org.opensearch.performanceanalyzer.stats.PACollectorMetrics.SHARD_STATE_COLLECTOR_EXECUTION_TIME;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.tools.StringUtils;
import org.jooq.tools.json.JSONObject;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.routing.RoutingTable;
import org.opensearch.cluster.routing.ShardRouting;
import org.opensearch.cluster.routing.ShardRoutingState;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.collectors.MetricStatus;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;

public class ShardStateCollector extends PerformanceAnalyzerMetricsCollector
        implements MetricsProcessor {
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(ShardStateCollector.class).samplingInterval;
    private static final Logger LOG = LogManager.getLogger(ShardStateCollector.class);
    private static final int KEYS_PATH_LENGTH = 0;
    private final ConfigOverridesWrapper configOverridesWrapper;
    private final PerformanceAnalyzerController controller;
    private StringBuilder value;

    public ShardStateCollector(
            PerformanceAnalyzerController controller,
            ConfigOverridesWrapper configOverridesWrapper) {
        super(
                SAMPLING_TIME_INTERVAL,
                "ShardsStateCollector",
                SHARD_STATE_COLLECTOR_EXECUTION_TIME,
                SHARD_STATE_COLLECTOR_ERROR);
        value = new StringBuilder();
        this.controller = controller;
        this.configOverridesWrapper = configOverridesWrapper;
    }

    @Override
    public void collectMetrics(long startTime) {
        if (!controller.isCollectorEnabled(configOverridesWrapper, getCollectorName())) {
            return;
        }
        if (OpenSearchResources.INSTANCE.getClusterService() == null) {
            return;
        }

        ClusterState clusterState = OpenSearchResources.INSTANCE.getClusterService().state();
        boolean inActiveShard = false;
        value.setLength(0);
        value.append(PerformanceAnalyzerMetrics.getJsonCurrentMilliSeconds())
                .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor);
        RoutingTable routingTable = clusterState.routingTable();
        String[] indices = routingTable.indicesRouting().keySet().toArray(new String[0]);
        for (String index : indices) {
            List<ShardRouting> allShardsIndex = routingTable.allShards(index);
            value.append(
                    createJsonObject(AllMetrics.ShardStateDimension.INDEX_NAME.toString(), index));
            for (ShardRouting shard : allShardsIndex) {
                String nodeName = StringUtils.EMPTY;
                if (shard.assignedToNode()) {
                    nodeName = clusterState.nodes().get(shard.currentNodeId()).getName();
                }
                if (shard.state() != ShardRoutingState.STARTED) {
                    inActiveShard = true;
                    value.append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                            .append(
                                    new ShardStateMetrics(
                                                    shard.getId(),
                                                    shard.primary()
                                                            ? SHARD_PRIMARY.toString()
                                                            : SHARD_REPLICA.toString(),
                                                    nodeName,
                                                    shard.state().name())
                                            .serialize());
                }
            }
        }
        value.append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor);
        if (inActiveShard) {
            saveMetricValues(value.toString(), startTime);
        }
    }

    @SuppressWarnings("unchecked")
    private String createJsonObject(String key, String value) {
        JSONObject json = new JSONObject();
        json.put(key, value);
        return json.toString();
    }

    @Override
    public String getMetricsPath(long startTime, String... keysPath) {
        if (keysPath.length != KEYS_PATH_LENGTH) {
            throw new RuntimeException("keys length should be " + KEYS_PATH_LENGTH);
        }

        return PerformanceAnalyzerMetrics.generatePath(
                startTime, PerformanceAnalyzerMetrics.sShardStatePath);
    }

    public static class ShardStateMetrics extends MetricStatus {

        private final int shardId;
        private final String shardType;
        private final String nodeName;
        private final String shardState;

        public ShardStateMetrics(
                int shardId, String shardType, String nodeName, String shardState) {
            this.shardId = shardId;
            this.shardType = shardType;
            this.nodeName = nodeName;
            this.shardState = shardState;
        }

        @JsonProperty(AllMetrics.CommonDimension.Constants.SHARDID_VALUE)
        public int getShardId() {
            return shardId;
        }

        @JsonProperty(AllMetrics.ShardStateDimension.Constants.SHARD_TYPE)
        public String getShardType() {
            return shardType;
        }

        @JsonProperty(AllMetrics.ShardStateDimension.Constants.NODE_NAME)
        public String getNodeName() {
            return nodeName;
        }

        @JsonProperty(AllMetrics.ShardStateValue.Constants.SHARD_STATE)
        public String getShardState() {
            return shardState;
        }
    }
}
