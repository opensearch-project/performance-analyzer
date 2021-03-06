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
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.rca.framework.metrics.ExceptionsAndErrors;
import org.opensearch.performanceanalyzer.rca.framework.metrics.WriterMetrics;

public class MasterThrottlingMetricsCollector extends PerformanceAnalyzerMetricsCollector
        implements MetricsProcessor {

    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(MasterThrottlingMetricsCollector.class)
                    .samplingInterval;
    private static final Logger LOG = LogManager.getLogger(MasterThrottlingMetricsCollector.class);
    private static final int KEYS_PATH_LENGTH = 0;
    private static final String MASTER_THROTTLING_RETRY_LISTENER_PATH =
            "org.opensearch.action.support.master.MasterThrottlingRetryListener";
    private static final String THROTTLED_PENDING_TASK_COUNT_METHOD_NAME =
            "numberOfThrottledPendingTasks";
    private static final String RETRYING_TASK_COUNT_METHOD_NAME = "getRetryingTasksCount";
    private final StringBuilder value;
    private final PerformanceAnalyzerController controller;
    private final ConfigOverridesWrapper configOverridesWrapper;

    public MasterThrottlingMetricsCollector(
            PerformanceAnalyzerController controller,
            ConfigOverridesWrapper configOverridesWrapper) {
        super(SAMPLING_TIME_INTERVAL, "MasterThrottlingMetricsCollector");
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
            if (!isMasterThrottlingFeatureAvailable()) {
                LOG.debug("Master Throttling Feature is not available for this domain");
                PerformanceAnalyzerApp.WRITER_METRICS_AGGREGATOR.updateStat(
                        WriterMetrics.MASTER_THROTTLING_COLLECTOR_NOT_AVAILABLE, "", 1);
                return;
            }

            value.setLength(0);
            value.append(PerformanceAnalyzerMetrics.getJsonCurrentMilliSeconds())
                    .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor);
            value.append(
                    new MasterThrottlingMetrics(
                                    getRetryingPendingTaskCount(),
                                    getTotalMasterThrottledTaskCount())
                            .serialize());

            saveMetricValues(value.toString(), startTime);

            PerformanceAnalyzerApp.WRITER_METRICS_AGGREGATOR.updateStat(
                    WriterMetrics.MASTER_THROTTLING_COLLECTOR_EXECUTION_TIME,
                    "",
                    System.currentTimeMillis() - mCurrT);

        } catch (Exception ex) {
            PerformanceAnalyzerApp.ERRORS_AND_EXCEPTIONS_AGGREGATOR.updateStat(
                    ExceptionsAndErrors.MASTER_THROTTLING_COLLECTOR_ERROR, "", 1);
            LOG.debug(
                    "Exception in Collecting Master Throttling Metrics: {} for startTime {}",
                    () -> ex.toString(),
                    () -> startTime);
        }
    }

    private boolean isMasterThrottlingFeatureAvailable() {
        try {
            Class.forName(MASTER_THROTTLING_RETRY_LISTENER_PATH);
            MasterService.class.getMethod(THROTTLED_PENDING_TASK_COUNT_METHOD_NAME);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return false;
        }
        return true;
    }

    private long getTotalMasterThrottledTaskCount() throws Exception {
        Method method = MasterService.class.getMethod(THROTTLED_PENDING_TASK_COUNT_METHOD_NAME);
        return (long)
                method.invoke(OpenSearchResources.INSTANCE.getClusterService().getMasterService());
    }

    private long getRetryingPendingTaskCount() throws Exception {
        Method method =
                Class.forName(MASTER_THROTTLING_RETRY_LISTENER_PATH)
                        .getMethod(RETRYING_TASK_COUNT_METHOD_NAME);
        return (long) method.invoke(null);
    }

    @Override
    public String getMetricsPath(long startTime, String... keysPath) {
        if (keysPath.length != KEYS_PATH_LENGTH) {
            throw new RuntimeException("keys length should be " + KEYS_PATH_LENGTH);
        }

        return PerformanceAnalyzerMetrics.generatePath(
                startTime, PerformanceAnalyzerMetrics.sMasterThrottledTasksPath);
    }

    public static class MasterThrottlingMetrics extends MetricStatus {
        private final long retryingTaskCount;
        private final long throttledPendingTasksCount;

        public MasterThrottlingMetrics(long retryingTaskCount, long throttledPendingTasksCount) {
            this.retryingTaskCount = retryingTaskCount;
            this.throttledPendingTasksCount = throttledPendingTasksCount;
        }

        @JsonProperty(AllMetrics.MasterThrottlingValue.Constants.RETRYING_TASK_COUNT)
        public long getRetryingTaskCount() {
            return retryingTaskCount;
        }

        @JsonProperty(AllMetrics.MasterThrottlingValue.Constants.THROTTLED_PENDING_TASK_COUNT)
        public long getThrottledPendingTasksCount() {
            return throttledPendingTasksCount;
        }
    }
}
