/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.opensearch.indices.IndicesService;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.RTFMetrics;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.telemetry.metrics.Counter;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.test.OpenSearchSingleNodeTestCase;

public class RTFShardOperationRateCollectorTests extends OpenSearchSingleNodeTestCase {

    private long startTimeInMills = 1153721339;
    private static final String TEST_INDEX = "test";
    private RTFShardOperationRateCollector rtfShardOperationRateCollector;

    @Mock private MetricsRegistry metricsRegistry;
    @Mock private Counter indexingRateCounter;
    @Mock private Counter searchRateCounter;
    @Mock private ConfigOverridesWrapper configOverridesWrapper;
    @Mock private PerformanceAnalyzerController performanceAnalyzerController;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);

        MetricsConfiguration.CONFIG_MAP.put(
                RTFShardOperationRateCollector.class, MetricsConfiguration.cdefault);
        IndicesService indicesService = getInstanceFromNode(IndicesService.class);
        OpenSearchResources.INSTANCE.setIndicesService(indicesService);
        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry);

        when(metricsRegistry.createCounter(anyString(), anyString(), anyString()))
                .thenReturn(indexingRateCounter)
                .thenReturn(searchRateCounter);

        when(metricsRegistry.createCounter(anyString(), anyString(), anyString()))
                .thenAnswer(
                        invocationOnMock -> {
                            String counterName = (String) invocationOnMock.getArguments()[0];
                            if (counterName.contains(
                                    RTFMetrics.OperationsValue.Constants.INDEXING_RATE)) {
                                return indexingRateCounter;
                            }
                            return searchRateCounter;
                        });

        when(performanceAnalyzerController.isCollectorDisabled(any(), anyString()))
                .thenReturn(false);

        rtfShardOperationRateCollector =
                spy(
                        new RTFShardOperationRateCollector(
                                performanceAnalyzerController, configOverridesWrapper));
    }

    @Test
    public void testCollectMetrics() throws IOException {
        createIndex(TEST_INDEX);
        rtfShardOperationRateCollector.collectMetrics(startTimeInMills);

        verify(indexingRateCounter, never()).add(anyDouble(), any());
        verify(searchRateCounter, never()).add(anyDouble(), any());

        startTimeInMills += 60000;
        rtfShardOperationRateCollector.collectMetrics(startTimeInMills);

        verify(indexingRateCounter, atLeastOnce()).add(anyDouble(), any());
        verify(searchRateCounter, atLeastOnce()).add(anyDouble(), any());
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }
}
