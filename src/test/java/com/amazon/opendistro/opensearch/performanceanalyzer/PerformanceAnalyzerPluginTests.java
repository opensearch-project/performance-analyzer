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

package com.amazon.opendistro.opensearch.performanceanalyzer;

import static org.mockito.MockitoAnnotations.initMocks;

import com.amazon.opendistro.opensearch.performanceanalyzer.action.PerformanceAnalyzerActionFilter;
import com.amazon.opendistro.opensearch.performanceanalyzer.config.setting.PerformanceAnalyzerClusterSettings;
import com.amazon.opendistro.opensearch.performanceanalyzer.http_action.config.PerformanceAnalyzerClusterConfigAction;
import com.amazon.opendistro.opensearch.performanceanalyzer.http_action.config.PerformanceAnalyzerConfigAction;
import com.amazon.opendistro.opensearch.performanceanalyzer.http_action.config.PerformanceAnalyzerOverridesClusterConfigAction;
import com.amazon.opendistro.opensearch.performanceanalyzer.http_action.config.PerformanceAnalyzerResourceProvider;
import com.amazon.opendistro.opensearch.performanceanalyzer.transport.PerformanceAnalyzerTransportInterceptor;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope.Scope;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionResponse;
import org.opensearch.action.support.ActionFilter;
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.env.Environment;
import org.opensearch.env.TestEnvironment;
import org.opensearch.indices.breaker.BreakerSettings;
import org.opensearch.indices.breaker.CircuitBreakerService;
import org.opensearch.indices.breaker.HierarchyCircuitBreakerService;
import org.opensearch.plugins.ActionPlugin.ActionHandler;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestHandler;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.Transport;
import org.opensearch.transport.TransportInterceptor;
import org.opensearch.usage.UsageService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@ThreadLeakScope(Scope.NONE)
public class PerformanceAnalyzerPluginTests extends OpenSearchTestCase {
    private PerformanceAnalyzerPlugin plugin;
    private Settings settings;
    private RestController restController;
    private ThreadPool threadPool;
    private NodeClient nodeClient;
    private Environment environment;
    private CircuitBreakerService circuitBreakerService;
    private ClusterService clusterService;
    private ClusterSettings clusterSettings;

    @Before
    public void setup() {
        initMocks(this);

        settings = Settings.builder().put("path.home", "./").build();
        plugin = new PerformanceAnalyzerPlugin(settings, Paths.get("build/tmp/junit_metrics"));
        clusterSettings = new ClusterSettings(settings, ClusterSettings.BUILT_IN_CLUSTER_SETTINGS);
        circuitBreakerService =
                new HierarchyCircuitBreakerService(
                        settings, new ArrayList<BreakerSettings>(), clusterSettings);
        UsageService usageService = new UsageService();
        threadPool = new TestThreadPool("test");
        nodeClient = new NodeClient(settings, threadPool);
        environment = TestEnvironment.newEnvironment(settings);
        clusterService = new ClusterService(settings, clusterSettings, threadPool);
        restController =
                new RestController(
                        Collections.emptySet(),
                        null,
                        nodeClient,
                        circuitBreakerService,
                        usageService);
    }

    @After
    public void tearDown() throws Exception {
        threadPool.shutdownNow();
        super.tearDown();
    }

    @Test
    public void testGetActionFilters() {
        List<ActionFilter> list = plugin.getActionFilters();
        assertEquals(1, list.size());
        assertEquals(PerformanceAnalyzerActionFilter.class, list.get(0).getClass());
    }

    @Test
    public void testGetActions() {
        List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> list =
                plugin.getActions();
        assertEquals(1, list.size());
        assertEquals(ActionHandler.class, list.get(0).getClass());
    }

    @Test
    public void testGetTransportInterceptors() {
        List<TransportInterceptor> list = plugin.getTransportInterceptors(null, null);
        assertEquals(1, list.size());
        assertEquals(PerformanceAnalyzerTransportInterceptor.class, list.get(0).getClass());
    }

    @Test
    public void testGetRestHandlers() {
        List<RestHandler> handlers =
                plugin.getRestHandlers(settings, restController, null, null, null, null, null);
        assertEquals(4, handlers.size());
        assertEquals(PerformanceAnalyzerConfigAction.class, handlers.get(0).getClass());
        assertEquals(PerformanceAnalyzerClusterConfigAction.class, handlers.get(1).getClass());
        assertEquals(PerformanceAnalyzerResourceProvider.class, handlers.get(2).getClass());
        assertEquals(
                PerformanceAnalyzerOverridesClusterConfigAction.class, handlers.get(3).getClass());
    }

    @Test
    public void testCreateComponents() {
        Collection<Object> components =
                plugin.createComponents(
                        nodeClient,
                        clusterService,
                        threadPool,
                        null,
                        null,
                        null,
                        environment,
                        null,
                        null,
                        null,
                        null);
        assertEquals(1, components.size());
        assertEquals(settings, OpenSearchResources.INSTANCE.getSettings());
        assertEquals(threadPool, OpenSearchResources.INSTANCE.getThreadPool());
        assertEquals(environment, OpenSearchResources.INSTANCE.getEnvironment());
        assertEquals(nodeClient, OpenSearchResources.INSTANCE.getClient());
    }

    @Test
    public void testGetTransports() {
        Map<String, Supplier<Transport>> map =
                plugin.getTransports(settings, threadPool, null, circuitBreakerService, null, null);
        assertEquals(0, map.size());
        assertEquals(settings, OpenSearchResources.INSTANCE.getSettings());
        assertEquals(circuitBreakerService, OpenSearchResources.INSTANCE.getCircuitBreakerService());
    }

    @Test
    public void testGetSettings() {
        List<Setting<?>> list = plugin.getSettings();
        assertEquals(3, list.size());
        assertEquals(PerformanceAnalyzerClusterSettings.COMPOSITE_PA_SETTING, list.get(0));
        assertEquals(PerformanceAnalyzerClusterSettings.PA_NODE_STATS_SETTING, list.get(1));
        assertEquals(PerformanceAnalyzerClusterSettings.CONFIG_OVERRIDES_SETTING, list.get(2));
    }
}
