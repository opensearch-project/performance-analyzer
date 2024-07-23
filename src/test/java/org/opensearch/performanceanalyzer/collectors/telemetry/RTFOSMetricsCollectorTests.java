/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.collectors.CollectorTestBase;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;

public class RTFOSMetricsCollectorTests extends CollectorTestBase {
    private RTFOSMetricsCollector rtfosMetricsCollector;
    private static MetricsRegistry metricsRegistry;
    private static Histogram testHistogram;

    @Before
    public void init() {
        MetricsConfiguration.CONFIG_MAP.put(
                RTFOSMetricsCollector.class, MetricsConfiguration.cdefault);
        System.setProperty("os.name", "Linux");
        metricsRegistry = mock(MetricsRegistry.class);
        testHistogram = mock(Histogram.class);
        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry);
        when(metricsRegistry.createHistogram(anyString(), anyString(), anyString()))
                .thenReturn(testHistogram);
        rtfosMetricsCollector = spy(new RTFOSMetricsCollector(mockController, mockWrapper));
    }

    @Test
    public void testCollectMetrics() throws IOException {
        rtfosMetricsCollector.collectMetrics(System.currentTimeMillis());
        verify(rtfosMetricsCollector, atLeastOnce()).recordMetrics(any());
    }
}
