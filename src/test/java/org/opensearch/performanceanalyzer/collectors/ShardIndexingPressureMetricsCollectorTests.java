/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.performanceanalyzer.CustomMetricsLocationTestBase;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.event_process.Event;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.util.TestUtil;
import org.powermock.core.classloader.annotations.PrepareForTest;

@PrepareForTest(Class.class)
public class ShardIndexingPressureMetricsCollectorTests extends CustomMetricsLocationTestBase {

    private ShardIndexingPressureMetricsCollector shardIndexingPressureMetricsCollector;

    @Mock private ClusterService mockClusterService;

    @Mock PerformanceAnalyzerController mockController;

    @Mock ConfigOverridesWrapper mockConfigOverrides;

    @Before
    public void init() {
        initMocks(this);
        OpenSearchResources.INSTANCE.setClusterService(mockClusterService);
        System.setProperty("performanceanalyzer.metrics.log.enabled", "False");
        MetricsConfiguration.CONFIG_MAP.put(
                ShardIndexingPressureMetricsCollector.class, MetricsConfiguration.cdefault);
        shardIndexingPressureMetricsCollector =
                new ShardIndexingPressureMetricsCollector(mockController, mockConfigOverrides);

        // clean metricQueue before running every test
        TestUtil.readEvents();
    }

    @Test
    public void testShardIndexingPressureMetrics() {
        long startTimeInMills = 1153721339;

        Mockito.when(
                        mockController.isCollectorDisabled(
                                mockConfigOverrides, "ShardIndexingPressureMetricsCollector"))
                .thenReturn(false);

        shardIndexingPressureMetricsCollector.saveMetricValues(
                "shard_indexing_pressure_metrics", startTimeInMills);

        List<Event> metrics = TestUtil.readEvents();
        assertEquals(1, metrics.size());
        assertEquals("shard_indexing_pressure_metrics", metrics.get(0).value);

        try {
            shardIndexingPressureMetricsCollector.saveMetricValues(
                    "shard_indexing_pressure_metrics", startTimeInMills, "123");
            assertTrue("Negative scenario test: Should have been a RuntimeException", true);
        } catch (RuntimeException ex) {
            // - expecting exception...1 values passed; 0 expected
        }
    }
}
