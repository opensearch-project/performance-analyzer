/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.HashMap;
import java.util.List;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.cluster.service.PendingClusterTask;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.MasterPendingTaskDimension;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.MasterPendingValue;
import org.opensearch.performanceanalyzer.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.metrics.PerformanceAnalyzerMetrics;

@SuppressWarnings("unchecked")
public class MasterServiceMetrics extends PerformanceAnalyzerMetricsCollector
        implements MetricsProcessor {
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(MasterServiceMetrics.class).samplingInterval;
    private static final Logger LOG = LogManager.getLogger(MasterServiceMetrics.class);
    private static final int KEYS_PATH_LENGTH = 2;
    private StringBuilder value;

    public MasterServiceMetrics() {
        super(SAMPLING_TIME_INTERVAL, "MasterServiceMetrics");
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
        try {
            if (OpenSearchResources.INSTANCE.getClusterService() == null
                    || OpenSearchResources.INSTANCE.getClusterService().getMasterService()
                            == null) {
                return;
            }

            /*
            pendingTasks API returns object of PendingClusterTask which contains insertOrder, priority, source, timeInQueue.
                Example :
                     insertOrder: 101,
                     priority: "URGENT",
                     source: "create-index [foo_9], cause [api]",
                     timeIn_queue: "86ms"
             */

            List<PendingClusterTask> pendingTasks =
                    OpenSearchResources.INSTANCE
                            .getClusterService()
                            .getMasterService()
                            .pendingTasks();
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
                                new MasterPendingStatus(pendingTaskType, PendingTaskValue)
                                        .serialize());
                    });
            saveMetricValues(
                    value.toString(),
                    startTime,
                    PerformanceAnalyzerMetrics.MASTER_CURRENT,
                    PerformanceAnalyzerMetrics.MASTER_META_DATA);
        } catch (Exception ex) {
            LOG.debug(
                    "Exception in Collecting Master Metrics: {} for startTime {}",
                    () -> ex.toString(),
                    () -> startTime);
        }
    }

    public static class MasterPendingStatus extends MetricStatus {
        private final String pendingTaskType;
        private final int pendingTasksCount;

        public MasterPendingStatus(String pendingTaskType, int pendingTasksCount) {
            this.pendingTaskType = pendingTaskType;
            this.pendingTasksCount = pendingTasksCount;
        }

        @JsonProperty(MasterPendingTaskDimension.Constants.PENDING_TASK_TYPE)
        public String getMasterTaskType() {
            return pendingTaskType;
        }

        @JsonProperty(MasterPendingValue.Constants.PENDING_TASKS_COUNT_VALUE)
        public int getPendingTasksCount() {
            return pendingTasksCount;
        }
    }
}
