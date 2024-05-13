/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.collectors.StatsCollector;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode;
import org.opensearch.performanceanalyzer.commons.stats.metrics.StatMetrics;
import org.opensearch.telemetry.metrics.Counter;
import org.opensearch.telemetry.metrics.MetricsRegistry;

public class RTFGCInfoCollector extends PerformanceAnalyzerMetricsCollector {
    // ToDo: Use separate configs for each collector
    private static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(StatsCollector.class).samplingInterval;

    private static final Logger LOG = LogManager.getLogger(RTFHeapMetricsCollector.class);
    private MetricsRegistry metricsRegistry;

    private Counter counter;

    public RTFGCInfoCollector() {
        super(
                SAMPLING_TIME_INTERVAL,
                "RTFGCInfo",
                StatMetrics.GC_INFO_COLLECTOR_EXECUTION_TIME,
                StatExceptionCode.GC_INFO_COLLECTOR_ERROR);
    }

    @Override
    public void collectMetrics(long startTime) {
        metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        if (metricsRegistry == null) {
            LOG.error("could not get the instance of MetricsRegistry class");
            return;
        }

        initialiseMetrics();
        LOG.info("Executing collect metrics for RTFGCInfoCollector");
        recordMetrics();
    }

    private void initialiseMetrics() {}

    private void recordMetrics() {}
}
