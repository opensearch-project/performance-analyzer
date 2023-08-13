/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.action;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opensearch.action.ActionRequest;
import org.opensearch.action.bulk.BulkRequest;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.action.support.ActionFilterChain;
import org.opensearch.core.action.ActionListener;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.tasks.Task;

public class PerformanceAnalyzerActionFilterTests {
    private static final String[] testIndices = new String[] {"testIndex"};
    private PerformanceAnalyzerActionFilter filter;

    @Mock private PerformanceAnalyzerController controller;
    @Mock private SearchRequest searchRequest;
    @Mock private BulkRequest bulkRequest;
    @Mock private ActionRequest request;
    @Mock private ActionListener<ActionResponse> listener;
    @Mock private ActionFilterChain<ActionRequest, ActionResponse> chain;
    @Mock private Task task;

    @Before
    public void init() {
        initMocks(this);

        Mockito.when(controller.isPerformanceAnalyzerEnabled()).thenReturn(true);
        filter = new PerformanceAnalyzerActionFilter((controller));
    }

    @Test
    public void testApplyWithSearchRequest() {
        Mockito.when(searchRequest.indices()).thenReturn(testIndices);
        testApply(searchRequest);
    }

    @Test
    public void testApplyWithBulkRequest() {
        testApply(bulkRequest);
    }

    @Test
    public void testApplyWithOtherRequest() {
        testApply(request);
    }

    private void testApply(ActionRequest request) {
        filter.apply(task, "_action", request, listener, chain);
        verify(chain).proceed(eq(task), eq("_action"), eq(request), any());
    }

    @Test
    public void testOrder() {
        assertEquals(Integer.MIN_VALUE, filter.order());
    }
}
