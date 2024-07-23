/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer;

import static java.util.Collections.singletonList;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.opensearch.SpecialPermission;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.support.ActionFilter;
import org.opensearch.client.Client;
import org.opensearch.cluster.metadata.IndexNameExpressionResolver;
import org.opensearch.cluster.node.DiscoveryNodes;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.network.NetworkService;
import org.opensearch.common.settings.ClusterSettings;
import org.opensearch.common.settings.IndexScopedSettings;
import org.opensearch.common.settings.Setting;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.settings.SettingsFilter;
import org.opensearch.common.util.PageCacheRecycler;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.NamedWriteableRegistry;
import org.opensearch.core.indices.breaker.CircuitBreakerService;
import org.opensearch.core.xcontent.NamedXContentRegistry;
import org.opensearch.discovery.Discovery;
import org.opensearch.env.Environment;
import org.opensearch.env.NodeEnvironment;
import org.opensearch.index.IndexModule;
import org.opensearch.performanceanalyzer.action.PerformanceAnalyzerActionFilter;
import org.opensearch.performanceanalyzer.collectors.AdmissionControlMetricsCollector;
import org.opensearch.performanceanalyzer.collectors.CacheConfigMetricsCollector;
import org.opensearch.performanceanalyzer.collectors.CircuitBreakerCollector;
import org.opensearch.performanceanalyzer.collectors.ClusterApplierServiceStatsCollector;
import org.opensearch.performanceanalyzer.collectors.ClusterManagerServiceEventMetrics;
import org.opensearch.performanceanalyzer.collectors.ClusterManagerServiceMetrics;
import org.opensearch.performanceanalyzer.collectors.ElectionTermCollector;
import org.opensearch.performanceanalyzer.collectors.FaultDetectionMetricsCollector;
import org.opensearch.performanceanalyzer.collectors.NodeDetailsCollector;
import org.opensearch.performanceanalyzer.collectors.NodeStatsAllShardsMetricsCollector;
import org.opensearch.performanceanalyzer.collectors.SearchBackPressureStatsCollector;
import org.opensearch.performanceanalyzer.collectors.ShardIndexingPressureMetricsCollector;
import org.opensearch.performanceanalyzer.collectors.ShardStateCollector;
import org.opensearch.performanceanalyzer.collectors.ThreadPoolMetricsCollector;
import org.opensearch.performanceanalyzer.collectors.telemetry.RTFCacheConfigMetricsCollector;
import org.opensearch.performanceanalyzer.collectors.telemetry.RTFDisksCollector;
import org.opensearch.performanceanalyzer.collectors.telemetry.RTFHeapMetricsCollector;
import org.opensearch.performanceanalyzer.collectors.telemetry.RTFNodeStatsAllShardsMetricsCollector;
import org.opensearch.performanceanalyzer.collectors.telemetry.RTFThreadPoolMetricsCollector;
import org.opensearch.performanceanalyzer.commons.OSMetricsGeneratorFactory;
import org.opensearch.performanceanalyzer.commons.collectors.DisksCollector;
import org.opensearch.performanceanalyzer.commons.collectors.GCInfoCollector;
import org.opensearch.performanceanalyzer.commons.collectors.HeapMetricsCollector;
import org.opensearch.performanceanalyzer.commons.collectors.NetworkInterfaceCollector;
import org.opensearch.performanceanalyzer.commons.collectors.OSMetricsCollector;
import org.opensearch.performanceanalyzer.commons.collectors.ScheduledMetricCollectorsExecutor;
import org.opensearch.performanceanalyzer.commons.collectors.StatsCollector;
import org.opensearch.performanceanalyzer.commons.config.PluginSettings;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.event_process.EventLog;
import org.opensearch.performanceanalyzer.commons.event_process.EventLogFileHandler;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.config.setting.ClusterSettingsManager;
import org.opensearch.performanceanalyzer.config.setting.PerformanceAnalyzerClusterSettings;
import org.opensearch.performanceanalyzer.config.setting.handler.ConfigOverridesClusterSettingHandler;
import org.opensearch.performanceanalyzer.config.setting.handler.NodeStatsSettingHandler;
import org.opensearch.performanceanalyzer.config.setting.handler.PerformanceAnalyzerClusterSettingHandler;
import org.opensearch.performanceanalyzer.config.setting.handler.PerformanceAnalyzerCollectorsSettingHandler;
import org.opensearch.performanceanalyzer.http_action.config.PerformanceAnalyzerClusterConfigAction;
import org.opensearch.performanceanalyzer.http_action.config.PerformanceAnalyzerConfigAction;
import org.opensearch.performanceanalyzer.http_action.config.PerformanceAnalyzerOverridesClusterConfigAction;
import org.opensearch.performanceanalyzer.http_action.config.PerformanceAnalyzerResourceProvider;
import org.opensearch.performanceanalyzer.http_action.whoami.TransportWhoAmIAction;
import org.opensearch.performanceanalyzer.http_action.whoami.WhoAmIAction;
import org.opensearch.performanceanalyzer.listener.PerformanceAnalyzerSearchListener;
import org.opensearch.performanceanalyzer.listener.RTFPerformanceAnalyzerSearchListener;
import org.opensearch.performanceanalyzer.transport.PerformanceAnalyzerTransportInterceptor;
import org.opensearch.performanceanalyzer.transport.RTFPerformanceAnalyzerTransportInterceptor;
import org.opensearch.performanceanalyzer.util.Utils;
import org.opensearch.performanceanalyzer.writer.EventLogQueueProcessor;
import org.opensearch.plugins.ActionPlugin;
import org.opensearch.plugins.NetworkPlugin;
import org.opensearch.plugins.Plugin;
import org.opensearch.plugins.SearchPlugin;
import org.opensearch.plugins.TelemetryAwarePlugin;
import org.opensearch.repositories.RepositoriesService;
import org.opensearch.rest.RestController;
import org.opensearch.script.ScriptService;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.tracing.Tracer;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.transport.Transport;
import org.opensearch.transport.TransportInterceptor;
import org.opensearch.watcher.ResourceWatcherService;

public final class PerformanceAnalyzerPlugin extends Plugin
        implements ActionPlugin, NetworkPlugin, SearchPlugin, TelemetryAwarePlugin {
    private static final Logger LOG = LogManager.getLogger(PerformanceAnalyzerPlugin.class);
    public static final String PLUGIN_NAME = "opensearch-performance-analyzer";
    private static final String ADD_FAULT_DETECTION_METHOD = "addFaultDetectionListener";
    private static final String LISTENER_INJECTOR_CLASS_PATH =
            "org.opensearch.performanceanalyzer.listener.ListenerInjector";
    public static final int QUEUE_PURGE_INTERVAL_MS = 1000;
    private static SecurityManager sm = null;
    private final PerformanceAnalyzerClusterSettingHandler perfAnalyzerClusterSettingHandler;
    private final NodeStatsSettingHandler nodeStatsSettingHandler;
    private final PerformanceAnalyzerCollectorsSettingHandler
            performanceAnalyzerCollectorsSettingHandler;
    private final ConfigOverridesClusterSettingHandler configOverridesClusterSettingHandler;
    private final ConfigOverridesWrapper configOverridesWrapper;
    private final PerformanceAnalyzerController performanceAnalyzerController;
    private final ClusterSettingsManager clusterSettingsManager;

    static {
        SecurityManager sm = System.getSecurityManager();
        Utils.configureMetrics();
        if (sm != null) {
            // unprivileged code such as scripts do not have SpecialPermission
            sm.checkPermission(new SpecialPermission());
        }
    }

    public static void invokePrivileged(Runnable runner) {
        AccessController.doPrivileged(
                (PrivilegedAction<Void>)
                        () -> {
                            try {
                                runner.run();
                            } catch (Exception ex) {
                                LOG.debug(
                                        (Supplier<?>)
                                                () ->
                                                        new ParameterizedMessage(
                                                                "Privileged Invocation failed {}",
                                                                ex.toString()),
                                        ex);
                            }
                            return null;
                        });
    }

    private final ScheduledMetricCollectorsExecutor scheduledMetricCollectorsExecutor;

    public PerformanceAnalyzerPlugin(final Settings settings, final java.nio.file.Path configPath) {

        OSMetricsGeneratorFactory.getInstance();

        OpenSearchResources.INSTANCE.setSettings(settings);
        OpenSearchResources.INSTANCE.setConfigPath(configPath);
        OpenSearchResources.INSTANCE.setPluginFileLocation(
                new Environment(settings, configPath).pluginsDir().toString()
                        + File.separator
                        + PLUGIN_NAME
                        + File.separator);
        // initialize plugin settings. Accessing plugin settings before this
        // point will break, as the plugin location will not be initialized.
        PluginSettings.instance();
        scheduledMetricCollectorsExecutor = new ScheduledMetricCollectorsExecutor();
        this.performanceAnalyzerController =
                new PerformanceAnalyzerController(scheduledMetricCollectorsExecutor);

        configOverridesWrapper = new ConfigOverridesWrapper();
        clusterSettingsManager =
                new ClusterSettingsManager(
                        Arrays.asList(
                                PerformanceAnalyzerClusterSettings.COMPOSITE_PA_SETTING,
                                PerformanceAnalyzerClusterSettings.PA_NODE_STATS_SETTING,
                                PerformanceAnalyzerClusterSettings.PA_COLLECTORS_SETTING),
                        Collections.singletonList(
                                PerformanceAnalyzerClusterSettings.CONFIG_OVERRIDES_SETTING));
        configOverridesClusterSettingHandler =
                new ConfigOverridesClusterSettingHandler(
                        configOverridesWrapper,
                        clusterSettingsManager,
                        PerformanceAnalyzerClusterSettings.CONFIG_OVERRIDES_SETTING);
        clusterSettingsManager.addSubscriberForStringSetting(
                PerformanceAnalyzerClusterSettings.CONFIG_OVERRIDES_SETTING,
                configOverridesClusterSettingHandler);
        perfAnalyzerClusterSettingHandler =
                new PerformanceAnalyzerClusterSettingHandler(
                        performanceAnalyzerController, clusterSettingsManager);
        clusterSettingsManager.addSubscriberForIntSetting(
                PerformanceAnalyzerClusterSettings.COMPOSITE_PA_SETTING,
                perfAnalyzerClusterSettingHandler);

        nodeStatsSettingHandler =
                new NodeStatsSettingHandler(performanceAnalyzerController, clusterSettingsManager);
        clusterSettingsManager.addSubscriberForIntSetting(
                PerformanceAnalyzerClusterSettings.PA_NODE_STATS_SETTING, nodeStatsSettingHandler);

        performanceAnalyzerCollectorsSettingHandler =
                new PerformanceAnalyzerCollectorsSettingHandler(
                        performanceAnalyzerController, clusterSettingsManager);
        clusterSettingsManager.addSubscriberForIntSetting(
                PerformanceAnalyzerClusterSettings.PA_COLLECTORS_SETTING,
                performanceAnalyzerCollectorsSettingHandler);

        scheduleTelemetryCollectors();
        scheduleRcaCollectors();

        scheduledMetricCollectorsExecutor.start();

        EventLog eventLog = new EventLog();
        EventLogFileHandler eventLogFileHandler =
                new EventLogFileHandler(eventLog, PluginSettings.instance().getMetricsLocation());
        new EventLogQueueProcessor(
                        eventLogFileHandler,
                        MetricsConfiguration.SAMPLING_INTERVAL,
                        QUEUE_PURGE_INTERVAL_MS,
                        performanceAnalyzerController)
                .scheduleExecutor();
    }

    private void scheduleTelemetryCollectors() {
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new RTFDisksCollector(performanceAnalyzerController, configOverridesWrapper));
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new RTFHeapMetricsCollector(performanceAnalyzerController, configOverridesWrapper));
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new RTFThreadPoolMetricsCollector(
                        performanceAnalyzerController, configOverridesWrapper));
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new RTFNodeStatsAllShardsMetricsCollector(
                        performanceAnalyzerController, configOverridesWrapper));
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new RTFCacheConfigMetricsCollector(
                        performanceAnalyzerController, configOverridesWrapper));
    }

    private void scheduleRcaCollectors() {
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new ThreadPoolMetricsCollector());
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(new HeapMetricsCollector());
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new NodeStatsAllShardsMetricsCollector(performanceAnalyzerController));
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(new DisksCollector());
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new CacheConfigMetricsCollector());
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new CircuitBreakerCollector());
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(new OSMetricsCollector());
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new NodeDetailsCollector(configOverridesWrapper));
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new ClusterManagerServiceMetrics());
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new ClusterManagerServiceEventMetrics());
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new NetworkInterfaceCollector());
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(StatsCollector.instance());
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new FaultDetectionMetricsCollector(
                        performanceAnalyzerController, configOverridesWrapper));
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new ShardStateCollector(performanceAnalyzerController, configOverridesWrapper));
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new ClusterApplierServiceStatsCollector(
                        performanceAnalyzerController, configOverridesWrapper));
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new SearchBackPressureStatsCollector(
                        performanceAnalyzerController, configOverridesWrapper));
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new AdmissionControlMetricsCollector());
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                new ElectionTermCollector(performanceAnalyzerController, configOverridesWrapper));
        scheduledMetricCollectorsExecutor.addScheduledMetricCollector(new GCInfoCollector());
        try {
            Class.forName(ShardIndexingPressureMetricsCollector.SHARD_INDEXING_PRESSURE_CLASS_NAME);
            scheduledMetricCollectorsExecutor.addScheduledMetricCollector(
                    new ShardIndexingPressureMetricsCollector(
                            performanceAnalyzerController, configOverridesWrapper));
        } catch (ClassNotFoundException e) {
            LOG.info(
                    "Shard IndexingPressure not present in this OpenSearch version. Skipping ShardIndexingPressureMetricsCollector");
        }
    }

    // - http level: bulk, search
    @Override
    public List<ActionFilter> getActionFilters() {
        return singletonList(new PerformanceAnalyzerActionFilter(performanceAnalyzerController));
    }

    @Override
    public List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> getActions() {
        List<ActionHandler<? extends ActionRequest, ? extends ActionResponse>> actions =
                new ArrayList<>(1);
        actions.add(new ActionHandler<>(WhoAmIAction.INSTANCE, TransportWhoAmIAction.class));
        return actions;
    }

    // - shardquery, shardfetch
    @Override
    public void onIndexModule(IndexModule indexModule) {
        PerformanceAnalyzerSearchListener performanceanalyzerSearchListener =
                new PerformanceAnalyzerSearchListener(performanceAnalyzerController);
        RTFPerformanceAnalyzerSearchListener rtfPerformanceAnalyzerSearchListener =
                new RTFPerformanceAnalyzerSearchListener(performanceAnalyzerController);
        indexModule.addSearchOperationListener(performanceanalyzerSearchListener);
        indexModule.addSearchOperationListener(rtfPerformanceAnalyzerSearchListener);
    }

    // follower check, leader check
    public void onDiscovery(Discovery discovery) {
        try {
            Class<?> listenerInjector = Class.forName(LISTENER_INJECTOR_CLASS_PATH);
            Object listenerInjectorInstance =
                    listenerInjector.getDeclaredConstructor().newInstance();
            Method addListenerMethod =
                    listenerInjectorInstance
                            .getClass()
                            .getMethod(ADD_FAULT_DETECTION_METHOD, Discovery.class);
            addListenerMethod.invoke(listenerInjectorInstance, discovery);
        } catch (InstantiationException
                | InvocationTargetException
                | NoSuchMethodException
                | IllegalAccessException e) {
            LOG.debug("Exception while calling addFaultDetectionListener in Discovery");
        } catch (ClassNotFoundException e) {
            LOG.debug("No Class for ListenerInjector detected");
        }
    }

    // - shardbulk
    @Override
    public List<TransportInterceptor> getTransportInterceptors(
            NamedWriteableRegistry namedWriteableRegistry, ThreadContext threadContext) {
        return Arrays.asList(
                new PerformanceAnalyzerTransportInterceptor(performanceAnalyzerController),
                new RTFPerformanceAnalyzerTransportInterceptor(performanceAnalyzerController));
    }

    @Override
    public List<org.opensearch.rest.RestHandler> getRestHandlers(
            final Settings settings,
            final RestController restController,
            final ClusterSettings clusterSettings,
            final IndexScopedSettings indexScopedSettings,
            final SettingsFilter settingsFilter,
            final IndexNameExpressionResolver indexNameExpressionResolver,
            final Supplier<DiscoveryNodes> nodesInCluster) {
        PerformanceAnalyzerConfigAction performanceanalyzerConfigAction =
                new PerformanceAnalyzerConfigAction(restController, performanceAnalyzerController);
        PerformanceAnalyzerConfigAction.setInstance(performanceanalyzerConfigAction);
        PerformanceAnalyzerResourceProvider performanceAnalyzerRp =
                new PerformanceAnalyzerResourceProvider(settings, restController);
        PerformanceAnalyzerClusterConfigAction paClusterConfigAction =
                new PerformanceAnalyzerClusterConfigAction(
                        settings,
                        restController,
                        perfAnalyzerClusterSettingHandler,
                        nodeStatsSettingHandler,
                        performanceAnalyzerCollectorsSettingHandler);
        PerformanceAnalyzerOverridesClusterConfigAction paOverridesConfigClusterAction =
                new PerformanceAnalyzerOverridesClusterConfigAction(
                        settings,
                        restController,
                        configOverridesClusterSettingHandler,
                        configOverridesWrapper);
        return Arrays.asList(
                performanceanalyzerConfigAction,
                paClusterConfigAction,
                performanceAnalyzerRp,
                paOverridesConfigClusterAction);
    }

    @Override
    @SuppressWarnings("checkstyle:parameternumber")
    public Collection<Object> createComponents(
            Client client,
            ClusterService clusterService,
            ThreadPool threadPool,
            ResourceWatcherService resourceWatcherService,
            ScriptService scriptService,
            NamedXContentRegistry xContentRegistry,
            Environment environment,
            NodeEnvironment nodeEnvironment,
            NamedWriteableRegistry namedWriteableRegistry,
            IndexNameExpressionResolver indexNameExpressionResolver,
            Supplier<RepositoriesService> repositoriesServiceSupplier,
            Tracer tracer,
            MetricsRegistry metricsRegistry) {
        OpenSearchResources.INSTANCE.setClusterService(clusterService);
        OpenSearchResources.INSTANCE.setThreadPool(threadPool);
        OpenSearchResources.INSTANCE.setEnvironment(environment);
        OpenSearchResources.INSTANCE.setClient(client);
        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry);
        // ClusterSettingsManager needs ClusterService to have been created before we can
        // initialize it. This is the earliest point at which we know ClusterService is created.
        // So, call the initialize method here.
        clusterSettingsManager.initialize();
        return Collections.singletonList(performanceAnalyzerController);
    }

    @Override
    public Map<String, Supplier<Transport>> getTransports(
            Settings settings,
            ThreadPool threadPool,
            PageCacheRecycler pageCacheRecycler,
            CircuitBreakerService circuitBreakerService,
            NamedWriteableRegistry namedWriteableRegistry,
            NetworkService networkService,
            Tracer tracer) {
        OpenSearchResources.INSTANCE.setSettings(settings);
        OpenSearchResources.INSTANCE.setCircuitBreakerService(circuitBreakerService);
        return Collections.emptyMap();
    }

    /** Returns a list of additional {@link Setting} definitions for this plugin. */
    @Override
    public List<Setting<?>> getSettings() {
        return Arrays.asList(
                PerformanceAnalyzerClusterSettings.COMPOSITE_PA_SETTING,
                PerformanceAnalyzerClusterSettings.PA_NODE_STATS_SETTING,
                PerformanceAnalyzerClusterSettings.CONFIG_OVERRIDES_SETTING,
                PerformanceAnalyzerClusterSettings.PA_COLLECTORS_SETTING);
    }
}
