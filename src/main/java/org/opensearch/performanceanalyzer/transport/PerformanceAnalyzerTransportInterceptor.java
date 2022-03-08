/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.transport;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.transport.TransportInterceptor;
import org.opensearch.transport.TransportRequest;
import org.opensearch.transport.TransportRequestHandler;

public class PerformanceAnalyzerTransportInterceptor implements TransportInterceptor {

    private static final Logger LOG =
            LogManager.getLogger(PerformanceAnalyzerTransportInterceptor.class);
    private final PerformanceAnalyzerController controller;

    public PerformanceAnalyzerTransportInterceptor(final PerformanceAnalyzerController controller) {
        this.controller = controller;
    }

    @Override
    public <T extends TransportRequest> TransportRequestHandler<T> interceptHandler(
            String action,
            String executor,
            boolean forceExecution,
            TransportRequestHandler<T> actualHandler) {
        return new PerformanceAnalyzerTransportRequestHandler<>(actualHandler, controller);
    }
}
