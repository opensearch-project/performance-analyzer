/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode.CIRCUIT_BREAKER_COLLECTOR_ERROR;
import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatMetrics.CIRCUIT_BREAKER_COLLECTOR_EXECUTION_TIME;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.core.indices.breaker.CircuitBreakerStats;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.collectors.MetricStatus;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.CircuitBreakerDimension;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.CircuitBreakerValue;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;

public class CircuitBreakerCollector extends PerformanceAnalyzerMetricsCollector
        implements MetricsProcessor {
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(CircuitBreakerCollector.class).samplingInterval;

    private static final Logger LOG = LogManager.getLogger(CircuitBreakerCollector.class);
    private static final int KEYS_PATH_LENGTH = 0;
    private StringBuilder value;
    private PerformanceAnalyzerController performanceAnalyzerController;
    private ConfigOverridesWrapper configOverridesWrapper;

    public CircuitBreakerCollector(
            PerformanceAnalyzerController performanceAnalyzerController,
            ConfigOverridesWrapper configOverridesWrapper) {
        super(
                SAMPLING_TIME_INTERVAL,
                "CircuitBreaker",
                CIRCUIT_BREAKER_COLLECTOR_EXECUTION_TIME,
                CIRCUIT_BREAKER_COLLECTOR_ERROR);
        value = new StringBuilder();
        this.performanceAnalyzerController = performanceAnalyzerController;
        this.configOverridesWrapper = configOverridesWrapper;
    }

    @Override
    public void collectMetrics(long startTime) {
        if (!performanceAnalyzerController.rcaCollectorsEnabled()) {
            LOG.info("All RCA collectors are disabled. Skipping collection.");
            return;
        }

        if (performanceAnalyzerController.isCollectorDisabled(
                configOverridesWrapper, getCollectorName())) {
            LOG.info(getCollectorName() + " is disabled. Skipping collection.");
            return;
        }

        if (OpenSearchResources.INSTANCE.getCircuitBreakerService() == null) {
            return;
        }

        CircuitBreakerStats[] allCircuitBreakerStats =
                OpenSearchResources.INSTANCE.getCircuitBreakerService().stats().getAllStats();
        // - Reusing the same StringBuilder across exectuions; so clearing before using
        value.setLength(0);
        value.append(PerformanceAnalyzerMetrics.getJsonCurrentMilliSeconds());

        for (CircuitBreakerStats stats : allCircuitBreakerStats) {
            value.append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                    .append(
                            new CircuitBreakerStatus(
                                            stats.getName(),
                                            stats.getEstimated(),
                                            stats.getTrippedCount(),
                                            stats.getLimit())
                                    .serialize());
        }

        saveMetricValues(value.toString(), startTime);
    }

    @Override
    public String getMetricsPath(long startTime, String... keysPath) {
        // throw exception if keys.length is not equal to 0
        if (keysPath.length != KEYS_PATH_LENGTH) {
            throw new RuntimeException("keys length should be " + KEYS_PATH_LENGTH);
        }

        return PerformanceAnalyzerMetrics.generatePath(
                startTime, PerformanceAnalyzerMetrics.sCircuitBreakerPath);
    }

    static class CircuitBreakerStatus extends MetricStatus {
        private final String type;

        private final long estimated;

        private final long tripped;

        private final long limitConfigured;

        CircuitBreakerStatus(String type, long estimated, long tripped, long limitConfigured) {
            this.type = type;
            this.estimated = estimated;
            this.tripped = tripped;
            this.limitConfigured = limitConfigured;
        }

        @JsonProperty(CircuitBreakerDimension.Constants.TYPE_VALUE)
        public String getType() {
            return type;
        }

        @JsonProperty(CircuitBreakerValue.Constants.ESTIMATED_VALUE)
        public long getEstimated() {
            return estimated;
        }

        @JsonProperty(CircuitBreakerValue.Constants.TRIPPED_VALUE)
        public long getTripped() {
            return tripped;
        }

        @JsonProperty(CircuitBreakerValue.Constants.LIMIT_CONFIGURED_VALUE)
        public long getLimitConfigured() {
            return limitConfigured;
        }
    }
}
