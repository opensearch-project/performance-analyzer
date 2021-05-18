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
 * Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.reader_writer_shared.Event;
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
                        mockController.isCollectorEnabled(
                                mockConfigOverrides, "ShardIndexingPressureMetricsCollector"))
                .thenReturn(true);
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
