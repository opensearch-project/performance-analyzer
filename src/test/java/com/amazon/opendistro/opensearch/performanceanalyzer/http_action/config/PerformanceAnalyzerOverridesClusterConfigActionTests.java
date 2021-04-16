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

package com.amazon.opendistro.opensearch.performanceanalyzer.http_action.config;

import static com.amazon.opendistro.opensearch.performanceanalyzer.http_action.config.PerformanceAnalyzerOverridesClusterConfigAction.PA_CONFIG_OVERRIDES_PATH;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.MockitoAnnotations.initMocks;

import com.amazon.opendistro.opensearch.performanceanalyzer.config.ConfigOverridesTestHelper;
import com.amazon.opendistro.opensearch.performanceanalyzer.config.overrides.ConfigOverridesWrapper;
import com.amazon.opendistro.opensearch.performanceanalyzer.config.setting.handler.ConfigOverridesClusterSettingHandler;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.indices.breaker.BreakerSettings;
import org.opensearch.indices.breaker.CircuitBreakerService;
import org.opensearch.indices.breaker.HierarchyCircuitBreakerService;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestHandler.Route;
import org.opensearch.rest.RestRequest.Method;
import org.opensearch.rest.RestStatus;
import org.opensearch.test.rest.FakeRestChannel;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.usage.UsageService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

public class PerformanceAnalyzerOverridesClusterConfigActionTests {
    private PerformanceAnalyzerOverridesClusterConfigAction configAction;
    private RestController restController;
    private ThreadPool threadPool;
    private NodeClient nodeClient;
    private CircuitBreakerService circuitBreakerService;
    private ClusterSettings clusterSettings;

    @Mock private ConfigOverridesClusterSettingHandler configOverridesClusterSettingHandler;
    @Mock private ConfigOverridesWrapper overridesWrapper;

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
        configAction =
                new PerformanceAnalyzerOverridesClusterConfigAction(
                        Settings.EMPTY,
                        restController,
                        configOverridesClusterSettingHandler,
                        overridesWrapper);
        restController.registerHandler(configAction);
    }

    @After
    public void tearDown() throws Exception {
        threadPool.shutdownNow();
    }

    @Test
    public void testRoutes() {
        List<Route> routes = configAction.routes();
        assertEquals(2, routes.size());
    }

    @Test
    public void testGetName() {
        assertEquals(
                PerformanceAnalyzerOverridesClusterConfigAction.class.getSimpleName(),
                configAction.getName());
    }

    @Test
    public void testWithGetMethod() throws IOException {
        final FakeRestRequest fakeRestRequest =
                buildRequest(
                        PA_CONFIG_OVERRIDES_PATH,
                        Method.GET,
                        ConfigOverridesTestHelper.getValidConfigOverridesJson());
        final FakeRestChannel channel = new FakeRestChannel(fakeRestRequest, true, 10);
        restController.dispatchRequest(fakeRestRequest, channel, new ThreadContext(Settings.EMPTY));
        assertEquals(RestStatus.OK, channel.capturedResponse().status());
    }

    @Test
    public void testWithPostMethod() throws IOException {
        final FakeRestRequest fakeRestRequest =
                buildRequest(
                        PA_CONFIG_OVERRIDES_PATH,
                        Method.POST,
                        ConfigOverridesTestHelper.getValidConfigOverridesJson());
        final FakeRestChannel channel = new FakeRestChannel(fakeRestRequest, true, 10);
        restController.dispatchRequest(fakeRestRequest, channel, new ThreadContext(Settings.EMPTY));
        assertEquals(RestStatus.OK, channel.capturedResponse().status());
        String responseStr = channel.capturedResponse().content().utf8ToString();
        assertTrue(
                responseStr.contains(
                        PerformanceAnalyzerOverridesClusterConfigAction.OVERRIDE_TRIGGERED_FIELD));
    }

    @Test
    public void testWithUnsupportedMethod() throws IOException {
        final FakeRestRequest fakeRestRequest =
                buildRequest(
                        PA_CONFIG_OVERRIDES_PATH,
                        Method.PUT,
                        ConfigOverridesTestHelper.getValidConfigOverridesJson());
        final FakeRestChannel channel = new FakeRestChannel(fakeRestRequest, true, 10);
        restController.dispatchRequest(fakeRestRequest, channel, new ThreadContext(Settings.EMPTY));
        assertEquals(RestStatus.METHOD_NOT_ALLOWED, channel.capturedResponse().status());
    }

    @Test
    public void testWithInvalidOverrides() throws IOException {
        final FakeRestRequest fakeRestRequest =
                buildRequest(
                        PA_CONFIG_OVERRIDES_PATH,
                        Method.POST,
                        ConfigOverridesTestHelper.getInvalidConfigOverridesJson());
        final FakeRestChannel channel = new FakeRestChannel(fakeRestRequest, true, 10);
        restController.dispatchRequest(fakeRestRequest, channel, new ThreadContext(Settings.EMPTY));
        assertEquals(RestStatus.BAD_REQUEST, channel.capturedResponse().status());
    }

    private FakeRestRequest buildRequest(
            String requestPath, Method requestMethod, String configOverridesJson)
            throws IOException {
        ByteBuffer byteBuffer = ByteBuffer.wrap(configOverridesJson.getBytes());
        return new FakeRestRequest.Builder(NamedXContentRegistry.EMPTY)
                .withMethod(requestMethod)
                .withPath(requestPath)
                .withContent(BytesReference.fromByteBuffer(byteBuffer), XContentType.JSON)
                .build();
    }
}
