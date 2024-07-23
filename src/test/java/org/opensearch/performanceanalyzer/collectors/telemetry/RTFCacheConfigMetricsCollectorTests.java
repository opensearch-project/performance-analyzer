/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.test.OpenSearchSingleNodeTestCase;

public class RTFCacheConfigMetricsCollectorTests extends OpenSearchSingleNodeTestCase {
    private static final String TEST_INDEX = "test";
    private RTFCacheConfigMetricsCollector rtfCacheConfigMetricsCollector;
    private static MetricsRegistry metricsRegistry;
    private static Histogram testHistogram;
    private long startTimeInMills = 1153721339;

    @Before
    public void init() {
        MetricsConfiguration.CONFIG_MAP.put(
                RTFCacheConfigMetricsCollector.class, MetricsConfiguration.cdefault);
        metricsRegistry = mock(MetricsRegistry.class);
        testHistogram = mock(Histogram.class);
        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry);
        when(metricsRegistry.createHistogram(anyString(), anyString(), anyString()))
                .thenReturn(testHistogram);
        IndicesService indicesService = getInstanceFromNode(IndicesService.class);
        OpenSearchResources.INSTANCE.setIndicesService(indicesService);
        ConfigOverridesWrapper mockWrapper = mock(ConfigOverridesWrapper.class);
        PerformanceAnalyzerController mockController = mock(PerformanceAnalyzerController.class);
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
        verify(testHistogram, atLeastOnce()).record(anyDouble(), any());
    }
}
