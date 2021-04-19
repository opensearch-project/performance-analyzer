/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

/*
 * Copyright 2019-2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.opendistro.opensearch.performanceanalyzer.collectors;

import static org.mockito.MockitoAnnotations.initMocks;

import com.amazon.opendistro.opensearch.performanceanalyzer.OpenSearchResources;
import com.amazon.opendistro.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import com.amazon.opendistro.opensearch.performanceanalyzer.config.PluginSettings;
import com.amazon.opendistro.opensearch.performanceanalyzer.metrics.AllMetrics.ShardStatsValue;
import com.amazon.opendistro.opensearch.performanceanalyzer.metrics.MetricsConfiguration;
import com.amazon.opendistro.opensearch.performanceanalyzer.metrics.PerformanceAnalyzerMetrics;
import com.amazon.opendistro.opensearch.performanceanalyzer.reader_writer_shared.Event;
import com.amazon.opendistro.opensearch.performanceanalyzer.util.TestUtil;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opensearch.indices.IndicesService;
import org.opensearch.test.OpenSearchSingleNodeTestCase;

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
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.SEGMENTS_MEMORY_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.TERMS_MEMORY_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.STORED_FIELDS_MEMORY_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.TERM_VECTOR_MEMORY_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.NORMS_MEMORY_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.POINTS_MEMORY_VALUE));
        assertTrue(jsonStr.contains(ShardStatsValue.Constants.DOC_VALUES_MEMORY_VALUE));
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
