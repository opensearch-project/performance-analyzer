/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;


import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.ExceptionsAndErrors;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.WriterMetrics;
import org.opensearch.performanceanalyzer.commons.stats.CommonStats;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.config.overrides.ConfigOverridesWrapper;

/**
 * This class starts publishing election term metric. These metric is emitted from cluster state.
 */
public class ElectionTermCollector extends PerformanceAnalyzerMetricsCollector
        implements MetricsProcessor {
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(ElectionTermCollector.class).samplingInterval;
    private static final Logger LOG = LogManager.getLogger(ElectionTermCollector.class);
    private static final int KEYS_PATH_LENGTH = 0;
    private final ConfigOverridesWrapper configOverridesWrapper;
    private final PerformanceAnalyzerController controller;
    private StringBuilder value;

    public ElectionTermCollector(
            PerformanceAnalyzerController controller,
            ConfigOverridesWrapper configOverridesWrapper) {
        super(SAMPLING_TIME_INTERVAL, "ElectionTermCollector");
        value = new StringBuilder();
        this.controller = controller;
        this.configOverridesWrapper = configOverridesWrapper;
    }

    @Override
    public String getMetricsPath(long startTime, String... keysPath) {
        // throw exception if keysPath.length is not equal to 0
        if (keysPath.length != KEYS_PATH_LENGTH) {
            throw new RuntimeException("keys length should be " + KEYS_PATH_LENGTH);
        }

        return PerformanceAnalyzerMetrics.generatePath(
                startTime, PerformanceAnalyzerMetrics.sElectionTermPath);
    }

    @Override
    public void collectMetrics(long startTime) {
        if (!controller.isCollectorEnabled(configOverridesWrapper, getCollectorName())) {
            return;
        }
        long mCurrT = System.currentTimeMillis();
        try {
            if (OpenSearchResources.INSTANCE.getClusterService() == null
                    || OpenSearchResources.INSTANCE.getClusterService().state() == null) {
                return;
            }

            value.setLength(0);
            value.append(PerformanceAnalyzerMetrics.getJsonCurrentMilliSeconds())
                    .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor);
            value.append(
                    new ElectionTermMetrics(
                                    OpenSearchResources.INSTANCE.getClusterService().state().term())
                            .serialize());
            saveMetricValues(value.toString(), startTime);

            CommonStats.WRITER_METRICS_AGGREGATOR.updateStat(
                    WriterMetrics.ELECTION_TERM_COLLECTOR_EXECUTION_TIME,
                    "",
                    System.currentTimeMillis() - mCurrT);

        } catch (Exception ex) {
            LOG.debug(
                    "Exception in Collecting Election term Metrics: {} for startTime {}",
                    () -> ex.toString(),
                    () -> startTime);
            CommonStats.WRITER_METRICS_AGGREGATOR.updateStat(
                    ExceptionsAndErrors.ELECTION_TERM_COLLECTOR_ERROR, "", 1);
        }
    }

    public static class ElectionTermMetrics extends MetricStatus {
        private final long electionTerm;

        public ElectionTermMetrics(long electionTerm) {
            this.electionTerm = electionTerm;
        }

        @JsonProperty(AllMetrics.ElectionTermValue.Constants.ELECTION_TERM_VALUE)
        public long getElectionTerm() {
            return electionTerm;
        }
    }
}
