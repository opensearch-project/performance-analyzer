/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.transport;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.Version;
import org.opensearch.core.transport.TransportResponse;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.ShardBulkDimension;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.ShardBulkMetric;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.commons.util.ThreadIDUtil;
import org.opensearch.transport.TransportChannel;

public class PerformanceAnalyzerTransportChannel implements TransportChannel, MetricsProcessor {
    private static final Logger LOG =
            LogManager.getLogger(PerformanceAnalyzerTransportChannel.class);
    private static final int KEYS_PATH_LENGTH = 3;
    private static final AtomicLong UNIQUE_ID = new AtomicLong(0);

    private TransportChannel original;
    private String indexName;
    private int shardId;
    private boolean primary;
    private String id;
    private String threadID;

    void set(
            TransportChannel original,
            long startTime,
            String indexName,
            int shardId,
            int itemCount,
            boolean bPrimary) {
        this.original = original;
        this.id = String.valueOf(UNIQUE_ID.getAndIncrement());
        this.indexName = indexName;
        this.shardId = shardId;
        this.primary = bPrimary;
        this.threadID = String.valueOf(ThreadIDUtil.INSTANCE.getNativeCurrentThreadId());

        StringBuilder value =
                new StringBuilder()
                        .append(PerformanceAnalyzerMetrics.getCurrentTimeMetric())
                        .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                        .append(ShardBulkMetric.START_TIME.toString())
                        .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                        .append(startTime)
                        .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                        .append(ShardBulkMetric.ITEM_COUNT.toString())
                        .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                        .append(itemCount)
                        .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                        .append(ShardBulkDimension.INDEX_NAME.toString())
                        .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                        .append(indexName)
                        .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                        .append(ShardBulkDimension.SHARD_ID.toString())
                        .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                        .append(shardId)
                        .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                        .append(ShardBulkDimension.PRIMARY.toString())
                        .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                        .append(bPrimary);

        saveMetricValues(
                value.toString(),
                startTime,
                threadID,
                id,
                PerformanceAnalyzerMetrics.START_FILE_NAME);
    }

    @Override
    public String getProfileName() {
        return this.original.getProfileName();
    }

    @Override
    public String getChannelType() {
        return this.original.getChannelType();
    }

    @Override
    public <T> Optional<T> get(String name, Class<T> clazz) {
        return this.original.get(name, clazz);
    }

    @Override
    public Version getVersion() {
        return this.original.getVersion();
    }

    @Override
    public void sendResponse(TransportResponse response) throws IOException {
        emitMetricsFinish(null);
        original.sendResponse(response);
    }

    @Override
    public void sendResponse(Exception exception) throws IOException {
        emitMetricsFinish(exception);
        original.sendResponse(exception);
    }

    private void emitMetricsFinish(Exception exception) {
        long currTime = System.currentTimeMillis();
        StringBuilder value =
                new StringBuilder()
                        .append(PerformanceAnalyzerMetrics.getCurrentTimeMetric())
                        .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                        .append(ShardBulkMetric.FINISH_TIME.toString())
                        .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                        .append(currTime)
                        .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                        .append(ShardBulkDimension.INDEX_NAME.toString())
                        .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                        .append(indexName)
                        .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                        .append(ShardBulkDimension.SHARD_ID.toString())
                        .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                        .append(shardId)
                        .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                        .append(ShardBulkDimension.PRIMARY.toString())
                        .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                        .append(primary);
        if (exception != null) {
            value.append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                    .append(ShardBulkDimension.EXCEPTION.toString())
                    .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                    .append(exception.getClass().getName());
            value.append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                    .append(ShardBulkDimension.FAILED.toString())
                    .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                    .append(true);
        } else {
            value.append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                    .append(ShardBulkDimension.FAILED.toString())
                    .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                    .append(false);
        }

        saveMetricValues(
                value.toString(),
                currTime,
                threadID,
                id,
                PerformanceAnalyzerMetrics.FINISH_FILE_NAME);
    }

    // This function is called from the security plugin using reflection. Do not
    // remove this function without changing the security plugin.
    public TransportChannel getInnerChannel() {
        return this.original;
    }

    @Override
    public String getMetricsPath(long startTime, String... keysPath) {
        // throw exception if keys.length is not equal to 3 (Keys should be threadID, ShardBulkId,
        // start/finish)
        if (keysPath.length != KEYS_PATH_LENGTH) {
            throw new RuntimeException("keys length should be " + KEYS_PATH_LENGTH);
        }
        return PerformanceAnalyzerMetrics.generatePath(
                startTime,
                PerformanceAnalyzerMetrics.sThreadsPath,
                keysPath[0],
                PerformanceAnalyzerMetrics.sShardBulkPath,
                keysPath[1],
                keysPath[2]);
    }
}
