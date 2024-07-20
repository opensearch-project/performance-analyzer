/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.transport;

import com.google.common.annotations.VisibleForTesting;
import com.sun.management.ThreadMXBean;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.transport.TransportResponse;
import org.opensearch.performanceanalyzer.commons.metrics.RTFMetrics;
import org.opensearch.performanceanalyzer.util.Utils;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.tags.Tags;
import org.opensearch.transport.TransportChannel;

/**
 * {@link TransportChannel} implementation to override the sendResponse behavior to have handle of
 * the {@link org.opensearch.action.bulk.BulkShardRequest} completion.
 */
public final class RTFPerformanceAnalyzerTransportChannel implements TransportChannel {
    private static final Logger LOG =
            LogManager.getLogger(RTFPerformanceAnalyzerTransportChannel.class);

    private static final ThreadMXBean threadMXBean =
            (ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final String OPERATION_SHARD_BULK = "shardbulk";
    private static final String SHARD_ROLE_PRIMARY = "primary";
    private static final String SHARD_ROLE_REPLICA = "replica";

    private long cpuStartTime;
    private long operationStartTime;

    private Histogram cpuUtilizationHistogram;

    private TransportChannel original;
    private String indexName;
    private ShardId shardId;
    private boolean primary;

    private long threadID;
    private int numProcessors;

    void set(
            TransportChannel original,
            Histogram cpuUtilizationHistogram,
            String indexName,
            ShardId shardId,
            boolean bPrimary) {
        this.original = original;
        this.cpuUtilizationHistogram = cpuUtilizationHistogram;
        this.indexName = indexName;
        this.shardId = shardId;
        this.primary = bPrimary;

        this.operationStartTime = System.nanoTime();
        threadID = Thread.currentThread().getId();
        this.cpuStartTime = threadMXBean.getThreadCpuTime(threadID);
        this.numProcessors = Runtime.getRuntime().availableProcessors();
        LOG.debug("Thread Name {}", Thread.currentThread().getName());
    }

    @Override
    public String getProfileName() {
        return "RTFPerformanceAnalyzerTransportChannelProfile";
    }

    @Override
    public String getChannelType() {
        return "RTFPerformanceAnalyzerTransportChannelType";
    }

    @Override
    public void sendResponse(TransportResponse response) throws IOException {
        emitMetrics(null);
        original.sendResponse(response);
    }

    @Override
    public void sendResponse(Exception exception) throws IOException {
        emitMetrics(exception);
        original.sendResponse(exception);
    }

    private void emitMetrics(Exception exception) {
        double cpuUtilization = calculateCPUUtilization(operationStartTime, cpuStartTime);
        recordCPUUtilizationMetric(
                shardId, cpuUtilization, OPERATION_SHARD_BULK, exception != null);
    }

    private double calculateCPUUtilization(long phaseStartTime, long phaseCPUStartTime) {
        LOG.debug("Completion Thread Name {}", Thread.currentThread().getName());
        long totalCpuTime =
                Math.max(0, (threadMXBean.getThreadCpuTime(threadID) - phaseCPUStartTime));
        return Utils.calculateCPUUtilization(
                numProcessors, (System.nanoTime() - phaseStartTime), totalCpuTime);
    }

    @VisibleForTesting
    void recordCPUUtilizationMetric(
            ShardId shardId, double cpuUtilization, String operation, boolean isFailed) {
        cpuUtilizationHistogram.record(
                cpuUtilization,
                Tags.create()
                        .addTag(
                                RTFMetrics.CommonDimension.INDEX_NAME.toString(),
                                shardId.getIndex().getName())
                        .addTag(
                                RTFMetrics.CommonDimension.INDEX_UUID.toString(),
                                shardId.getIndex().getUUID())
                        .addTag(RTFMetrics.CommonDimension.SHARD_ID.toString(), shardId.getId())
                        .addTag(RTFMetrics.CommonDimension.OPERATION.toString(), operation)
                        .addTag(RTFMetrics.CommonDimension.FAILED.toString(), isFailed)
                        .addTag(
                                RTFMetrics.CommonDimension.SHARD_ROLE.toString(),
                                primary ? SHARD_ROLE_PRIMARY : SHARD_ROLE_REPLICA));
    }

    // This function is called from the security plugin using reflection. Do not
    // remove this function without changing the security plugin.
    public TransportChannel getInnerChannel() {
        return this.original;
    }
}
