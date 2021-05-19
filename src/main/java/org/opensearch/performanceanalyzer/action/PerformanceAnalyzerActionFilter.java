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
 * Copyright 2019-2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package org.opensearch.performanceanalyzer.action;


import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.ActionResponse;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.ActionFilter;
import org.opensearch.action.support.ActionFilterChain;
import org.opensearch.common.inject.Inject;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.metrics.PerformanceAnalyzerMetrics;
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

        if (controller.isPerformanceAnalyzerEnabled()) {
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
