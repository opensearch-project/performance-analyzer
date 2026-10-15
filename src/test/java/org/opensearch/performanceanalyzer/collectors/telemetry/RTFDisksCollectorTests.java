/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.collectors.CollectorTestBase;
import org.opensearch.performanceanalyzer.commons.OSMetricsGeneratorFactory;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics_generator.DiskMetricsGenerator;
import org.opensearch.performanceanalyzer.commons.metrics_generator.OSMetricsGenerator;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@PowerMockIgnore({"org.apache.logging.log4j.*"})
@RunWith(PowerMockRunner.class)
@PrepareForTest({OSMetricsGeneratorFactory.class})
public class RTFDisksCollectorTests extends CollectorTestBase {
    private RTFDisksCollector rtfDisksCollector;
    private static MetricsRegistry metricsRegistry;
    private static Histogram testHistogram;

    @Before
    public void init() {
        MetricsConfiguration.CONFIG_MAP.put(RTFDisksCollector.class, MetricsConfiguration.cdefault);

        // Stub the OS-specific metrics generator factory so the collector reaches
        // recordMetrics on any platform. OSMetricsGeneratorFactory.getInstance() only
        // returns a generator on Linux (and caches os.name in a static final field at
        // class load), so without this stub the test is non-deterministic across
        // platforms and test-execution ordering.
        OSMetricsGenerator osMetricsGenerator = mock(OSMetricsGenerator.class);
        DiskMetricsGenerator diskMetricsGenerator = mock(DiskMetricsGenerator.class);
        when(diskMetricsGenerator.getAllDisks()).thenReturn(Collections.singleton("testDisk"));
        when(osMetricsGenerator.getDiskMetricsGenerator()).thenReturn(diskMetricsGenerator);
        PowerMockito.mockStatic(OSMetricsGeneratorFactory.class);
        PowerMockito.when(OSMetricsGeneratorFactory.getInstance()).thenReturn(osMetricsGenerator);

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
