/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.atLeastOnce;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.collectors.CollectorTestBase;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.threadpool.ThreadPoolStats;

public class RTFThreadPoolMetricsCollectorTests extends CollectorTestBase {
    private RTFThreadPoolMetricsCollector rtfThreadPoolMetricsCollector;
    private static MetricsRegistry metricsRegistry;
    private static Histogram threadPoolQueueSizeHistogram;
    private static Histogram threadPoolRejectedReqsHistogram;
    private static Histogram threadPoolTotalThreadsHistogram;
    private static Histogram threadPoolActiveThreadsHistogram;
    private static Histogram threadPoolQueueCapacityHistogram;
    @Mock private ThreadPool mockThreadPool;

    @Before
    public void init() {
        MetricsConfiguration.CONFIG_MAP.put(
                RTFThreadPoolMetricsCollector.class, MetricsConfiguration.cdefault);
        metricsRegistry = mock(MetricsRegistry.class);
        threadPoolQueueSizeHistogram = mock(Histogram.class);
        threadPoolRejectedReqsHistogram = mock(Histogram.class);
        threadPoolActiveThreadsHistogram = mock(Histogram.class);
        threadPoolTotalThreadsHistogram = mock(Histogram.class);
        threadPoolQueueCapacityHistogram = mock(Histogram.class);

        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry);
        OpenSearchResources.INSTANCE.setThreadPool(mockThreadPool);

        when(metricsRegistry.createHistogram(anyString(), anyString(), anyString()))
                .thenAnswer(
                        invocationOnMock -> {
                            String histogramName = (String) invocationOnMock.getArguments()[0];
                            if (histogramName.contains(
                                    AllMetrics.ThreadPoolValue.Constants.QUEUE_SIZE_VALUE)) {
                                return threadPoolQueueSizeHistogram;
                            } else if (histogramName.contains(
                                    AllMetrics.ThreadPoolValue.Constants.REJECTED_VALUE)) {
                                return threadPoolRejectedReqsHistogram;
                            } else if (histogramName.contains(
                                    AllMetrics.ThreadPoolValue.Constants.THREADS_ACTIVE_VALUE)) {
                                return threadPoolActiveThreadsHistogram;
                            } else if (histogramName.contains(
                                    AllMetrics.ThreadPoolValue.Constants.QUEUE_CAPACITY_VALUE)) {
                                return threadPoolQueueCapacityHistogram;
                            }
                            return threadPoolTotalThreadsHistogram;
                        });
        rtfThreadPoolMetricsCollector =
                new RTFThreadPoolMetricsCollector(mockController, mockWrapper);
    }

    @Test
    public void testCollectMetrics() throws IOException {
        when(mockThreadPool.stats()).thenReturn(generateThreadPoolStat());
        rtfThreadPoolMetricsCollector.collectMetrics(System.currentTimeMillis());
        verify(mockThreadPool, atLeastOnce()).stats();
        verify(threadPoolQueueSizeHistogram, atLeastOnce()).record(anyDouble(), any());
        verify(threadPoolRejectedReqsHistogram, atLeastOnce()).record(anyDouble(), any());
        verify(threadPoolActiveThreadsHistogram, atLeastOnce()).record(anyDouble(), any());
        verify(threadPoolTotalThreadsHistogram, atLeastOnce()).record(anyDouble(), any());
    }

    private ThreadPoolStats generateThreadPoolStat() {
        List<ThreadPoolStats.Stats> stats = new ArrayList<>();
        stats.add(new ThreadPoolStats.Stats("write", 0, 0, 0, 2, 0, 0L, 20L));
        return new ThreadPoolStats(stats);
    }
}
