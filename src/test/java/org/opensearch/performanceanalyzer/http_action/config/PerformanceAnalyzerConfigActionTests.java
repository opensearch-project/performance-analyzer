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
import org.mockito.Mockito;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.common.bytes.BytesReference;
import org.opensearch.core.indices.breaker.CircuitBreakerService;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.indices.breaker.BreakerSettings;
import org.opensearch.indices.breaker.HierarchyCircuitBreakerService;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.rest.FakeRestChannel;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.client.node.NodeClient;
import org.opensearch.usage.UsageService;

public class PerformanceAnalyzerConfigActionTests {
    private PerformanceAnalyzerConfigAction configAction;
    private RestController restController;
    private ThreadPool threadPool;
    private NodeClient nodeClient;
    private CircuitBreakerService circuitBreakerService;
    private ClusterSettings clusterSettings;

    @Mock private PerformanceAnalyzerController controller;

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
        configAction = new PerformanceAnalyzerConfigAction(restController, controller);
        restController.registerHandler(configAction);

        PerformanceAnalyzerConfigAction.setInstance(configAction);
        assertEquals(configAction, PerformanceAnalyzerConfigAction.getInstance());
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
                PerformanceAnalyzerConfigAction.PERFORMANCE_ANALYZER_CONFIG_ACTION,
                configAction.getName());
    }

    @Test
    public void testUpdateRcaState_ShouldEnable_paEnabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.RCA_CONFIG_PATH, true, true);
    }

    @Test
    public void testLegacyUpdateRcaState_ShouldEnable_paEnabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.LEGACY_RCA_CONFIG_PATH, true, true);
    }

    @Test
    public void testUpdateRcaState_ShouldEnable_paDisabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.RCA_CONFIG_PATH, true, false);
    }

    @Test
    public void testLegacyUpdateRcaState_ShouldEnable_paDisabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.LEGACY_RCA_CONFIG_PATH, true, false);
    }

    @Test
    public void testUpdateRcaState_ShouldDisable_paEnabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.RCA_CONFIG_PATH, false, true);
    }

    @Test
    public void testLegacyUpdateRcaState_ShouldDisable_paEnabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.LEGACY_RCA_CONFIG_PATH, false, true);
    }

    @Test
    public void testUpdateRcaState_ShouldDisable_paDisabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.RCA_CONFIG_PATH, false, false);
    }

    @Test
    public void testLegacyUpdateRcaState_ShouldDisable_paDisabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.LEGACY_RCA_CONFIG_PATH, false, false);
    }

    @Test
    public void testUpdateLoggingState_ShouldEnable_paEnabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.LOGGING_CONFIG_PATH, true, true);
    }

    @Test
    public void testLegacyUpdateLoggingState_ShouldEnable_paEnabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.LEGACY_LOGGING_CONFIG_PATH, true, true);
    }

    @Test
    public void testUpdateLoggingState_ShouldEnable_paDisabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.LOGGING_CONFIG_PATH, true, false);
    }

    @Test
    public void testLegacyUpdateLoggingState_ShouldEnable_paDisabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.LEGACY_LOGGING_CONFIG_PATH, true, false);
    }

    @Test
    public void testUpdateLoggingState_ShouldDisable_paEnabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.LOGGING_CONFIG_PATH, false, true);
    }

    @Test
    public void testLegacyUpdateLoggingState_ShouldDisable_paEnabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.LEGACY_LOGGING_CONFIG_PATH, false, true);
    }

    @Test
    public void testUpdateLoggingState_ShouldDisable_paDisabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.LOGGING_CONFIG_PATH, false, false);
    }

    @Test
    public void testLegacyUpdateLoggingState_ShouldDisable_paDisabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.LEGACY_LOGGING_CONFIG_PATH, false, false);
    }

    @Test
    public void testUpdateBatchMetricsState_ShouldEnable_paEnabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.BATCH_METRICS_CONFIG_PATH, true, true);
    }

    @Test
    public void testLegacyUpdateBatchMetricsState_ShouldEnable_paEnabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.LEGACY_BATCH_METRICS_CONFIG_PATH, true, true);
    }

    @Test
    public void testUpdateBatchMetricsState_ShouldEnable_paDisabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.BATCH_METRICS_CONFIG_PATH, true, false);
    }

    @Test
    public void testLegacyUpdateBatchMetricsState_ShouldEnable_paDisabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.LEGACY_BATCH_METRICS_CONFIG_PATH, true, false);
    }

    @Test
    public void testUpdateBatchMetricsState_ShouldDisable_paEnabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.BATCH_METRICS_CONFIG_PATH, false, true);
    }

    @Test
    public void testLegacyUpdateBatchMetricsState_ShouldDisable_paEnabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.LEGACY_BATCH_METRICS_CONFIG_PATH, false, true);
    }

    @Test
    public void testUpdateBatchMetricsState_ShouldDisable_paDisabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.BATCH_METRICS_CONFIG_PATH, false, false);
    }

    @Test
    public void testLegacyUpdateBatchMetricsState_ShouldDisable_paDisabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.LEGACY_BATCH_METRICS_CONFIG_PATH, false, false);
    }

    @Test
    public void testUpdatePerformanceAnalyzerState_ShouldEnable_paEnabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.PA_CONFIG_PATH, true, true);
    }

    @Test
    public void testLegacyUpdatePerformanceAnalyzerState_ShouldEnable_paEnabled()
            throws IOException {
        test(PerformanceAnalyzerConfigAction.LEGACY_PA_CONFIG_PATH, true, true);
    }

    @Test
    public void testUpdatePerformanceAnalyzerState_ShouldDisable_paEnabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.PA_CONFIG_PATH, false, true);
    }

    @Test
    public void testLegacyUpdatePerformanceAnalyzerState_ShouldDisable_paEnabled()
            throws IOException {
        test(PerformanceAnalyzerConfigAction.LEGACY_PA_CONFIG_PATH, false, true);
    }

    @Test
    public void testUpdatePerformanceAnalyzerState_ShouldDisable_paDisabled() throws IOException {
        test(PerformanceAnalyzerConfigAction.PA_CONFIG_PATH, false, false);
    }

    @Test
    public void testLegacyUpdatePerformanceAnalyzerState_ShouldDisable_paDisabled()
            throws IOException {
        test(PerformanceAnalyzerConfigAction.LEGACY_PA_CONFIG_PATH, false, false);
    }

    @Test
    public void testUpdateThreadContentionMonitoringState_ShouldEnable_paEnabled()
            throws IOException {
        test(PerformanceAnalyzerConfigAction.THREAD_CONTENTION_MONITORING_CONFIG_PATH, true, true);
    }

    @Test
    public void testUpdateThreadContentionMonitoringState_ShouldEnable_paDisabled()
            throws IOException {
        test(PerformanceAnalyzerConfigAction.THREAD_CONTENTION_MONITORING_CONFIG_PATH, true, false);
    }

    @Test
    public void testUpdateThreadContentionMonitoringState_ShouldDisable_paDisabled()
            throws IOException {
        test(
                PerformanceAnalyzerConfigAction.THREAD_CONTENTION_MONITORING_CONFIG_PATH,
                false,
                false);
    }

    @Test
    public void testUpdateThreadContentionMonitoringState_ShouldDisable_paEnabled()
            throws IOException {
        test(PerformanceAnalyzerConfigAction.THREAD_CONTENTION_MONITORING_CONFIG_PATH, false, true);
    }

    private void test(String requestPath, boolean shouldEnable, boolean paEnabled)
            throws IOException {
        final FakeRestRequest fakeRestRequest = buildRequest(requestPath, shouldEnable);
        final FakeRestChannel channel = new FakeRestChannel(fakeRestRequest, true, 10);
        Mockito.when(controller.isPerformanceAnalyzerEnabled()).thenReturn(paEnabled);
        restController.dispatchRequest(fakeRestRequest, channel, new ThreadContext(Settings.EMPTY));
        boolean testWithError = shouldEnable && !paEnabled;
        if (testWithError) {
            assertEquals(RestStatus.BAD_REQUEST, channel.capturedResponse().status());
        } else {
            assertEquals(RestStatus.OK, channel.capturedResponse().status());
            String responseStr = channel.capturedResponse().content().utf8ToString();
            assertTrue(responseStr.contains(PerformanceAnalyzerConfigAction.PA_ENABLED));
            assertTrue(responseStr.contains(PerformanceAnalyzerConfigAction.RCA_ENABLED));
            assertTrue(responseStr.contains(PerformanceAnalyzerConfigAction.PA_LOGGING_ENABLED));
            assertTrue(responseStr.contains(PerformanceAnalyzerConfigAction.SHARDS_PER_COLLECTION));
            assertTrue(responseStr.contains(PerformanceAnalyzerConfigAction.BATCH_METRICS_ENABLED));
            assertTrue(
                    responseStr.contains(
                            PerformanceAnalyzerConfigAction.THREAD_CONTENTION_MONITORING_ENABLED));
            assertTrue(
                    responseStr.contains(
                            PerformanceAnalyzerConfigAction
                                    .BATCH_METRICS_RETENTION_PERIOD_MINUTES));
        }
    }

    private FakeRestRequest buildRequest(String requestPath, boolean shouldEnable)
            throws IOException {
        final XContentBuilder builder =
                XContentFactory.jsonBuilder()
                        .startObject()
                        .field(PerformanceAnalyzerConfigAction.ENABLED, shouldEnable)
                        .field(PerformanceAnalyzerConfigAction.SHARDS_PER_COLLECTION, 1)
                        .endObject();

        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
                .withMethod(RestRequest.Method.POST)
                .withPath(requestPath)
                .withContent(BytesReference.bytes(builder), (XContentType) builder.contentType())
                .build();
    }
}
