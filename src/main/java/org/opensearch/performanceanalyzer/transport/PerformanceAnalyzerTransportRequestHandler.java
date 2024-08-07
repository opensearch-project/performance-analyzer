/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.transport;

import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode.OPENSEARCH_REQUEST_INTERCEPTOR_ERROR;

import com.google.common.annotations.VisibleForTesting;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.bulk.BulkShardRequest;
import org.opensearch.action.support.replication.TransportReplicationAction.ConcreteShardRequest;
import org.opensearch.performanceanalyzer.commons.collectors.StatsCollector;
import org.opensearch.performanceanalyzer.commons.util.Util;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.TransportRequest;
import org.opensearch.transport.TransportRequestHandler;

public class PerformanceAnalyzerTransportRequestHandler<T extends TransportRequest>
        implements TransportRequestHandler<T> {
    private static final Logger LOG =
            LogManager.getLogger(PerformanceAnalyzerTransportRequestHandler.class);
    private final PerformanceAnalyzerController controller;
    private TransportRequestHandler<T> actualHandler;
    boolean logOnce = false;

    PerformanceAnalyzerTransportRequestHandler(
            TransportRequestHandler<T> actualHandler, PerformanceAnalyzerController controller) {
        this.actualHandler = actualHandler;
        this.controller = controller;
    }

    PerformanceAnalyzerTransportRequestHandler<T> set(TransportRequestHandler<T> actualHandler) {
        this.actualHandler = actualHandler;
        return this;
    }

    @Override
    public void messageReceived(T request, TransportChannel channel, Task task) throws Exception {
        actualHandler.messageReceived(request, getChannel(request, channel, task), task);
    }

    @VisibleForTesting
    TransportChannel getChannel(T request, TransportChannel channel, Task task) {
        if (!isCollectorEnabled()) {
            return channel;
        }

        if (request instanceof ConcreteShardRequest) {
            return getShardBulkChannel(request, channel, task);
        } else {
            return channel;
        }
    }

    private boolean isCollectorEnabled() {
        return controller.isPerformanceAnalyzerEnabled()
                && (controller.getCollectorsRunModeValue() == Util.CollectorMode.DUAL.getValue()
                        || controller.getCollectorsRunModeValue()
                                == Util.CollectorMode.RCA.getValue());
    }

    private TransportChannel getShardBulkChannel(T request, TransportChannel channel, Task task) {
        String className = request.getClass().getName();
        boolean bPrimary = false;

        if (className.equals(
                "org.opensearch.action.support.replication.TransportReplicationAction$ConcreteShardRequest")) {
            bPrimary = true;
        } else if (className.equals(
                "org.opensearch.action.support.replication.TransportReplicationAction$ConcreteReplicaRequest")) {
            bPrimary = false;
        } else {
            return channel;
        }

        TransportRequest transportRequest = ((ConcreteShardRequest<?>) request).getRequest();

        if (!(transportRequest instanceof BulkShardRequest)) {
            return channel;
        }

        BulkShardRequest bsr = (BulkShardRequest) transportRequest;
        PerformanceAnalyzerTransportChannel performanceanalyzerChannel =
                new PerformanceAnalyzerTransportChannel();

        try {
            performanceanalyzerChannel.set(
                    channel,
                    System.currentTimeMillis(),
                    bsr.index(),
                    bsr.shardId().id(),
                    bsr.items().length,
                    bPrimary);
        } catch (Exception ex) {
            if (!logOnce) {
                LOG.error(ex);
                logOnce = true;
            }
            StatsCollector.instance().logException(OPENSEARCH_REQUEST_INTERCEPTOR_ERROR);
        }

        return performanceanalyzerChannel;
    }
}
