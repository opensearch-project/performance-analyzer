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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opensearch.indices.IndicesService;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.telemetry.metrics.Counter;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.test.OpenSearchSingleNodeTestCase;

public class RTFNodeStatsAllShardsMetricsCollectorTests extends OpenSearchSingleNodeTestCase {

    private long startTimeInMills = 1153721339;
    private static final String TEST_INDEX = "test";
    private RTFNodeStatsAllShardsMetricsCollector rtfNodeStatsAllShardsMetricsCollector;
    private static MetricsRegistry metricsRegistry;
    private static Counter cacheQueryHitCounter;
    private static Counter cacheQueryMissCounter;
    private static Counter cacheQuerySizeCounter;
    private static Counter cacheFieldDataEvictionCounter;
    private static Counter cacheFieldDataSizeCounter;
    private static Counter cacheRequestHitCounter;
    private static Counter cacheRequestMissCounter;
    private static Counter cacheRequestEvictionCounter;
    private static Counter cacheRequestSizeCounter;

    @Before
    public void init() {
        MetricsConfiguration.CONFIG_MAP.put(
                RTFNodeStatsAllShardsMetricsCollector.class, MetricsConfiguration.cdefault);

        IndicesService indicesService = getInstanceFromNode(IndicesService.class);
        OpenSearchResources.INSTANCE.setIndicesService(indicesService);
        metricsRegistry = mock(MetricsRegistry.class);
        cacheFieldDataEvictionCounter = mock(Counter.class);
        cacheFieldDataSizeCounter = mock(Counter.class);
        cacheQueryHitCounter = mock(Counter.class);
        cacheQueryMissCounter = mock(Counter.class);
        cacheRequestEvictionCounter = mock(Counter.class);
        cacheRequestMissCounter = mock(Counter.class);
        cacheRequestHitCounter = mock(Counter.class);
        cacheRequestSizeCounter = mock(Counter.class);
        cacheQuerySizeCounter = mock(Counter.class);

        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry);

        when(metricsRegistry.createCounter(anyString(), anyString(), anyString()))
                .thenAnswer(
                        invocationOnMock -> {
                            String counterName = (String) invocationOnMock.getArguments()[0];
                            if (counterName.contains(
                                    AllMetrics.ShardStatsValue.Constants
                                            .REQUEST_CACHE_IN_BYTES_VALUE)) {
                                return cacheRequestSizeCounter;
                            } else if (counterName.contains(
                                    AllMetrics.ShardStatsValue.Constants
                                            .REQUEST_CACHE_HIT_COUNT_VALUE)) {
                                return cacheRequestHitCounter;
                            } else if (counterName.contains(
                                    AllMetrics.ShardStatsValue.Constants
                                            .REQUEST_CACHE_MISS_COUNT_VALUE)) {
                                return cacheRequestMissCounter;
                            } else if (counterName.contains(
                                    AllMetrics.ShardStatsValue.Constants
                                            .REQUEST_CACHE_EVICTION_VALUE)) {
                                return cacheRequestEvictionCounter;
                            } else if (counterName.contains(
                                    AllMetrics.ShardStatsValue.Constants
                                            .FIELDDATA_EVICTION_VALUE)) {
                                return cacheFieldDataEvictionCounter;
                            } else if (counterName.contains(
                                    AllMetrics.ShardStatsValue.Constants
                                            .FIELD_DATA_IN_BYTES_VALUE)) {
                                return cacheFieldDataSizeCounter;
                            } else if (counterName.contains(
                                    AllMetrics.ShardStatsValue.Constants
                                            .QUERY_CACHE_IN_BYTES_VALUE)) {
                                return cacheQuerySizeCounter;
                            } else if (counterName.contains(
                                    AllMetrics.ShardStatsValue.Constants
                                            .QUEY_CACHE_HIT_COUNT_VALUE)) {
                                return cacheQueryHitCounter;
                            }
                            return cacheQueryMissCounter;
                        });

        ConfigOverridesWrapper mockWrapper = mock(ConfigOverridesWrapper.class);
        PerformanceAnalyzerController mockController = mock(PerformanceAnalyzerController.class);
        Mockito.when(mockController.isCollectorDisabled(any(), anyString())).thenReturn(false);
        Mockito.when(mockController.rcaCollectorsEnabled()).thenReturn(true);
        Mockito.when(mockController.telemetryCollectorsEnabled()).thenReturn(true);

        rtfNodeStatsAllShardsMetricsCollector =
                spy(new RTFNodeStatsAllShardsMetricsCollector(mockController, mockWrapper));
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testCollectMetrics() throws IOException {
        createIndex(TEST_INDEX);
        rtfNodeStatsAllShardsMetricsCollector.collectMetrics(startTimeInMills);
        verify(rtfNodeStatsAllShardsMetricsCollector, never())
                .populateDiffMetricValue(any(), any(), anyString(), anyInt());
        startTimeInMills += 500;
        rtfNodeStatsAllShardsMetricsCollector.collectMetrics(startTimeInMills);
        verify(rtfNodeStatsAllShardsMetricsCollector, times(1))
                .populateDiffMetricValue(any(), any(), anyString(), anyInt());
        verify(cacheFieldDataEvictionCounter, atLeastOnce()).add(anyDouble(), any());
        verify(cacheFieldDataSizeCounter, atLeastOnce()).add(anyDouble(), any());
        verify(cacheQueryMissCounter, atLeastOnce()).add(anyDouble(), any());
        verify(cacheQueryHitCounter, atLeastOnce()).add(anyDouble(), any());
        verify(cacheQuerySizeCounter, atLeastOnce()).add(anyDouble(), any());
        verify(cacheRequestEvictionCounter, atLeastOnce()).add(anyDouble(), any());
        verify(cacheRequestHitCounter, atLeastOnce()).add(anyDouble(), any());
        verify(cacheRequestMissCounter, atLeastOnce()).add(anyDouble(), any());
        verify(cacheRequestSizeCounter, atLeastOnce()).add(anyDouble(), any());
    }
}
