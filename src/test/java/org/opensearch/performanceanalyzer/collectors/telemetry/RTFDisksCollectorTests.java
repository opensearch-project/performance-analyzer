/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;

public class RTFDisksCollectorTests {
    private RTFDisksCollector rtfDisksCollector;
    private static MetricsRegistry metricsRegistry;
    private static Histogram diskWaitTimeHistogram;
    private static Histogram diskServiceRateHistogram;
    private static Histogram diskUtilizationHistogram;

    private static final String OS_TYPE = System.getProperty("os.name");

    @Before
    public void init() {
        initMocks(this);
        System.setProperty("performanceanalyzer.metrics.log.enabled", "False");
        MetricsConfiguration.CONFIG_MAP.put(RTFDisksCollector.class, MetricsConfiguration.cdefault);

        metricsRegistry = mock(MetricsRegistry.class);
        diskWaitTimeHistogram = mock(Histogram.class);
        diskServiceRateHistogram = mock(Histogram.class);
        diskUtilizationHistogram = mock(Histogram.class);
        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry);

        when(metricsRegistry.createHistogram(anyString(), anyString(), anyString()))
                .thenAnswer(
                        invocationOnMock -> {
                            String histogramName = (String) invocationOnMock.getArguments()[0];
                            if (histogramName.contains(AllMetrics.DiskValue.Constants.WAIT_VALUE)) {
                                return diskWaitTimeHistogram;
                            } else if (histogramName.contains(
                                    AllMetrics.DiskValue.Constants.SRATE_VALUE)) {
                                return diskServiceRateHistogram;
                            }
                            return diskUtilizationHistogram;
                        });

        rtfDisksCollector = new RTFDisksCollector();
    }

    @Test
    public void testCollectMetrics() throws IOException {
        if (isLinux()) {
            rtfDisksCollector.collectMetrics(System.currentTimeMillis());
            verify(diskUtilizationHistogram, atLeastOnce()).record(anyDouble(), any());
            verify(diskServiceRateHistogram, atLeastOnce()).record(anyDouble(), any());
            verify(diskWaitTimeHistogram, atLeastOnce()).record(anyDouble(), any());
        }
    }

    private static boolean isLinux() {
        return OS_TYPE.toLowerCase().contains("linux");
    }
}
