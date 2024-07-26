/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.transport;

import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.transport.TransportInterceptor;
import org.opensearch.transport.TransportRequest;
import org.opensearch.transport.TransportRequestHandler;

/**
 * Transport Interceptor to intercept the Indexing requests and populate the resource utilization
 * metrics.
 */
public final class RTFPerformanceAnalyzerTransportInterceptor implements TransportInterceptor {

    private final PerformanceAnalyzerController controller;

    public RTFPerformanceAnalyzerTransportInterceptor(
            final PerformanceAnalyzerController controller) {
        this.controller = controller;
    }

    @Override
    public <T extends TransportRequest> TransportRequestHandler<T> interceptHandler(
            String action,
            String executor,
            boolean forceExecution,
            TransportRequestHandler<T> actualHandler) {
        return new RTFPerformanceAnalyzerTransportRequestHandler<>(actualHandler, controller);
    }
}
