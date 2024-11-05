/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.Closeable;
import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opensearch.indices.IndicesService;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.test.OpenSearchSingleNodeTestCase;

public class RTFCacheConfigMetricsCollectorTests extends OpenSearchSingleNodeTestCase {
    private static final String TEST_INDEX = "test";
    private RTFCacheConfigMetricsCollector rtfCacheConfigMetricsCollector;
    private static MetricsRegistry metricsRegistry;
    private static MetricsRegistry metricsRegistry1;
    PerformanceAnalyzerController mockController;
    private long startTimeInMills = 1153721339;

    @Before
    public void init() {
        MetricsConfiguration.CONFIG_MAP.put(
                RTFCacheConfigMetricsCollector.class, MetricsConfiguration.cdefault);
        metricsRegistry = mock(MetricsRegistry.class);
        metricsRegistry1 = mock(MetricsRegistry.class);
        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry);
        IndicesService indicesService = getInstanceFromNode(IndicesService.class);
        OpenSearchResources.INSTANCE.setIndicesService(indicesService);
        ConfigOverridesWrapper mockWrapper = mock(ConfigOverridesWrapper.class);
        mockController = mock(PerformanceAnalyzerController.class);
        Mockito.when(mockController.isCollectorDisabled(any(), anyString())).thenReturn(false);
        rtfCacheConfigMetricsCollector =
                spy(new RTFCacheConfigMetricsCollector(mockController, mockWrapper));
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testCollectMetrics() throws IOException {
        createIndex(TEST_INDEX);
        rtfCacheConfigMetricsCollector.collectMetrics(startTimeInMills);
        verify(metricsRegistry, atLeastOnce())
                .createGauge(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    public void testCollectMetricsRepeated() throws IOException {
        createIndex(TEST_INDEX);
        rtfCacheConfigMetricsCollector.collectMetrics(startTimeInMills);
        verify(metricsRegistry, atLeastOnce())
                .createGauge(anyString(), anyString(), anyString(), any(), any());

        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry1);
        rtfCacheConfigMetricsCollector.collectMetrics(startTimeInMills);
        verify(metricsRegistry1, never())
                .createGauge(anyString(), anyString(), anyString(), any(), any());
    }

    @Test
    public void testCollectMetricsDisableEnable() throws IOException {
        createIndex(TEST_INDEX);
        Closeable fieldCacheGaugeObservable = Mockito.mock(Closeable.class);
        Closeable requestCacheGaugeObservable = Mockito.mock(Closeable.class);

        Mockito.when(
                        metricsRegistry.createGauge(
                                anyString(), anyString(), anyString(), any(), any()))
                .thenReturn(requestCacheGaugeObservable, fieldCacheGaugeObservable);
        rtfCacheConfigMetricsCollector.collectMetrics(startTimeInMills);
        verify(metricsRegistry, times(2))
                .createGauge(anyString(), anyString(), anyString(), any(), any());

        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry1);
        Mockito.when(
                        mockController.isCollectorDisabled(
                                any(), eq("RTFCacheConfigMetricsCollector")))
                .thenReturn(true);
        rtfCacheConfigMetricsCollector.collectMetrics(startTimeInMills);
        verify(fieldCacheGaugeObservable, Mockito.only()).close();
        verify(requestCacheGaugeObservable, Mockito.only()).close();
        verify(metricsRegistry1, never())
                .createGauge(anyString(), anyString(), anyString(), any(), any());

        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry1);
        Mockito.when(
                        mockController.isCollectorDisabled(
                                any(), eq("RTFCacheConfigMetricsCollector")))
                .thenReturn(false);
        rtfCacheConfigMetricsCollector.collectMetrics(startTimeInMills);
        verify(metricsRegistry1, times(2))
                .createGauge(anyString(), anyString(), anyString(), any(), any());
    }
}
