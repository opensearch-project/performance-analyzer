/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer;

import static org.mockito.MockitoAnnotations.initMocks;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope.Scope;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilter;
import org.opensearch.client.node.NodeClient;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.indices.breaker.CircuitBreakerService;
import org.opensearch.env.Environment;
import org.opensearch.env.TestEnvironment;
import org.opensearch.identity.IdentityService;
import org.opensearch.indices.breaker.BreakerSettings;
import org.opensearch.indices.breaker.HierarchyCircuitBreakerService;
import org.opensearch.performanceanalyzer.action.PerformanceAnalyzerActionFilter;
import org.opensearch.performanceanalyzer.config.setting.PerformanceAnalyzerClusterSettings;
import org.opensearch.performanceanalyzer.http_action.config.PerformanceAnalyzerClusterConfigAction;
import org.opensearch.performanceanalyzer.http_action.config.PerformanceAnalyzerConfigAction;
import org.opensearch.performanceanalyzer.http_action.config.PerformanceAnalyzerOverridesClusterConfigAction;
import org.opensearch.performanceanalyzer.http_action.config.PerformanceAnalyzerResourceProvider;
import org.opensearch.performanceanalyzer.transport.PerformanceAnalyzerTransportInterceptor;
import org.opensearch.plugins.ActionPlugin.ActionHandler;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestHandler;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.NoopMetricsRegistryFactory;
import org.opensearch.telemetry.tracing.noop.NoopTracer;
import org.opensearch.test.OpenSearchTestCase;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.Transport;
import org.opensearch.transport.TransportInterceptor;
import org.opensearch.usage.UsageService;

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
    private IdentityService identityService;

    private MetricsRegistry metricsRegistry;

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
        NoopMetricsRegistryFactory metricsRegistryFactory = new NoopMetricsRegistryFactory();
        metricsRegistry = metricsRegistryFactory.getMetricsRegistry();
        try {
            metricsRegistryFactory.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        identityService = new IdentityService(Settings.EMPTY, List.of());
        restController =
                new RestController(
                        Collections.emptySet(),
                        null,
                        nodeClient,
                        circuitBreakerService,
                        usageService,
                        identityService);
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
                        null,
                        null,
                        metricsRegistry);
        assertEquals(1, components.size());
        assertEquals(settings, OpenSearchResources.INSTANCE.getSettings());
        assertEquals(threadPool, OpenSearchResources.INSTANCE.getThreadPool());
        assertEquals(environment, OpenSearchResources.INSTANCE.getEnvironment());
        assertEquals(nodeClient, OpenSearchResources.INSTANCE.getClient());
        assertEquals(metricsRegistry, OpenSearchResources.INSTANCE.getMetricsRegistry());
    }

    @Test
    public void testGetTransports() {
        Map<String, Supplier<Transport>> map =
                plugin.getTransports(
                        settings,
                        threadPool,
                        null,
                        circuitBreakerService,
                        null,
                        null,
                        NoopTracer.INSTANCE);
        assertEquals(0, map.size());
        assertEquals(settings, OpenSearchResources.INSTANCE.getSettings());
        assertEquals(
                circuitBreakerService, OpenSearchResources.INSTANCE.getCircuitBreakerService());
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
