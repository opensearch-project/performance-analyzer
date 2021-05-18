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
