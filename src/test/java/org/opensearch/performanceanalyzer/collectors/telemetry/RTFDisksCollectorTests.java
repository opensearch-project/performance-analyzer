/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.collectors.CollectorTestBase;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;

public class RTFDisksCollectorTests extends CollectorTestBase {
    private RTFDisksCollector rtfDisksCollector;
    private static MetricsRegistry metricsRegistry;
    private static Histogram testHistogram;

    @Before
    public void init() {
        MetricsConfiguration.CONFIG_MAP.put(RTFDisksCollector.class, MetricsConfiguration.cdefault);
        System.setProperty("os.name", "Linux");
        metricsRegistry = mock(MetricsRegistry.class);
        testHistogram = mock(Histogram.class);
        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry);
        when(metricsRegistry.createHistogram(anyString(), anyString(), anyString()))
                .thenReturn(testHistogram);
        rtfDisksCollector = spy(new RTFDisksCollector(mockController, mockWrapper));
    }

    @Test
    public void testCollectMetrics() throws IOException {
        rtfDisksCollector.collectMetrics(System.currentTimeMillis());
        verify(rtfDisksCollector, atLeastOnce()).recordMetrics(any());
    }
}
