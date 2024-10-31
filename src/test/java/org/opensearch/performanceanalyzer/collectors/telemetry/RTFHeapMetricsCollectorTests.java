/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.atLeastOnce;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.collectors.CollectorTestBase;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.RTFMetrics;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;

public class RTFHeapMetricsCollectorTests extends CollectorTestBase {
    private RTFHeapMetricsCollector rtfHeapMetricsCollector;

    private static MetricsRegistry metricsRegistry;
    private static MetricsRegistry metricsRegistry1;
    private static Histogram gcCollectionEventHistogram;
    private static Histogram gcCollectionTimeHistogram;
    private static Histogram heapUsedHistogram;

    @Before
    public void init() {
        MetricsConfiguration.CONFIG_MAP.put(
                RTFHeapMetricsCollector.class, MetricsConfiguration.cdefault);

        metricsRegistry = mock(MetricsRegistry.class);
        metricsRegistry1 = mock(MetricsRegistry.class);
        gcCollectionEventHistogram = mock(Histogram.class);
        gcCollectionTimeHistogram = mock(Histogram.class);
        heapUsedHistogram = mock(Histogram.class);
        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry);

        when(metricsRegistry.createHistogram(anyString(), anyString(), anyString()))
                .thenAnswer(
                        invocationOnMock -> {
                            String histogramName = (String) invocationOnMock.getArguments()[0];
                            if (histogramName.contains(
                                    RTFMetrics.HeapValue.Constants.COLLECTION_COUNT_VALUE)) {
                                return gcCollectionEventHistogram;
                            } else if (histogramName.contains(
                                    RTFMetrics.HeapValue.Constants.COLLECTION_TIME_VALUE)) {
                                return gcCollectionTimeHistogram;
                            }
                            return heapUsedHistogram;
                        });
        rtfHeapMetricsCollector = new RTFHeapMetricsCollector(mockController, mockWrapper);
    }

    @Test
    public void testCollectMetrics() throws IOException {
        rtfHeapMetricsCollector.collectMetrics(System.currentTimeMillis());
        verify(heapUsedHistogram, atLeastOnce()).record(anyDouble(), any());
        verify(gcCollectionTimeHistogram, atLeastOnce()).record(anyDouble(), any());
        verify(gcCollectionEventHistogram, atLeastOnce()).record(anyDouble(), any());
        verify(metricsRegistry, atLeastOnce())
                .createGauge(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    public void testCollectMetricsRepeated() throws IOException {

        rtfHeapMetricsCollector.collectMetrics(System.currentTimeMillis());
        verify(heapUsedHistogram, atLeastOnce()).record(anyDouble(), any());
        verify(gcCollectionTimeHistogram, atLeastOnce()).record(anyDouble(), any());
        verify(gcCollectionEventHistogram, atLeastOnce()).record(anyDouble(), any());
        verify(metricsRegistry, atLeastOnce())
                .createGauge(anyString(), anyString(), anyString(), any(), any());

        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry1);
        rtfHeapMetricsCollector.collectMetrics(System.currentTimeMillis());
        verify(metricsRegistry1, never())
                .createGauge(anyString(), anyString(), anyString(), any(), any());
    }
}
