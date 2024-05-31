/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import java.lang.management.MemoryUsage;
import java.util.Map;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.collectors.HeapMetricsCollector;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.jvm.GCMetrics;
import org.opensearch.performanceanalyzer.commons.jvm.HeapMetrics;
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
            MetricsConfiguration.CONFIG_MAP.get(HeapMetricsCollector.class).samplingInterval;
    private Histogram gcCollectionEventMetrics;
    private Histogram gcCollectionTimeMetrics;
    private Histogram heapUsedMetrics;
    private MetricsRegistry metricsRegistry;
    private final String memTypeAttributeKey = "mem_type";
    private boolean metricsInitialised;

    public RTFHeapMetricsCollector() {
        super(
                SAMPLING_TIME_INTERVAL,
                "RTFHeapMetricsCollector",
                StatMetrics.HEAP_METRICS_COLLECTOR_EXECUTION_TIME,
                StatExceptionCode.HEAP_METRICS_COLLECTOR_ERROR);
        this.metricsInitialised = false;
    }

    @Override
    public void collectMetrics(long startTime) {
        metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        if (metricsRegistry == null) {
            LOG.error("could not get the instance of MetricsRegistry class");
            return;
        }

        initialiseMetricsIfNeeded();
        GCMetrics.runGCMetrics();
        LOG.debug("Executing collect metrics for RTFHeapMetricsCollector");
        recordMetrics();
    }

    private void initialiseMetricsIfNeeded() {
        if (metricsInitialised == false) {
            gcCollectionEventMetrics =
                    metricsRegistry.createHistogram(
                            AllMetrics.HeapValue.Constants.COLLECTION_COUNT_VALUE,
                            "GC Collection Event PA Metrics",
                            "");

            gcCollectionTimeMetrics =
                    metricsRegistry.createHistogram(
                            AllMetrics.HeapValue.Constants.COLLECTION_TIME_VALUE,
                            "GC Collection Time PA Metrics",
                            "");

            heapUsedMetrics =
                    metricsRegistry.createHistogram(
                            AllMetrics.HeapValue.Constants.USED_VALUE,
                            "GC Heap Used PA Metrics",
                            "");
            metricsInitialised = true;
        }
    }

    private void recordMetrics() {
        Tags totYoungGCTag =
                Tags.create()
                        .addTag(memTypeAttributeKey, AllMetrics.GCType.TOT_YOUNG_GC.toString());

        Tags totFullGCTag =
                Tags.create().addTag(memTypeAttributeKey, AllMetrics.GCType.TOT_FULL_GC.toString());

        gcCollectionEventMetrics.record(GCMetrics.getTotYoungGCCollectionCount(), totYoungGCTag);

        gcCollectionEventMetrics.record(GCMetrics.getTotFullGCCollectionCount(), totFullGCTag);

        gcCollectionTimeMetrics.record(GCMetrics.getTotYoungGCCollectionTime(), totYoungGCTag);

        gcCollectionTimeMetrics.record(GCMetrics.getTotFullGCCollectionTime(), totFullGCTag);

        for (Map.Entry<String, Supplier<MemoryUsage>> entry :
                HeapMetrics.getMemoryUsageSuppliers().entrySet()) {
            MemoryUsage memoryUsage = entry.getValue().get();
            heapUsedMetrics.record(
                    memoryUsage.getUsed(),
                    Tags.create().addTag(memTypeAttributeKey, entry.getKey()));
            metricsRegistry.createGauge(
                    AllMetrics.HeapValue.Constants.MAX_VALUE,
                    "Heap Max PA metrics",
                    "",
                    () -> (double) memoryUsage.getMax(),
                    Tags.create().addTag(memTypeAttributeKey, entry.getKey()));
        }
    }
}
