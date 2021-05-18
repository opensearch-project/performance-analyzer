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
 * Copyright 2020-2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

import static org.mockito.MockitoAnnotations.initMocks;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paranamer.ParanamerModule;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opensearch.Version;
import org.opensearch.cluster.node.DiscoveryNode;
import org.opensearch.cluster.node.DiscoveryNodeRole;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.config.PluginSettings;
import org.opensearch.performanceanalyzer.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.NodeRole;
import org.opensearch.performanceanalyzer.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.reader_writer_shared.Event;
import org.opensearch.performanceanalyzer.util.TestUtil;
import org.opensearch.test.ClusterServiceUtils;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class NodeDetailsCollectorTests extends OpenSearchTestCase {
    private static final String NODE_ID = "testNode";
    private NodeDetailsCollector collector;
    private ThreadPool threadPool;
    private long startTimeInMills = 1153721339;

    @Mock private ConfigOverridesWrapper configOverrides;

    @Before
    public void init() {
        initMocks(this);

        DiscoveryNode testNode =
                new DiscoveryNode(
                        NODE_ID,
                        OpenSearchTestCase.buildNewFakeTransportAddress(),
                        Collections.emptyMap(),
                        DiscoveryNodeRole.BUILT_IN_ROLES,
                        Version.CURRENT);

        threadPool = new TestThreadPool("test");
        ClusterService clusterService =
                ClusterServiceUtils.createClusterService(threadPool, testNode);
        OpenSearchResources.INSTANCE.setClusterService(clusterService);

        MetricsConfiguration.CONFIG_MAP.put(
                NodeDetailsCollector.class, MetricsConfiguration.cdefault);
        collector = new NodeDetailsCollector(configOverrides);

        // clean metricQueue before running every test
        TestUtil.readEvents();
    }

    @After
    public void tearDown() throws Exception {
        threadPool.shutdownNow();
        super.tearDown();
    }

    @Test
    public void testGetMetricsPath() {
        String expectedPath =
                PluginSettings.instance().getMetricsLocation()
                        + PerformanceAnalyzerMetrics.getTimeInterval(startTimeInMills)
                        + "/"
                        + PerformanceAnalyzerMetrics.sNodesPath;
        String actualPath = collector.getMetricsPath(startTimeInMills);
        assertEquals(expectedPath, actualPath);

        try {
            collector.getMetricsPath(startTimeInMills, "nodesPath");
            fail("Negative scenario test: Should have been a RuntimeException");
        } catch (RuntimeException ex) {
            // - expecting exception...1 values passed; 0 expected
        }
    }

    @Test
    public void testCollectMetrics() throws IOException {
        long startTimeInMills = 1153721339;
        collector.collectMetrics(startTimeInMills);
        NodeDetailsCollector.NodeDetailsStatus nodeDetailsStatus = readMetrics();

        assertEquals(NODE_ID, nodeDetailsStatus.getID());
        assertEquals("0.0.0.0", nodeDetailsStatus.getHostAddress());
        assertEquals(NodeRole.DATA.role(), nodeDetailsStatus.getRole());
        assertTrue(nodeDetailsStatus.getIsMasterNode());
    }

    private NodeDetailsCollector.NodeDetailsStatus readMetrics() throws IOException {
        List<Event> metrics = TestUtil.readEvents();
        assert metrics.size() == 1;
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new ParanamerModule());
        String[] jsonStrs = metrics.get(0).value.split("\n");
        assert jsonStrs.length == 4;
        return objectMapper.readValue(jsonStrs[3], NodeDetailsCollector.NodeDetailsStatus.class);
    }
}
