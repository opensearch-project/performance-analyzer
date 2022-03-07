/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.http_action.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.indices.breaker.BreakerSettings;
import org.opensearch.indices.breaker.CircuitBreakerService;
import org.opensearch.indices.breaker.HierarchyCircuitBreakerService;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.config.setting.ClusterSettingsManager;
import org.opensearch.performanceanalyzer.config.setting.handler.NodeStatsSettingHandler;
import org.opensearch.performanceanalyzer.config.setting.handler.PerformanceAnalyzerClusterSettingHandler;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestStatus;
import org.opensearch.test.rest.FakeRestChannel;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.usage.UsageService;

public class PerformanceAnalyzerClusterConfigActionTests {
    private PerformanceAnalyzerClusterConfigAction configAction;
    private RestController restController;
    private ThreadPool threadPool;
    private NodeClient nodeClient;
    private CircuitBreakerService circuitBreakerService;
    private ClusterSettings clusterSettings;
    private PerformanceAnalyzerClusterSettingHandler clusterSettingHandler;
    private NodeStatsSettingHandler nodeStatsSettingHandler;

    @Mock private PerformanceAnalyzerController controller;
    @Mock private ClusterSettingsManager clusterSettingsManager;

    @Before
    public void init() {
        initMocks(this);

        clusterSettings =
                new ClusterSettings(Settings.EMPTY, ClusterSettings.BUILT_IN_CLUSTER_SETTINGS);
        circuitBreakerService =
                new HierarchyCircuitBreakerService(
                        Settings.EMPTY, new ArrayList<BreakerSettings>(), clusterSettings);
        UsageService usageService = new UsageService();
        threadPool = new TestThreadPool("test");
        nodeClient = new NodeClient(Settings.EMPTY, threadPool);
        restController =
                new RestController(
                        Collections.emptySet(),
                        null,
                        nodeClient,
                        circuitBreakerService,
                        usageService);
        clusterSettingHandler =
                new PerformanceAnalyzerClusterSettingHandler(controller, clusterSettingsManager);
        nodeStatsSettingHandler = new NodeStatsSettingHandler(controller, clusterSettingsManager);
        configAction =
                new PerformanceAnalyzerClusterConfigAction(
                        Settings.EMPTY,
                        restController,
                        clusterSettingHandler,
                        nodeStatsSettingHandler);
        restController.registerHandler(configAction);
    }

    @After
    public void tearDown() throws Exception {
        threadPool.shutdownNow();
    }

    @Test
    public void testRoutes() {
        assertEquals(8, configAction.replacedRoutes().size());
    }

    @Test
    public void testGetName() {
        assertEquals(
                PerformanceAnalyzerClusterConfigAction.class.getSimpleName(),
                configAction.getName());
    }

    @Test
    public void testUpdateRcaSetting() throws IOException {
        testWithRequestPath(PerformanceAnalyzerClusterConfigAction.RCA_CLUSTER_CONFIG_PATH);
    }

    @Test
    public void testLegacyUpdateRcaSetting() throws IOException {
        testWithRequestPath(PerformanceAnalyzerClusterConfigAction.LEGACY_RCA_CLUSTER_CONFIG_PATH);
    }

    @Test
    public void testUpdateLoggingSetting() throws IOException {
        testWithRequestPath(PerformanceAnalyzerClusterConfigAction.LOGGING_CLUSTER_CONFIG_PATH);
    }

    @Test
    public void testLegacyUpdateLoggingSetting() throws IOException {
        testWithRequestPath(
                PerformanceAnalyzerClusterConfigAction.LEGACY_LOGGING_CLUSTER_CONFIG_PATH);
    }

    @Test
    public void testUpdateBatchMetricsSetting() throws IOException {
        testWithRequestPath(
                PerformanceAnalyzerClusterConfigAction.BATCH_METRICS_CLUSTER_CONFIG_PATH);
    }

    @Test
    public void testLegacyUpdateBatchMetricsSetting() throws IOException {
        testWithRequestPath(
                PerformanceAnalyzerClusterConfigAction.LEGACY_BATCH_METRICS_CLUSTER_CONFIG_PATH);
    }

    @Test
    public void testUpdatePerformanceAnalyzerSetting() throws IOException {
        testWithRequestPath(PerformanceAnalyzerClusterConfigAction.PA_CLUSTER_CONFIG_PATH);
    }

    @Test
    public void testLegacyUpdatePerformanceAnalyzerSetting() throws IOException {
        testWithRequestPath(PerformanceAnalyzerClusterConfigAction.LEGACY_PA_CLUSTER_CONFIG_PATH);
    }

    private void testWithRequestPath(String requestPath) throws IOException {
        final FakeRestRequest fakeRestRequest = buildRequest(requestPath);
        final FakeRestChannel channel = new FakeRestChannel(fakeRestRequest, true, 10);
        restController.dispatchRequest(fakeRestRequest, channel, new ThreadContext(Settings.EMPTY));
        assertEquals(RestStatus.OK, channel.capturedResponse().status());

        String responseStr = channel.capturedResponse().content().utf8ToString();
        assertTrue(responseStr.contains(PerformanceAnalyzerClusterConfigAction.CURRENT));
        assertTrue(
                responseStr.contains(PerformanceAnalyzerClusterConfigAction.SHARDS_PER_COLLECTION));
        assertTrue(
                responseStr.contains(
                        PerformanceAnalyzerClusterConfigAction
                                .BATCH_METRICS_RETENTION_PERIOD_MINUTES));
    }

    private FakeRestRequest buildRequest(String requestPath) throws IOException {
        final XContentBuilder builder =
                XContentFactory.jsonBuilder()
                        .startObject()
                        .field(PerformanceAnalyzerClusterConfigAction.ENABLED, true)
                        .field(PerformanceAnalyzerClusterConfigAction.SHARDS_PER_COLLECTION, 1)
                        .endObject();

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
                .withMethod(RestRequest.Method.POST)
                .withPath(requestPath)
                .withContent(BytesReference.bytes(builder), builder.contentType())
                .build();
    }
}
