/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.rtf;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.jvm.GCMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode;
import org.opensearch.performanceanalyzer.commons.stats.metrics.StatMetrics;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;

public class RTFHeapMetricsCollector extends PerformanceAnalyzerMetricsCollector {
    private static final Logger LOG = LogManager.getLogger(RTFHeapMetricsCollector.class);
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(
                            org.opensearch.performanceanalyzer.commons.collectors
                                    .HeapMetricsCollector.class)
                    .samplingInterval;
    private Histogram myHistogram;
    private MetricsRegistry metricsRegistry;

    public RTFHeapMetricsCollector() {
        super(
                SAMPLING_TIME_INTERVAL,
                "HeapMetrics",
                StatMetrics.HEAP_METRICS_COLLECTOR_EXECUTION_TIME,
                StatExceptionCode.HEAP_METRICS_COLLECTOR_ERROR);
    }

    @Override
    public void collectMetrics(long startTime) {
        metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        if (metricsRegistry == null) {
            LOG.error("could not get the instance of MetricsRegistry class");
            return;
        } else {
            myHistogram = metricsRegistry.createHistogram("my.histogram", "test histogram", "1");
        }

        GCMetrics.runGCMetrics();
        LOG.info("Running collect metrics for RTFHeapMetricsCollector");

        myHistogram.record(
                GCMetrics.getTotYoungGCCollectionCount(),
                Tags.create()
                        .addTag(
                                AllMetrics.HeapDimension.Constants.TYPE_VALUE,
                                AllMetrics.GCType.TOT_YOUNG_GC.toString()
                        ));
    }
}
