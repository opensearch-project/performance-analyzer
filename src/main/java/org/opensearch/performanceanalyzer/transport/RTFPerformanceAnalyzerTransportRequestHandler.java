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
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.collectors.StatsCollector;
import org.opensearch.performanceanalyzer.commons.metrics.RTFMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.RTFMetrics.MetricUnits;
import org.opensearch.performanceanalyzer.commons.util.Util;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.tasks.Task;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.TransportRequest;
import org.opensearch.transport.TransportRequestHandler;

/**
 * {@link TransportRequestHandler} implementation to intercept only the {@link BulkShardRequest} and
 * skip other transport calls.
 *
 * @param <T> {@link TransportRequest}
 */
public final class RTFPerformanceAnalyzerTransportRequestHandler<T extends TransportRequest>
        implements TransportRequestHandler<T> {
    private static final Logger LOG =
            LogManager.getLogger(RTFPerformanceAnalyzerTransportRequestHandler.class);
    private final PerformanceAnalyzerController controller;
    private TransportRequestHandler<T> actualHandler;
    private boolean logOnce = false;
    private final Histogram cpuUtilizationHistogram;
    private final Histogram indexingLatencyHistogram;
    private final Histogram heapUsedHistogram;

    RTFPerformanceAnalyzerTransportRequestHandler(
            TransportRequestHandler<T> actualHandler, PerformanceAnalyzerController controller) {
        this.actualHandler = actualHandler;
        this.controller = controller;
        this.cpuUtilizationHistogram = createCPUUtilizationHistogram();
        this.indexingLatencyHistogram = createIndexingLatencyHistogram();
        this.heapUsedHistogram = createHeapUsedHistogram();
    }

    private Histogram createCPUUtilizationHistogram() {
        MetricsRegistry metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        if (metricsRegistry != null) {
            return metricsRegistry.createHistogram(
                    RTFMetrics.OSMetrics.CPU_UTILIZATION.toString(),
                    "CPU Utilization per shard for an operation",
                    RTFMetrics.MetricUnits.RATE.toString());
        } else {
            return null;
        }
    }

    private Histogram createHeapUsedHistogram() {
        MetricsRegistry metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        if (metricsRegistry != null) {
            return metricsRegistry.createHistogram(
                    RTFMetrics.OSMetrics.HEAP_ALLOCATED.toString(),
                    "Heap Utilization per shard for an operation",
                    RTFMetrics.MetricUnits.BYTE.toString());
        } else {
            return null;
        }
    }

    private Histogram createIndexingLatencyHistogram() {
        MetricsRegistry metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        if (metricsRegistry != null) {
            return metricsRegistry.createHistogram(
                    RTFMetrics.ShardOperationsValue.SHARD_INDEXING_LATENCY.toString(),
                    "Indexing Latency per shard for an operation",
                    MetricUnits.MILLISECOND.toString());
        } else {
            return null;
        }
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
        return OpenSearchResources.INSTANCE.getMetricsRegistry() != null
                && controller.isPerformanceAnalyzerEnabled()
                && (controller.getCollectorsRunModeValue() == Util.CollectorMode.DUAL.getValue()
                        || controller.getCollectorsRunModeValue()
                                == Util.CollectorMode.TELEMETRY.getValue());
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

        if (!(transportRequest instanceof BulkShardRequest bsr)) {
            return channel;
        }

        RTFPerformanceAnalyzerTransportChannel rtfPerformanceAnalyzerTransportChannel =
                new RTFPerformanceAnalyzerTransportChannel();

        try {
            rtfPerformanceAnalyzerTransportChannel.set(
                    channel,
                    cpuUtilizationHistogram,
                    indexingLatencyHistogram,
                    heapUsedHistogram,
                    bsr.index(),
                    bsr.shardId(),
                    bPrimary);
        } catch (Exception ex) {
            if (!logOnce) {
                LOG.error(ex);
                logOnce = true;
            }
            StatsCollector.instance().logException(OPENSEARCH_REQUEST_INTERCEPTOR_ERROR);
        }

        return rtfPerformanceAnalyzerTransportChannel;
    }
}
