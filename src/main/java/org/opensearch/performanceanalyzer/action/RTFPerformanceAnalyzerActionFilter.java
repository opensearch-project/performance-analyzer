/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.action;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.action.support.ActionFilter;
import org.opensearch.action.support.ActionFilterChain;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.performanceanalyzer.collectors.telemetry.RTFSearchRequestMetricsCollector;
import org.opensearch.performanceanalyzer.commons.util.Util;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.tasks.Task;

/**
 * RTF-specific {@link ActionFilter} that intercepts search responses and emits telemetry metrics
 * via the RTF pipeline. Kept separate from {@link PerformanceAnalyzerActionFilter} which handles
 * the RCA/event-queue pipeline.
 */
public class RTFPerformanceAnalyzerActionFilter implements ActionFilter {

    private static final Logger LOG =
            LogManager.getLogger(RTFPerformanceAnalyzerActionFilter.class);

    private final PerformanceAnalyzerController controller;
    private final RTFSearchRequestMetricsCollector rtfSearchRequestMetricsCollector;

    public RTFPerformanceAnalyzerActionFilter(final PerformanceAnalyzerController controller) {
        this.controller = controller;
        this.rtfSearchRequestMetricsCollector = new RTFSearchRequestMetricsCollector(controller);
    }

    @Override
    public <Request extends ActionRequest, Response extends ActionResponse> void apply(
            Task task,
            final String action,
            Request request,
            ActionListener<Response> listener,
            ActionFilterChain<Request, Response> chain) {

        if (request instanceof SearchRequest
                && controller.isPerformanceAnalyzerEnabled()
                && (controller.getCollectorsRunModeValue() == Util.CollectorMode.DUAL.getValue()
                        || controller.getCollectorsRunModeValue()
                                == Util.CollectorMode.TELEMETRY.getValue())) {
            ActionListener<Response> wrappedListener =
                    ActionListener.wrap(
                            response -> {
                                if (response instanceof SearchResponse) {
                                    rtfSearchRequestMetricsCollector.onSearchResponse(
                                            (SearchResponse) response);
                                }
                                listener.onResponse(response);
                            },
                            listener::onFailure);
            chain.proceed(task, action, request, wrappedListener);
            return;
        }

        chain.proceed(task, action, request, listener);
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE + 1;
    }
}
