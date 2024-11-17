/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.action;

import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.ActionFilter;
import org.opensearch.action.support.ActionFilterChain;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.commons.util.Util;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.tasks.Task;

public class PerformanceAnalyzerActionFilter implements ActionFilter {
    private static final Logger LOG = LogManager.getLogger(PerformanceAnalyzerActionFilter.class);
    private static AtomicLong uniqueID = new AtomicLong(0);

    private final PerformanceAnalyzerController controller;

    @Inject
    public PerformanceAnalyzerActionFilter(final PerformanceAnalyzerController controller) {
        this.controller = controller;
    }

    @Override
    public <Request extends ActionRequest, Response extends ActionResponse> void apply(
            Task task,
            final String action,
            Request request,
            ActionListener<Response> listener,
            ActionFilterChain<Request, Response> chain) {

        if (controller.isPerformanceAnalyzerEnabled()
                && (controller.getCollectorsRunModeValue() == Util.CollectorMode.DUAL.getValue()
                        || controller.getCollectorsRunModeValue()
                                == Util.CollectorMode.RCA.getValue())) {
            if (request instanceof BulkRequest) {
                PerformanceAnalyzerActionListener<Response> newListener =
                        new PerformanceAnalyzerActionListener<>();
                String id = String.valueOf(uniqueID.getAndIncrement());
                long startTime = System.currentTimeMillis();
                BulkRequest bulk = (BulkRequest) request;
                newListener.set(RequestType.bulk, id, listener);
                newListener.saveMetricValues(
                        newListener.generateStartMetrics(startTime, "", bulk.requests().size()),
                        startTime,
                        RequestType.bulk.toString(),
                        id,
                        PerformanceAnalyzerMetrics.START_FILE_NAME);
                chain.proceed(task, action, request, newListener);
                return;
            } else if (request instanceof SearchRequest) {
                PerformanceAnalyzerActionListener<Response> newListener =
                        new PerformanceAnalyzerActionListener<>();
                String id = String.valueOf(uniqueID.getAndIncrement());
                long startTime = System.currentTimeMillis();
                SearchRequest search = (SearchRequest) request;
                newListener.set(RequestType.search, id, listener);
                newListener.saveMetricValues(
                        newListener.generateStartMetrics(
                                startTime, String.join(",", search.indices()), 0),
                        startTime,
                        RequestType.search.toString(),
                        id,
                        PerformanceAnalyzerMetrics.START_FILE_NAME);
                chain.proceed(task, action, request, newListener);
                return;
            }
        }

        chain.proceed(task, action, request, listener);
    }

    /** The position of the filter in the chain. Execution is done from lowest order to highest. */
    @Override
    public int order() {
        return Integer.MIN_VALUE;
    }
}
