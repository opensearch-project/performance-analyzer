/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics.addMetricEntry;
import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode.FAULT_DETECTION_COLLECTOR_ERROR;
import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatMetrics.FAULT_DETECTION_COLLECTOR_EXECUTION_TIME;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jooq.tools.StringUtils;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.collectors.StatsCollector;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;

public class FaultDetectionMetricsCollector extends PerformanceAnalyzerMetricsCollector
        implements MetricsProcessor {
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(FaultDetectionMetricsCollector.class)
                    .samplingInterval;
    private static final int KEYS_PATH_LENGTH = 3;
    private static final Logger LOG = LogManager.getLogger(FaultDetectionMetricsCollector.class);
    private static final String FAULT_DETECTION_HANDLER_NAME =
            "org.opensearch.performanceanalyzer.handler.ClusterFaultDetectionStatsHandler";
    private static final String FAULT_DETECTION_HANDLER_METRIC_QUEUE = "metricQueue";
    private final ConfigOverridesWrapper configOverridesWrapper;
    private final PerformanceAnalyzerController controller;
    private StringBuilder value;
    private static final ObjectMapper mapper = new ObjectMapper();

    public FaultDetectionMetricsCollector(
            PerformanceAnalyzerController controller,
            ConfigOverridesWrapper configOverridesWrapper) {
        super(
                SAMPLING_TIME_INTERVAL,
                "FaultDetectionMetricsCollector",
                FAULT_DETECTION_COLLECTOR_EXECUTION_TIME,
                FAULT_DETECTION_COLLECTOR_ERROR);
        value = new StringBuilder();
        this.configOverridesWrapper = configOverridesWrapper;
        this.controller = controller;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void collectMetrics(long startTime) {
        if (!controller.isCollectorEnabled(configOverridesWrapper, getCollectorName())) {
            return;
        }

        Class<?> faultDetectionHandler;
        try {
            faultDetectionHandler = Class.forName(FAULT_DETECTION_HANDLER_NAME);
        } catch (ClassNotFoundException e) {
            LOG.debug(
                    "[ {} ] No Handler Detected for Fault Detection. Skipping!",
                    this::getCollectorName);
            StatsCollector.instance().logException(FAULT_DETECTION_COLLECTOR_ERROR);
            return;
        }

        BlockingQueue<String> metricQueue;
        List<ClusterFaultDetectionContext> faultDetectionContextsList;
        try {
            metricQueue =
                    (BlockingQueue<String>)
                            getFaultDetectionHandlerMetricsQueue(faultDetectionHandler).get(null);
            List<String> metrics = new ArrayList<>();
            metricQueue.drainTo(metrics);

            faultDetectionContextsList = new ArrayList<>();
            for (String metric : metrics) {
                faultDetectionContextsList.add(
                        mapper.readValue(metric, ClusterFaultDetectionContext.class));
            }
        } catch (IllegalAccessException | NoSuchFieldException | JsonProcessingException e) {
            LOG.debug(
                    "[ {} ] Exception in getting fields for Fault Detection Metrics: {}",
                    this::getCollectorName,
                    e::getMessage);
            StatsCollector.instance().logException(FAULT_DETECTION_COLLECTOR_ERROR);
            return;
        }

        for (ClusterFaultDetectionContext clusterFaultDetectionContext :
                faultDetectionContextsList) {
            value.setLength(0);
            value.append(PerformanceAnalyzerMetrics.getCurrentTimeMetric());
            addMetricEntry(
                    value,
                    AllMetrics.FaultDetectionDimension.SOURCE_NODE_ID.toString(),
                    clusterFaultDetectionContext.getSourceNodeId());
            addMetricEntry(
                    value,
                    AllMetrics.FaultDetectionDimension.TARGET_NODE_ID.toString(),
                    clusterFaultDetectionContext.getTargetNodeId());

            if (StringUtils.isEmpty(clusterFaultDetectionContext.getStartTime())) {
                addMetricEntry(
                        value,
                        AllMetrics.CommonMetric.FINISH_TIME.toString(),
                        clusterFaultDetectionContext.getFinishTime());
                addMetricEntry(
                        value,
                        PerformanceAnalyzerMetrics.FAULT,
                        clusterFaultDetectionContext.getFault());
                saveMetricValues(
                        value.toString(),
                        startTime,
                        clusterFaultDetectionContext.getType(),
                        clusterFaultDetectionContext.getRequestId(),
                        PerformanceAnalyzerMetrics.FINISH_FILE_NAME);
            } else {
                addMetricEntry(
                        value,
                        AllMetrics.CommonMetric.START_TIME.toString(),
                        clusterFaultDetectionContext.getStartTime());
                saveMetricValues(
                        value.toString(),
                        startTime,
                        clusterFaultDetectionContext.getType(),
                        clusterFaultDetectionContext.getRequestId(),
                        PerformanceAnalyzerMetrics.START_FILE_NAME);
            }
        }
    }

    Field getFaultDetectionHandlerMetricsQueue(Class<?> faultDetectionHandler)
            throws NoSuchFieldException {
        Field metricsQueue =
                faultDetectionHandler.getDeclaredField(FAULT_DETECTION_HANDLER_METRIC_QUEUE);
        metricsQueue.setAccessible(true);
        return metricsQueue;
    }

    /**
     * Sample Event ^fault_detection/follower_check/7627/finish current_time:1601486201861
     * SourceNodeID:g52i9a93a762cd59dda8d3379b09a752a TargetNodeID:b2a5a93a762cd59dda8d3379b09a752a
     * FinishTime:1566413987986 fault:0$
     *
     * @param startTime time at which collector is called
     * @param keysPath List of string that would make up the metrics path
     * @return metric path
     */
    @Override
    public String getMetricsPath(long startTime, String... keysPath) {
        if (keysPath.length != KEYS_PATH_LENGTH) {
            throw new RuntimeException("keys length should be " + KEYS_PATH_LENGTH);
        }

        return PerformanceAnalyzerMetrics.generatePath(
                startTime,
                PerformanceAnalyzerMetrics.sFaultDetection,
                keysPath[0],
                keysPath[1],
                keysPath[2]);
    }

    public static class ClusterFaultDetectionContext {
        String type;
        String sourceNodeId;
        String targetNodeId;
        String requestId;
        String fault;
        String startTime;
        String finishTime;

        public String getType() {
            return this.type;
        }

        public String getSourceNodeId() {
            return this.sourceNodeId;
        }

        public String getTargetNodeId() {
            return this.targetNodeId;
        }

        public String getFault() {
            return this.fault;
        }

        public String getStartTime() {
            return this.startTime;
        }

        public String getFinishTime() {
            return this.finishTime;
        }

        public String getRequestId() {
            return this.requestId;
        }
    }
}
