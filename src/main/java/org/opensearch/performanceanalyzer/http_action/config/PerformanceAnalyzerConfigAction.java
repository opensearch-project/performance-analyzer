/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.http_action.config;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.core.rest.RestStatus;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.performanceanalyzer.commons.config.PluginSettings;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestRequest;
import org.opensearch.transport.client.node.NodeClient;

@SuppressWarnings("deprecation")
public class PerformanceAnalyzerConfigAction extends BaseRestHandler {

    private static final Logger LOG = LogManager.getLogger(PerformanceAnalyzerConfigAction.class);
    private static PerformanceAnalyzerConfigAction instance = null;
    private final PerformanceAnalyzerController performanceAnalyzerController;

    public static final String ENABLED = "enabled";
    public static final String SHARDS_PER_COLLECTION = "shardsPerCollection";
    public static final String PA_ENABLED = "performanceAnalyzerEnabled";
    public static final String RCA_ENABLED = "rcaEnabled";
    public static final String PA_LOGGING_ENABLED = "loggingEnabled";
    public static final String BATCH_METRICS_ENABLED = "batchMetricsEnabled";
    public static final String THREAD_CONTENTION_MONITORING_ENABLED =
            "threadContentionMonitoringEnabled";
    public static final String BATCH_METRICS_RETENTION_PERIOD_MINUTES =
            "batchMetricsRetentionPeriodMinutes";
    public static final String PERFORMANCE_ANALYZER_CONFIG_ACTION =
            "PerformanceAnalyzer_Config_Action";

    public static final String RCA_CONFIG_PATH = RestConfig.PA_BASE_URI + "/rca/config";
    public static final String PA_CONFIG_PATH = RestConfig.PA_BASE_URI + "/config";
    public static final String LOGGING_CONFIG_PATH = RestConfig.PA_BASE_URI + "/logging/config";
    public static final String BATCH_METRICS_CONFIG_PATH = RestConfig.PA_BASE_URI + "/batch/config";
    public static final String THREAD_CONTENTION_MONITORING_CONFIG_PATH =
            RestConfig.PA_BASE_URI + "/threadContentionMonitoring/config";

    public static final String LEGACY_RCA_CONFIG_PATH =
            RestConfig.LEGACY_PA_BASE_URI + "/rca/config";
    public static final String LEGACY_PA_CONFIG_PATH = RestConfig.LEGACY_PA_BASE_URI + "/config";
    public static final String LEGACY_LOGGING_CONFIG_PATH =
            RestConfig.LEGACY_PA_BASE_URI + "/logging/config";
    public static final String LEGACY_BATCH_METRICS_CONFIG_PATH =
            RestConfig.LEGACY_PA_BASE_URI + "/batch/config";
    private static final List<Route> ROUTES =
            unmodifiableList(
                    asList(
                            new Route(
                                    RestRequest.Method.GET,
                                    THREAD_CONTENTION_MONITORING_CONFIG_PATH),
                            new Route(
                                    RestRequest.Method.POST,
                                    THREAD_CONTENTION_MONITORING_CONFIG_PATH)));
    private static final List<ReplacedRoute> REPLACED_ROUTES =
            unmodifiableList(
                    asList(
                            new ReplacedRoute(
                                    RestRequest.Method.GET,
                                    PA_CONFIG_PATH,
                                    RestRequest.Method.GET,
                                    LEGACY_PA_CONFIG_PATH),
                            new ReplacedRoute(
                                    RestRequest.Method.POST,
                                    PA_CONFIG_PATH,
                                    RestRequest.Method.POST,
                                    LEGACY_PA_CONFIG_PATH),
                            new ReplacedRoute(
                                    RestRequest.Method.GET,
                                    RCA_CONFIG_PATH,
                                    RestRequest.Method.GET,
                                    LEGACY_RCA_CONFIG_PATH),
                            new ReplacedRoute(
                                    RestRequest.Method.POST,
                                    RCA_CONFIG_PATH,
                                    RestRequest.Method.POST,
                                    LEGACY_RCA_CONFIG_PATH),
                            new ReplacedRoute(
                                    RestRequest.Method.GET,
                                    LOGGING_CONFIG_PATH,
                                    RestRequest.Method.GET,
                                    LEGACY_LOGGING_CONFIG_PATH),
                            new ReplacedRoute(
                                    RestRequest.Method.POST,
                                    LOGGING_CONFIG_PATH,
                                    RestRequest.Method.POST,
                                    LEGACY_LOGGING_CONFIG_PATH),
                            new ReplacedRoute(
                                    RestRequest.Method.GET,
                                    BATCH_METRICS_CONFIG_PATH,
                                    RestRequest.Method.GET,
                                    LEGACY_BATCH_METRICS_CONFIG_PATH),
                            new ReplacedRoute(
                                    RestRequest.Method.POST,
                                    BATCH_METRICS_CONFIG_PATH,
                                    RestRequest.Method.POST,
                                    LEGACY_BATCH_METRICS_CONFIG_PATH)));

    public static PerformanceAnalyzerConfigAction getInstance() {
        return instance;
    }

    public static void setInstance(
            PerformanceAnalyzerConfigAction performanceanalyzerConfigAction) {
        instance = performanceanalyzerConfigAction;
    }

    @Override
    public List<Route> routes() {
        return ROUTES;
    }

    @Override
    public List<ReplacedRoute> replacedRoutes() {
        return REPLACED_ROUTES;
    }

    @Inject
    public PerformanceAnalyzerConfigAction(
            final RestController controller,
            final PerformanceAnalyzerController performanceAnalyzerController) {
        this.performanceAnalyzerController = performanceAnalyzerController;
        LOG.info(
                "PerformanceAnalyzer Enabled: {}",
                performanceAnalyzerController::isPerformanceAnalyzerEnabled);
    }

    @Override
    protected RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client)
            throws IOException {
        if (request.method() == RestRequest.Method.POST && request.content().length() > 0) {
            // Let's try to find the name from the body
            Map<String, Object> map = XContentHelper.convertToMap(request.content(), false).v2();
            Object value = map.get(ENABLED);
            LOG.debug(
                    "PerformanceAnalyzer:Value (Object) Received as Part of Request: {} current value: {}",
                    value,
                    performanceAnalyzerController.isPerformanceAnalyzerEnabled());
            if (value instanceof Boolean) {
                boolean shouldEnable = (Boolean) value;
                if (request.path().contains(RCA_CONFIG_PATH)
                        || request.path().contains(LEGACY_RCA_CONFIG_PATH)) {
                    // If RCA needs to be turned on, we need to have PA turned on also.
                    // If this is not the case, return error.
                    if (shouldEnable
                            && !performanceAnalyzerController.isPerformanceAnalyzerEnabled()) {
                        return getChannelConsumerWithError(
                                "Error: PA not enabled. Enable PA before turning RCA on");
                    }

                    performanceAnalyzerController.updateRcaState(shouldEnable);
                } else if (request.path().contains(LOGGING_CONFIG_PATH)
                        || request.path().contains(LEGACY_LOGGING_CONFIG_PATH)) {
                    if (shouldEnable
                            && !performanceAnalyzerController.isPerformanceAnalyzerEnabled()) {
                        return getChannelConsumerWithError(
                                "Error: PA not enabled. Enable PA before turning Logging on");
                    }

                    performanceAnalyzerController.updateLoggingState(shouldEnable);
                } else if (request.path().contains(BATCH_METRICS_CONFIG_PATH)
                        || request.path().contains(LEGACY_BATCH_METRICS_CONFIG_PATH)) {
                    if (shouldEnable
                            && !performanceAnalyzerController.isPerformanceAnalyzerEnabled()) {
                        return getChannelConsumerWithError(
                                "Error: PA not enabled. Enable PA before turning Batch Metrics on");
                    }

                    performanceAnalyzerController.updateBatchMetricsState(shouldEnable);
                } else if (request.path().contains(THREAD_CONTENTION_MONITORING_CONFIG_PATH)) {
                    if (shouldEnable
                            && !performanceAnalyzerController.isPerformanceAnalyzerEnabled()) {
                        return getChannelConsumerWithError(
                                "Error: PA not enabled. Enable PA before turning thread contention monitoring on");
                    }
                    performanceAnalyzerController.updateThreadContentionMonitoringState(
                            shouldEnable);
                } else {
                    // Disabling Performance Analyzer should disable the RCA framework as well.
                    if (!shouldEnable) {
                        performanceAnalyzerController.updateRcaState(false);
                        performanceAnalyzerController.updateLoggingState(false);
                        performanceAnalyzerController.updateBatchMetricsState(false);
                        performanceAnalyzerController.updateThreadContentionMonitoringState(false);
                    }
                    performanceAnalyzerController.updatePerformanceAnalyzerState(shouldEnable);
                }
            }
            // update node stats setting if exists
            if (map.containsKey(SHARDS_PER_COLLECTION)) {
                Object shardPerCollectionValue = map.get(SHARDS_PER_COLLECTION);
                if (shardPerCollectionValue instanceof Integer) {
                    performanceAnalyzerController.updateNodeStatsShardsPerCollection(
                            (Integer) shardPerCollectionValue);
                }
            }
        }

        return channel -> {
            try {
                XContentBuilder builder = channel.newBuilder();
                builder.startObject();
                builder.field(
                        PA_ENABLED, performanceAnalyzerController.isPerformanceAnalyzerEnabled());
                builder.field(RCA_ENABLED, performanceAnalyzerController.isRcaEnabled());
                builder.field(PA_LOGGING_ENABLED, performanceAnalyzerController.isLoggingEnabled());
                builder.field(
                        SHARDS_PER_COLLECTION,
                        performanceAnalyzerController.getNodeStatsShardsPerCollection());
                builder.field(
                        BATCH_METRICS_ENABLED,
                        performanceAnalyzerController.isBatchMetricsEnabled());
                builder.field(
                        THREAD_CONTENTION_MONITORING_ENABLED,
                        performanceAnalyzerController.isThreadContentionMonitoringEnabled());
                builder.field(
                        BATCH_METRICS_RETENTION_PERIOD_MINUTES,
                        PluginSettings.instance().getBatchMetricsRetentionPeriodMinutes());
                builder.endObject();
                channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
            } catch (IOException ioe) {
                LOG.error("Error sending response", ioe);
            }
        };
    }

    @Override
    public String getName() {
        return PERFORMANCE_ANALYZER_CONFIG_ACTION;
    }

    private RestChannelConsumer getChannelConsumerWithError(String error) {
        return restChannel -> {
            XContentBuilder builder = restChannel.newErrorBuilder();
            builder.startObject();
            builder.field(error);
            builder.endObject();
            restChannel.sendResponse(new BytesRestResponse(RestStatus.BAD_REQUEST, builder));
        };
    }
}
