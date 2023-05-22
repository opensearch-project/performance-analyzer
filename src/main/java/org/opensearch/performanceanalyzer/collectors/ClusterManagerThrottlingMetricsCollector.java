/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;


import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.Method;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.cluster.service.MasterService;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.PerformanceAnalyzerApp;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.rca.framework.metrics.ExceptionsAndErrors;
import org.opensearch.performanceanalyzer.rca.framework.metrics.WriterMetrics;

public class ClusterManagerThrottlingMetricsCollector extends PerformanceAnalyzerMetricsCollector
        implements MetricsProcessor {

    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(ClusterManagerThrottlingMetricsCollector.class)
                    .samplingInterval;
    private static final Logger LOG =
            LogManager.getLogger(ClusterManagerThrottlingMetricsCollector.class);
    private static final int KEYS_PATH_LENGTH = 0;
    private static final String CLUSTER_MANAGER_THROTTLING_RETRY_LISTENER_PATH =
            "org.opensearch.action.support.master.MasterThrottlingRetryListener";
    private static final String THROTTLED_PENDING_TASK_COUNT_METHOD_NAME =
            "numberOfThrottledPendingTasks";
    private static final String RETRYING_TASK_COUNT_METHOD_NAME = "getRetryingTasksCount";
    private final StringBuilder value;
    private final PerformanceAnalyzerController controller;
    private final ConfigOverridesWrapper configOverridesWrapper;

    public ClusterManagerThrottlingMetricsCollector(
            PerformanceAnalyzerController controller,
            ConfigOverridesWrapper configOverridesWrapper) {
        super(SAMPLING_TIME_INTERVAL, "ClusterManagerThrottlingMetricsCollector");
        value = new StringBuilder();
        this.controller = controller;
        this.configOverridesWrapper = configOverridesWrapper;
    }

    @Override
    public void collectMetrics(long startTime) {
        if (!controller.isCollectorEnabled(configOverridesWrapper, getCollectorName())) {
            return;
        }
        try {
            long mCurrT = System.currentTimeMillis();
            if (OpenSearchResources.INSTANCE.getClusterService() == null
                    || OpenSearchResources.INSTANCE.getClusterService().getMasterService()
                            == null) {
                return;
            }
            if (!isClusterManagerThrottlingFeatureAvailable()) {
                LOG.debug("ClusterManager Throttling Feature is not available for this domain");
                PerformanceAnalyzerApp.WRITER_METRICS_AGGREGATOR.updateStat(
                        WriterMetrics.CLUSTER_MANAGER_THROTTLING_COLLECTOR_NOT_AVAILABLE, "", 1);
                return;
            }

            value.setLength(0);
            value.append(PerformanceAnalyzerMetrics.getJsonCurrentMilliSeconds())
                    .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor);
            value.append(
                    new ClusterManagerThrottlingMetrics(
                                    getRetryingPendingTaskCount(),
                                    getTotalClusterManagerThrottledTaskCount())
                            .serialize());

            saveMetricValues(value.toString(), startTime);

            PerformanceAnalyzerApp.WRITER_METRICS_AGGREGATOR.updateStat(
                    WriterMetrics.CLUSTER_MANAGER_THROTTLING_COLLECTOR_EXECUTION_TIME,
                    "",
                    System.currentTimeMillis() - mCurrT);

        } catch (Exception ex) {
            LOG.debug(
                    "Exception in Collecting ClusterManager Throttling Metrics: {} for startTime {}",
                    () -> ex.toString(),
                    () -> startTime);
            PerformanceAnalyzerApp.ERRORS_AND_EXCEPTIONS_AGGREGATOR.updateStat(
                    ExceptionsAndErrors.CLUSTER_MANAGER_THROTTLING_COLLECTOR_ERROR, "", 1);
        }
    }

    private boolean isClusterManagerThrottlingFeatureAvailable() {
        try {
            Class.forName(CLUSTER_MANAGER_THROTTLING_RETRY_LISTENER_PATH);
            MasterService.class.getMethod(THROTTLED_PENDING_TASK_COUNT_METHOD_NAME);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return false;
        }
        return true;
    }

    private long getTotalClusterManagerThrottledTaskCount() throws Exception {
        Method method = MasterService.class.getMethod(THROTTLED_PENDING_TASK_COUNT_METHOD_NAME);
        return (long)
                method.invoke(OpenSearchResources.INSTANCE.getClusterService().getMasterService());
    }

    private long getRetryingPendingTaskCount() throws Exception {
        Method method =
                Class.forName(CLUSTER_MANAGER_THROTTLING_RETRY_LISTENER_PATH)
                        .getMethod(RETRYING_TASK_COUNT_METHOD_NAME);
        return (long) method.invoke(null);
    }

    @Override
    public String getMetricsPath(long startTime, String... keysPath) {
        if (keysPath.length != KEYS_PATH_LENGTH) {
            throw new RuntimeException("keys length should be " + KEYS_PATH_LENGTH);
        }

        return PerformanceAnalyzerMetrics.generatePath(
                startTime, PerformanceAnalyzerMetrics.sClusterManagerThrottledTasksPath);
    }

    public static class ClusterManagerThrottlingMetrics extends MetricStatus {
        private final long retryingTaskCount;
        private final long throttledPendingTasksCount;

        public ClusterManagerThrottlingMetrics(
                long retryingTaskCount, long throttledPendingTasksCount) {
            this.retryingTaskCount = retryingTaskCount;
            this.throttledPendingTasksCount = throttledPendingTasksCount;
        }

        @JsonProperty(AllMetrics.ClusterManagerThrottlingValue.Constants.RETRYING_TASK_COUNT)
        public long getRetryingTaskCount() {
            return retryingTaskCount;
        }

        @JsonProperty(
                AllMetrics.ClusterManagerThrottlingValue.Constants.THROTTLED_PENDING_TASK_COUNT)
        public long getThrottledPendingTasksCount() {
            return throttledPendingTasksCount;
        }
    }
}
