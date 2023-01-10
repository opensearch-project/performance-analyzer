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

package org.opensearch.performanceanalyzer.http_action.config;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentHelper;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.performanceanalyzer.config.PluginSettings;
import org.opensearch.performanceanalyzer.config.setting.handler.NodeStatsSettingHandler;
import org.opensearch.performanceanalyzer.config.setting.handler.PerformanceAnalyzerClusterSettingHandler;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestStatus;

/**
 * Rest request handler for handling cluster-wide enabling and disabling of performance analyzer
 * features.
 */
public class PerformanceAnalyzerClusterConfigAction extends BaseRestHandler {
    private static final Logger LOG =
            LogManager.getLogger(PerformanceAnalyzerClusterConfigAction.class);

    public static final String CURRENT = "currentPerformanceAnalyzerClusterState";
    public static final String BATCH_METRICS_RETENTION_PERIOD_MINUTES =
            "batchMetricsRetentionPeriodMinutes";
    public static final String ENABLED = "enabled";
    public static final String SHARDS_PER_COLLECTION = "shardsPerCollection";

    public static final String PA_CLUSTER_CONFIG_PATH = RestConfig.PA_BASE_URI + "/cluster/config";
    public static final String RCA_CLUSTER_CONFIG_PATH =
            RestConfig.PA_BASE_URI + "/rca/cluster/config";
    public static final String LOGGING_CLUSTER_CONFIG_PATH =
            RestConfig.PA_BASE_URI + "/logging/cluster/config";
    public static final String BATCH_METRICS_CLUSTER_CONFIG_PATH =
            RestConfig.PA_BASE_URI + "/batch/cluster/config";

    public static final String LEGACY_PA_CLUSTER_CONFIG_PATH =
            RestConfig.LEGACY_PA_BASE_URI + "/cluster/config";
    public static final String LEGACY_RCA_CLUSTER_CONFIG_PATH =
            RestConfig.LEGACY_PA_BASE_URI + "/rca/cluster/config";
    public static final String LEGACY_LOGGING_CLUSTER_CONFIG_PATH =
            RestConfig.LEGACY_PA_BASE_URI + "/logging/cluster/config";
    public static final String LEGACY_BATCH_METRICS_CLUSTER_CONFIG_PATH =
            RestConfig.LEGACY_PA_BASE_URI + "/batch/cluster/config";

    private static final List<ReplacedRoute> REPLACED_ROUTES =
            unmodifiableList(
                    asList(
                            new ReplacedRoute(
                                    RestRequest.Method.GET,
                                    PA_CLUSTER_CONFIG_PATH,
                                    RestRequest.Method.GET,
                                    LEGACY_PA_CLUSTER_CONFIG_PATH),
                            new ReplacedRoute(
                                    RestRequest.Method.POST,
                                    PA_CLUSTER_CONFIG_PATH,
                                    RestRequest.Method.POST,
                                    LEGACY_PA_CLUSTER_CONFIG_PATH),
                            new ReplacedRoute(
                                    RestRequest.Method.GET,
                                    RCA_CLUSTER_CONFIG_PATH,
                                    RestRequest.Method.GET,
                                    LEGACY_RCA_CLUSTER_CONFIG_PATH),
                            new ReplacedRoute(
                                    RestRequest.Method.POST,
                                    RCA_CLUSTER_CONFIG_PATH,
                                    RestRequest.Method.POST,
                                    LEGACY_RCA_CLUSTER_CONFIG_PATH),
                            new ReplacedRoute(
                                    RestRequest.Method.GET,
                                    LOGGING_CLUSTER_CONFIG_PATH,
                                    RestRequest.Method.GET,
                                    LEGACY_LOGGING_CLUSTER_CONFIG_PATH),
                            new ReplacedRoute(
                                    RestRequest.Method.POST,
                                    LOGGING_CLUSTER_CONFIG_PATH,
                                    RestRequest.Method.POST,
                                    LEGACY_LOGGING_CLUSTER_CONFIG_PATH),
                            new ReplacedRoute(
                                    RestRequest.Method.GET,
                                    BATCH_METRICS_CLUSTER_CONFIG_PATH,
                                    RestRequest.Method.GET,
                                    LEGACY_BATCH_METRICS_CLUSTER_CONFIG_PATH),
                            new ReplacedRoute(
                                    RestRequest.Method.POST,
                                    BATCH_METRICS_CLUSTER_CONFIG_PATH,
                                    RestRequest.Method.POST,
                                    LEGACY_BATCH_METRICS_CLUSTER_CONFIG_PATH)));

    private final PerformanceAnalyzerClusterSettingHandler clusterSettingHandler;
    private final NodeStatsSettingHandler nodeStatsSettingHandler;

    public PerformanceAnalyzerClusterConfigAction(
            final Settings settings,
            final RestController restController,
            final PerformanceAnalyzerClusterSettingHandler clusterSettingHandler,
            final NodeStatsSettingHandler nodeStatsSettingHandler) {
        this.clusterSettingHandler = clusterSettingHandler;
        this.nodeStatsSettingHandler = nodeStatsSettingHandler;
    }

    /**
     * @return the name of this handler. The name should be human readable and should describe the
     *     action that will performed when this API is called.
     */
    @Override
    public String getName() {
        return PerformanceAnalyzerClusterConfigAction.class.getSimpleName();
    }

    @Override
    public List<Route> routes() {
        return Collections.emptyList();
    }

    @Override
    public List<ReplacedRoute> replacedRoutes() {
        return REPLACED_ROUTES;
    }

    /**
     * Prepare the request for execution. Implementations should consume all request params before
     * returning the runnable for actual execution. Unconsumed params will immediately terminate
     * execution of the request. However, some params are only used in processing the response;
     * implementations can override {@link BaseRestHandler#responseParams()} to indicate such
     * params.
     *
     * @param request the request to execute
     * @param client client for executing actions on the local node
     * @return the action to execute
     * @throws IOException if an I/O exception occurred parsing the request and preparing for
     *     execution
     */
    @Override
    protected RestChannelConsumer prepareRequest(final RestRequest request, final NodeClient client)
            throws IOException {
        request.param("verbose");
        if (request.method() == RestRequest.Method.POST && request.content().length() > 0) {
            Map<String, Object> map =
                    XContentHelper.convertToMap(request.content(), false, XContentType.JSON).v2();
            Object value = map.get(ENABLED);
            LOG.debug(
                    "PerformanceAnalyzer:Value (Object) Received as Part of Request: {} current value: {}",
                    value,
                    clusterSettingHandler.getCurrentClusterSettingValue());

            if (value instanceof Boolean) {
                if (request.path().contains(RCA_CLUSTER_CONFIG_PATH)
                        || request.path().contains(LEGACY_RCA_CLUSTER_CONFIG_PATH)) {
                    clusterSettingHandler.updateRcaSetting((Boolean) value);
                } else if (request.path().contains(LOGGING_CLUSTER_CONFIG_PATH)
                        || request.path().contains(LEGACY_LOGGING_CLUSTER_CONFIG_PATH)) {
                    clusterSettingHandler.updateLoggingSetting((Boolean) value);
                } else if (request.path().contains(BATCH_METRICS_CLUSTER_CONFIG_PATH)
                        || request.path().contains(LEGACY_BATCH_METRICS_CLUSTER_CONFIG_PATH)) {
                    clusterSettingHandler.updateBatchMetricsSetting((Boolean) value);
                } else {
                    clusterSettingHandler.updatePerformanceAnalyzerSetting((Boolean) value);
                }
            }
            // update node stats setting if exists
            if (map.containsKey(SHARDS_PER_COLLECTION)) {
                Object shardPerCollectionValue = map.get(SHARDS_PER_COLLECTION);
                if (shardPerCollectionValue instanceof Integer) {
                    nodeStatsSettingHandler.updateNodeStatsSetting(
                            (Integer) shardPerCollectionValue);
                }
            }
        }

        return channel -> {
            try {
                XContentBuilder builder = channel.newBuilder();
                builder.startObject();
                builder.field(
                        CURRENT,
                        request.paramAsBoolean("verbose", false)
                                ? clusterSettingHandler.getCurrentClusterSettingValueVerbose()
                                : clusterSettingHandler.getCurrentClusterSettingValue());
                builder.field(SHARDS_PER_COLLECTION, nodeStatsSettingHandler.getNodeStatsSetting());
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
}
