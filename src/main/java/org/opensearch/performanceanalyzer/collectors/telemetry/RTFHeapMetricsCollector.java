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

    private int count;
    private Histogram GCCollectionEventMetrics;
    private Histogram GCCollectionTimeMetrics;
    private Histogram HeapUsedMetrics;
    private MetricsRegistry metricsRegistry;

    public RTFHeapMetricsCollector() {
        super(
                SAMPLING_TIME_INTERVAL,
                "RTFHeapMetricsCollector",
                StatMetrics.HEAP_METRICS_COLLECTOR_EXECUTION_TIME,
                StatExceptionCode.HEAP_METRICS_COLLECTOR_ERROR);
        this.count = 0;
    }

    @Override
    public void collectMetrics(long startTime) {
        count += 1;
        metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        if (metricsRegistry == null) {
            LOG.error("could not get the instance of MetricsRegistry class");
            return;
        }

        initialiseMetrics();
        GCMetrics.runGCMetrics();
        LOG.info("Executing collect metrics for RTFHeapMetricsCollector");
        recordMetrics();
    }

    private void initialiseMetrics() {
        GCCollectionEventMetrics =
                metricsRegistry.createHistogram(
                        AllMetrics.HeapValue.Constants.COLLECTION_COUNT_VALUE,
                        "GC Collection Event PA Metrics",
                        "1");

        GCCollectionTimeMetrics =
                metricsRegistry.createHistogram(
                        AllMetrics.HeapValue.Constants.COLLECTION_TIME_VALUE,
                        "GC Collection Time PA Metrics",
                        "1");

        HeapUsedMetrics =
                metricsRegistry.createHistogram(
                        AllMetrics.HeapValue.Constants.USED_VALUE, "GC Heap Used PA Metrics", "1");
    }

    private void recordMetrics() {
        Tags TotYoungGCTag =
                Tags.create()
                        .addTag(
                                AllMetrics.HeapDimension.Constants.TYPE_VALUE,
                                AllMetrics.GCType.TOT_YOUNG_GC.toString());

        Tags TotFullGCTag =
                Tags.create()
                        .addTag(
                                AllMetrics.HeapDimension.Constants.TYPE_VALUE,
                                AllMetrics.GCType.TOT_FULL_GC.toString());

        GCCollectionEventMetrics.record(GCMetrics.getTotYoungGCCollectionCount(), TotYoungGCTag);

        GCCollectionEventMetrics.record(GCMetrics.getTotFullGCCollectionCount(), TotFullGCTag);

        GCCollectionTimeMetrics.record(GCMetrics.getTotYoungGCCollectionTime(), TotYoungGCTag);

        GCCollectionTimeMetrics.record(GCMetrics.getTotFullGCCollectionTime(), TotFullGCTag);

        for (Map.Entry<String, Supplier<MemoryUsage>> entry :
                HeapMetrics.getMemoryUsageSuppliers().entrySet()) {
            MemoryUsage memoryUsage = entry.getValue().get();
            HeapUsedMetrics.record(
                    memoryUsage.getUsed(),
                    Tags.create()
                            .addTag(AllMetrics.HeapDimension.Constants.TYPE_VALUE, entry.getKey()));
        }

        if (count == 12) {
            count = 0;
            for (Map.Entry<String, Supplier<MemoryUsage>> entry :
                    HeapMetrics.getMemoryUsageSuppliers().entrySet()) {
                MemoryUsage memoryUsage = entry.getValue().get();
                //                HeapMaxMetrics.add(
                //                        memoryUsage.getMax(),
                //                        Tags.create()
                //                                .addTag(
                //
                // AllMetrics.HeapDimension.Constants.TYPE_VALUE,
                //                                        entry.getKey()));
                metricsRegistry.createGauge(
                        AllMetrics.HeapValue.Constants.MAX_VALUE,
                        "Heap Max PA metrics",
                        "1",
                        () -> (double) memoryUsage.getMax(),
                        Tags.create()
                                .addTag(
                                        AllMetrics.HeapDimension.Constants.TYPE_VALUE,
                                        entry.getKey()));
            }
        }
    }
}
