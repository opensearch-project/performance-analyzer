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
import org.opensearch.performanceanalyzer.config.overrides.ConfigOverridesHelper;
import org.opensearch.performanceanalyzer.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.NodeDetailColumns;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.NodeRole;
import org.opensearch.performanceanalyzer.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.metrics.PerformanceAnalyzerMetrics;

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
        try {
            if (OpenSearchResources.INSTANCE.getClusterService() == null
                    || OpenSearchResources.INSTANCE.getClusterService().state() == null
                    || OpenSearchResources.INSTANCE.getClusterService().state().nodes() == null) {
                return;
            }
        } catch (Exception e) {
            LOG.error("Unable to get cluster stats");
            return;
        }

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

        DiscoveryNode masterNode = discoveryNodes.getMasterNode();

        Iterator<DiscoveryNode> discoveryNodeIterator = discoveryNodes.iterator();
        addMetricsToStringBuilder(discoveryNodes.getLocalNode(), value, "", masterNode);
        String localNodeID = discoveryNodes.getLocalNode().getId();

        while (discoveryNodeIterator.hasNext()) {
            addMetricsToStringBuilder(discoveryNodeIterator.next(), value, localNodeID, masterNode);
        }
        saveMetricValues(value.toString(), startTime);
    }

    private void addMetricsToStringBuilder(
            DiscoveryNode discoveryNode,
            StringBuilder value,
            String localNodeID,
            DiscoveryNode masterNode) {
        if (!discoveryNode.getId().equals(localNodeID)) {
            boolean isMasterNode = discoveryNode.equals(masterNode);
            value.append(
                            new NodeDetailsStatus(
                                            discoveryNode.getId(),
                                            discoveryNode.getHostAddress(),
                                            getNodeRole(discoveryNode),
                                            isMasterNode)
                                    .serialize())
                    .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor);
        }
    }

    private String getNodeRole(final DiscoveryNode node) {
        final NodeRole role =
                node.isDataNode()
                        ? NodeRole.DATA
                        : node.isMasterNode() ? NodeRole.MASTER : NodeRole.UNKNOWN;
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

        private boolean isMasterNode;

        public NodeDetailsStatus(String id, String hostAddress, String role, boolean isMasterNode) {
            super();
            this.id = id;
            this.hostAddress = hostAddress;
            this.role = role;
            this.isMasterNode = isMasterNode;
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

        @JsonProperty(NodeDetailColumns.Constants.IS_MASTER_NODE)
        public boolean getIsMasterNode() {
            return isMasterNode;
        }
    }
}
