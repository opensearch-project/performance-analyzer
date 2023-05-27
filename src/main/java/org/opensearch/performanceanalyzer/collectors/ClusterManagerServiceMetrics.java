/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode.CLUSTER_MANAGER_METRICS_ERROR;
import static org.opensearch.performanceanalyzer.stats.PACollectorMetrics.CLUSTER_MANAGER_SERVICE_METRICS_COLLECTOR_EXECUTION_TIME;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.cluster.service.PendingClusterTask;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.collectors.MetricStatus;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.ClusterManagerPendingTaskDimension;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.ClusterManagerPendingValue;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;

@SuppressWarnings("unchecked")
public class ClusterManagerServiceMetrics extends PerformanceAnalyzerMetricsCollector
        implements MetricsProcessor {
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(ClusterManagerServiceMetrics.class)
                    .samplingInterval;
    private static final Logger LOG = LogManager.getLogger(ClusterManagerServiceMetrics.class);
    private static final int KEYS_PATH_LENGTH = 2;
    private StringBuilder value;

    public ClusterManagerServiceMetrics() {
        super(
                SAMPLING_TIME_INTERVAL,
                "ClusterManagerServiceMetrics",
                CLUSTER_MANAGER_SERVICE_METRICS_COLLECTOR_EXECUTION_TIME,
                CLUSTER_MANAGER_METRICS_ERROR);
        value = new StringBuilder();
    }

    @Override
    public String getMetricsPath(long startTime, String... keysPath) {
        // throw exception if keysPath.length is not equal to 2
        // (Keys should be Pending_Task_ID, start/finish OR current, metadata)
        if (keysPath.length != KEYS_PATH_LENGTH) {
            throw new RuntimeException("keys length should be " + KEYS_PATH_LENGTH);
        }

        return PerformanceAnalyzerMetrics.generatePath(
                startTime, PerformanceAnalyzerMetrics.sPendingTasksPath, keysPath[0], keysPath[1]);
    }

    @Override
    public void collectMetrics(long startTime) {
        if (Objects.isNull(OpenSearchResources.INSTANCE.getClusterService())
                || Objects.isNull(
                        OpenSearchResources.INSTANCE.getClusterService().getMasterService())) {
            return;
        }

        /*
         * pendingTasks API returns object of PendingClusterTask which contains insertOrder, priority, source, timeInQueue.
         * Example :
         *      insertOrder: 101,
         *      priority: "URGENT",
         *      source: "create-index [foo_9], cause [api]",
         *      timeIn_queue: "86ms"
         */
        List<PendingClusterTask> pendingTasks =
                OpenSearchResources.INSTANCE.getClusterService().getMasterService().pendingTasks();
        HashMap<String, Integer> pendingTaskCountPerTaskType = new HashMap<>();

        pendingTasks.stream()
                .forEach(
                        pendingTask -> {
                            String pendingTaskType =
                                    pendingTask.getSource().toString().split(" ", 2)[0];
                            pendingTaskCountPerTaskType.put(
                                    pendingTaskType,
                                    pendingTaskCountPerTaskType.getOrDefault(pendingTaskType, 0)
                                            + 1);
                        });

        value.setLength(0);
        value.append(PerformanceAnalyzerMetrics.getJsonCurrentMilliSeconds());
        pendingTaskCountPerTaskType.forEach(
                (pendingTaskType, PendingTaskValue) -> {
                    value.append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor);
                    value.append(
                            new ClusterManagerPendingStatus(pendingTaskType, PendingTaskValue)
                                    .serialize());
                });
        saveMetricValues(
                value.toString(),
                startTime,
                PerformanceAnalyzerMetrics.CLUSTER_MANAGER_CURRENT,
                PerformanceAnalyzerMetrics.CLUSTER_MANAGER_META_DATA);
    }

    public static class ClusterManagerPendingStatus extends MetricStatus {
        private final String pendingTaskType;
        private final int pendingTasksCount;

        public ClusterManagerPendingStatus(String pendingTaskType, int pendingTasksCount) {
            this.pendingTaskType = pendingTaskType;
            this.pendingTasksCount = pendingTasksCount;
        }

        @JsonProperty(ClusterManagerPendingTaskDimension.Constants.PENDING_TASK_TYPE)
        public String getClusterManagerTaskType() {
            return pendingTaskType;
        }

        @JsonProperty(ClusterManagerPendingValue.Constants.PENDING_TASKS_COUNT_VALUE)
        public int getPendingTasksCount() {
            return pendingTasksCount;
        }
    }
}
