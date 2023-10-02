/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.mockito.MockitoAnnotations.initMocks;

import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opensearch.indices.IndicesService;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.config.PluginSettings;
import org.opensearch.performanceanalyzer.commons.event_process.Event;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.ShardStatsValue;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.util.TestUtil;
import org.opensearch.test.OpenSearchSingleNodeTestCase;

@Ignore
/**
 * Note: 'NodeStatsAllShardsMetricsCollector' is already released and out of shadow mode,
 * NodeStatsFixedShardsMetricsCollector class can be deprecated/removed in future versions.
 */
public class NodeStatsFixedShardsMetricsCollectorTests extends OpenSearchSingleNodeTestCase {
    private static final String TEST_INDEX = "test";
    private static long startTimeInMills = 1153721339;
    private NodeStatsFixedShardsMetricsCollector collector;

    @Mock private PerformanceAnalyzerController controller;

    @Before
    public void init() {
        initMocks(this);

        IndicesService indicesService = getInstanceFromNode(IndicesService.class);
        OpenSearchResources.INSTANCE.setIndicesService(indicesService);

        MetricsConfiguration.CONFIG_MAP.put(
                NodeStatsAllShardsMetricsCollector.class, MetricsConfiguration.cdefault);
        collector = new NodeStatsFixedShardsMetricsCollector(controller);

        // clean metricQueue before running every test
        TestUtil.readEvents();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testGetMetricsPath() {
        String expectedPath =
                PluginSettings.instance().getMetricsLocation()
                        + PerformanceAnalyzerMetrics.getTimeInterval(startTimeInMills)
                        + "/"
                        + PerformanceAnalyzerMetrics.sIndicesPath
                        + "/NodesStatsIndex/55";
        String actualPath = collector.getMetricsPath(startTimeInMills, "NodesStatsIndex", "55");
        assertEquals(expectedPath, actualPath);

        try {
            collector.getMetricsPath(startTimeInMills, "NodesStatsIndex");
            fail("Negative scenario test: Should have been a RuntimeException");
        } catch (RuntimeException ex) {
            // - expecting exception...only 1 values passed; 2 expected
        }
    }

    @Test
    public void testNodeStatsMetrics() {
        try {
            collector.saveMetricValues("89123.23", startTimeInMills, "NodesStatsIndex");
            assertTrue("Negative scenario test: Should have been a RuntimeException", true);
        } catch (RuntimeException ex) {
            // - expecting exception...only 1 values passed; 2 expected
        }

        try {
            collector.saveMetricValues("89123.23", startTimeInMills);
            assertTrue("Negative scenario test: Should have been a RuntimeException", true);
        } catch (RuntimeException ex) {
            // - expecting exception...only 0 values passed; 2 expected
        }

        try {
            collector.saveMetricValues(
                    "89123.23", startTimeInMills, "NodesStatsIndex", "55", "123");
            assertTrue("Negative scenario test: Should have been a RuntimeException", true);
        } catch (RuntimeException ex) {
            // - expecting exception...only 3 values passed; 2 expected
        }

        try {
            collector.getNodeIndicesStatsByShardField();
        } catch (Exception exception) {
            assertTrue(
                    "There shouldn't be any exception in the code; Please check the reflection code for any changes",
                    true);
        }

        collector = new NodeStatsFixedShardsMetricsCollector(null);
        try {
            collector.collectMetrics(startTimeInMills);
        } catch (Exception exception) {
            assertTrue(
                    "There shouldn't be any exception in the code; Please check the reflection code for any changes",
                    true);
        }
    }

    @Test
    public void testCollectMetrics() {
        createIndex(TEST_INDEX);
        Mockito.when(controller.getNodeStatsShardsPerCollection()).thenReturn(1);
        collector.collectMetrics(startTimeInMills);

        // cannot make NodeStatsMetricsFixedShardsPerCollectionStatus static to deserialize it, so
        // check with jsonString
        String jsonStr = readMetricsInJsonString();
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.INDEXING_THROTTLE_TIME_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.REFRESH_COUNT_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.REFRESH_TIME_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.FLUSH_COUNT_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.FLUSH_TIME_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.MERGE_COUNT_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.MERGE_TIME_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.MERGE_CURRENT_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.INDEX_BUFFER_BYTES_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.SEGMENTS_COUNT_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.INDEX_WRITER_MEMORY_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.VERSION_MAP_MEMORY_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.BITSET_MEMORY_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.SHARD_SIZE_IN_BYTES_VALUE));
    }

    private String readMetricsInJsonString() {
        List<Event> metrics = TestUtil.readEvents();
        assert metrics.size() == 1;
        String[] jsonStrs = metrics.get(0).value.split("\n");
        assert jsonStrs.length == 2;
        return jsonStrs[1];
    }
}
