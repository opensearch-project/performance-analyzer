/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.util.Iterator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.NodeDetailColumns;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.NodeRole;
import org.opensearch.performanceanalyzer.commons.metrics.ExceptionsAndErrors;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.WriterMetrics;
import org.opensearch.performanceanalyzer.commons.stats.CommonStats;
import org.opensearch.performanceanalyzer.config.overrides.ConfigOverridesHelper;
import org.opensearch.performanceanalyzer.config.overrides.ConfigOverridesWrapper;

public class NodeDetailsCollector extends PerformanceAnalyzerMetricsCollector
        implements MetricsProcessor {
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(NodeDetailsCollector.class).samplingInterval;
    private static final Logger LOG = LogManager.getLogger(NodeDetailsCollector.class);
    private static final int KEYS_PATH_LENGTH = 0;
    private final ConfigOverridesWrapper configOverridesWrapper;

    public NodeDetailsCollector(final ConfigOverridesWrapper configOverridesWrapper) {
        super(SAMPLING_TIME_INTERVAL, "NodeDetails");
        this.configOverridesWrapper = configOverridesWrapper;
    }

    @Override
    public void collectMetrics(long startTime) {
        if (OpenSearchResources.INSTANCE.getClusterService() == null
                || OpenSearchResources.INSTANCE.getClusterService().state() == null
                || OpenSearchResources.INSTANCE.getClusterService().state().nodes() == null) {
            return;
        }

        long mCurrT = System.currentTimeMillis();

        StringBuilder value = new StringBuilder();
        value.append(PerformanceAnalyzerMetrics.getJsonCurrentMilliSeconds())
                .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor);

        // We add the config overrides in line#2 because we don't know how many lines
        // follow that belong to actual node details, and the reader also has no way to
        // know this information in advance unless we add the number of nodes as
        // additional metadata in the file.
        try {
            if (configOverridesWrapper != null) {
                String rcaOverrides =
                        ConfigOverridesHelper.serialize(
                                configOverridesWrapper.getCurrentClusterConfigOverrides());
                value.append(rcaOverrides);
            } else {
                LOG.warn("Overrides wrapper is null. Check NodeDetailsCollector instantiation.");
            }
        } catch (IOException ioe) {
            LOG.error("Unable to serialize rca config overrides.", ioe);
            CommonStats.WRITER_METRICS_AGGREGATOR.updateStat(
                    ExceptionsAndErrors.CONFIG_OVERRIDES_SER_FAILED, "", 1);
        }
        value.append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor);

        // line#3 denotes when the timestamp when the config override happened.
        if (configOverridesWrapper != null) {
            value.append(configOverridesWrapper.getLastUpdatedTimestamp());
        } else {
            value.append(0L);
        }
        value.append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor);

        DiscoveryNodes discoveryNodes =
                OpenSearchResources.INSTANCE.getClusterService().state().nodes();

        DiscoveryNode clusterManagerNode = discoveryNodes.getMasterNode();

        Iterator<DiscoveryNode> discoveryNodeIterator = discoveryNodes.iterator();
        addMetricsToStringBuilder(discoveryNodes.getLocalNode(), value, "", clusterManagerNode);
        String localNodeID = discoveryNodes.getLocalNode().getId();

        while (discoveryNodeIterator.hasNext()) {
            addMetricsToStringBuilder(
                    discoveryNodeIterator.next(), value, localNodeID, clusterManagerNode);
        }
        saveMetricValues(value.toString(), startTime);
        CommonStats.WRITER_METRICS_AGGREGATOR.updateStat(
                WriterMetrics.NODE_DETAILS_COLLECTOR_EXECUTION_TIME,
                "",
                System.currentTimeMillis() - mCurrT);
    }

    private void addMetricsToStringBuilder(
            DiscoveryNode discoveryNode,
            StringBuilder value,
            String localNodeID,
            DiscoveryNode clusterManagerNode) {
        if (!discoveryNode.getId().equals(localNodeID)) {
            boolean isClusterManagerNode = discoveryNode.equals(clusterManagerNode);
            value.append(
                            new NodeDetailsStatus(
                                            discoveryNode.getId(),
                                            discoveryNode.getHostAddress(),
                                            getNodeRole(discoveryNode),
                                            isClusterManagerNode)
                                    .serialize())
                    .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor);
        }
    }

    private String getNodeRole(final DiscoveryNode node) {
        final NodeRole role =
                node.isDataNode()
                        ? NodeRole.DATA
                        : node.isMasterNode() ? NodeRole.CLUSTER_MANAGER : NodeRole.UNKNOWN;
        return role.toString();
    }

    @Override
    public String getMetricsPath(long startTime, String... keysPath) {
        // throw exception if keys.length is not equal to 0
        if (keysPath.length != KEYS_PATH_LENGTH) {
            throw new RuntimeException("keys length should be " + KEYS_PATH_LENGTH);
        }

        return PerformanceAnalyzerMetrics.generatePath(
                startTime, PerformanceAnalyzerMetrics.sNodesPath);
    }

    public static class NodeDetailsStatus extends MetricStatus {
        private String id;

        private String hostAddress;

        private String role;

        private boolean isClusterManagerNode;

        public NodeDetailsStatus(
                String id, String hostAddress, String role, boolean isClusterManagerNode) {
            super();
            this.id = id;
            this.hostAddress = hostAddress;
            this.role = role;
            this.isClusterManagerNode = isClusterManagerNode;
        }

        @JsonProperty(NodeDetailColumns.Constants.ID_VALUE)
        public String getID() {
            return id;
        }

        @JsonProperty(NodeDetailColumns.Constants.HOST_ADDRESS_VALUE)
        public String getHostAddress() {
            return hostAddress;
        }

        @JsonProperty(NodeDetailColumns.Constants.ROLE_VALUE)
        public String getRole() {
            return role;
        }

        @JsonProperty(NodeDetailColumns.Constants.IS_CLUSTER_MANAGER_NODE)
        public boolean getIsClusterManagerNode() {
            return isClusterManagerNode;
        }
    }
}
