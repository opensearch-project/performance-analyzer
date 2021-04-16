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

import static com.amazon.opendistro.opensearch.performanceanalyzer.metrics.AllMetrics.ShardType.SHARD_PRIMARY;
import static com.amazon.opendistro.opensearch.performanceanalyzer.metrics.AllMetrics.ShardType.SHARD_REPLICA;
import static org.opensearch.test.OpenSearchTestCase.settings;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import com.amazon.opendistro.opensearch.performanceanalyzer.OpenSearchResources;
import com.amazon.opendistro.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import com.amazon.opendistro.opensearch.performanceanalyzer.config.PluginSettings;
import com.amazon.opendistro.opensearch.performanceanalyzer.config.overrides.ConfigOverridesWrapper;
import com.amazon.opendistro.opensearch.performanceanalyzer.metrics.MetricsConfiguration;
import com.amazon.opendistro.opensearch.performanceanalyzer.metrics.PerformanceAnalyzerMetrics;
import com.amazon.opendistro.opensearch.performanceanalyzer.reader_writer_shared.Event;
import com.amazon.opendistro.opensearch.performanceanalyzer.util.TestUtil;
import com.carrotsearch.randomizedtesting.RandomizedRunner;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paranamer.ParanamerModule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.opensearch.Version;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.metadata.IndexMetadata;
import org.opensearch.cluster.metadata.Metadata;
import org.opensearch.cluster.routing.IndexRoutingTable;
import org.opensearch.cluster.routing.RoutingTable;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

@RunWith(RandomizedRunner.class)
public class ShardStateCollectorTests {
    private static final String TEST_INDEX = "test";
    private static final int NUMBER_OF_PRIMARY_SHARDS = 1;
    private static final int NUMBER_OF_REPLICAS = 1;

    private long startTimeInMills = 1153721339;
    private ShardStateCollector shardStateCollector;
    private ClusterService clusterService;
    private PerformanceAnalyzerController controller;
    private ConfigOverridesWrapper configOverrides;

    @Before
    public void init() {
        clusterService = Mockito.mock(ClusterService.class);
        OpenSearchResources.INSTANCE.setClusterService(clusterService);

        System.setProperty("performanceanalyzer.metrics.log.enabled", "False");
        MetricsConfiguration.CONFIG_MAP.put(
                ShardStateCollector.class, MetricsConfiguration.cdefault);
        controller = Mockito.mock(PerformanceAnalyzerController.class);
        configOverrides = Mockito.mock(ConfigOverridesWrapper.class);
        shardStateCollector = new ShardStateCollector(controller, configOverrides);

        // clean metricQueue before running every test
        TestUtil.readEvents();
    }

    @Test
    public void testGetMetricsPath() {
        String expectedPath =
                PluginSettings.instance().getMetricsLocation()
                        + PerformanceAnalyzerMetrics.getTimeInterval(startTimeInMills)
                        + "/"
                        + PerformanceAnalyzerMetrics.sShardStatePath;
        String actualPath = shardStateCollector.getMetricsPath(startTimeInMills);
        assertEquals(expectedPath, actualPath);

        try {
            shardStateCollector.getMetricsPath(startTimeInMills, "shardStatePath");
            fail("Negative scenario test: Should have been a RuntimeException");
        } catch (RuntimeException ex) {
            // - expecting exception...1 values passed; 0 expected
        }
    }

    @Ignore
    @Test
    public void testCollectMetrics() throws IOException {

        Mockito.when(controller.isCollectorEnabled(configOverrides, "ShardsStateCollector"))
                .thenReturn(true);
        Mockito.when(clusterService.state()).thenReturn(generateClusterState());
        shardStateCollector.collectMetrics(startTimeInMills);
        List<ShardStateCollector.ShardStateMetrics> metrics = readMetrics();
        assertEquals(NUMBER_OF_PRIMARY_SHARDS + NUMBER_OF_REPLICAS, metrics.size());
        assertEquals(SHARD_PRIMARY.toString(), metrics.get(0).getShardType());
        assertEquals(SHARD_REPLICA.toString(), metrics.get(1).getShardType());
    }

    private ClusterState generateClusterState() {
        Metadata metaData =
                Metadata.builder()
                        .put(
                                IndexMetadata.builder(TEST_INDEX)
                                        .settings(settings(Version.CURRENT))
                                        .numberOfShards(NUMBER_OF_PRIMARY_SHARDS)
                                        .numberOfReplicas(NUMBER_OF_REPLICAS))
                        .build();

        RoutingTable testRoutingTable =
                new RoutingTable.Builder()
                        .add(
                                new IndexRoutingTable.Builder(metaData.index(TEST_INDEX).getIndex())
                                        .initializeAsNew(metaData.index(TEST_INDEX))
                                        .build())
                        .build();

        return ClusterState.builder(
                        org.opensearch.cluster.ClusterName.CLUSTER_NAME_SETTING.getDefault(
                                Settings.EMPTY))
                .metadata(metaData)
                .routingTable(testRoutingTable)
                .build();
    }

    private List<ShardStateCollector.ShardStateMetrics> readMetrics() throws IOException {
        List<Event> metrics = TestUtil.readEvents();
        assert metrics.size() == 1;
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new ParanamerModule());
        String[] jsonStrs = metrics.get(0).value.split("\n");
        assert jsonStrs.length == 4;
        List<ShardStateCollector.ShardStateMetrics> list = new ArrayList<>();
        list.add(objectMapper.readValue(jsonStrs[2], ShardStateCollector.ShardStateMetrics.class));
        list.add(objectMapper.readValue(jsonStrs[3], ShardStateCollector.ShardStateMetrics.class));
        return list;
    }
}
