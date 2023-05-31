/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.listener;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.index.shard.SearchOperationListener;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.CommonDimension;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.CommonMetric;
import org.opensearch.performanceanalyzer.commons.metrics.ExceptionsAndErrors;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.commons.stats.CommonStats;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.metrics.ThreadIDUtil;
import org.opensearch.search.internal.SearchContext;

public class PerformanceAnalyzerSearchListener
        implements SearchOperationListener, SearchListener, MetricsProcessor {
    private static final Logger LOG = LogManager.getLogger(PerformanceAnalyzerSearchListener.class);

    private static final SearchListener NO_OP_SEARCH_LISTENER = new NoOpSearchListener();
    private static final int KEYS_PATH_LENGTH = 4;
    private final PerformanceAnalyzerController controller;
    private SearchListener searchListener;

    public PerformanceAnalyzerSearchListener(final PerformanceAnalyzerController controller) {
        this.controller = controller;
    }

    @Override
    public String toString() {
        return PerformanceAnalyzerSearchListener.class.getSimpleName();
    }

    private SearchListener getSearchListener() {
        return controller.isPerformanceAnalyzerEnabled() ? this : NO_OP_SEARCH_LISTENER;
    }

    @Override
    public void onPreQueryPhase(SearchContext searchContext) {
        try {
            getSearchListener().preQueryPhase(searchContext);
        } catch (Exception ex) {
            LOG.error(ex);
            CommonStats.WRITER_METRICS_AGGREGATOR.updateStat(
                    ExceptionsAndErrors.OPENSEARCH_REQUEST_INTERCEPTOR_ERROR, "", 1);
        }
    }

    @Override
    public void onQueryPhase(SearchContext searchContext, long tookInNanos) {
        try {
            getSearchListener().queryPhase(searchContext, tookInNanos);
        } catch (Exception ex) {
            LOG.error(ex);
            CommonStats.WRITER_METRICS_AGGREGATOR.updateStat(
                    ExceptionsAndErrors.OPENSEARCH_REQUEST_INTERCEPTOR_ERROR, "", 1);
        }
    }

    @Override
    public void onFailedQueryPhase(SearchContext searchContext) {
        try {
            getSearchListener().failedQueryPhase(searchContext);
        } catch (Exception ex) {
            LOG.error(ex);
            CommonStats.WRITER_METRICS_AGGREGATOR.updateStat(
                    ExceptionsAndErrors.OPENSEARCH_REQUEST_INTERCEPTOR_ERROR, "", 1);
        }
    }

    @Override
    public void onPreFetchPhase(SearchContext searchContext) {
        try {
            getSearchListener().preFetchPhase(searchContext);
        } catch (Exception ex) {
            LOG.error(ex);
            CommonStats.WRITER_METRICS_AGGREGATOR.updateStat(
                    ExceptionsAndErrors.OPENSEARCH_REQUEST_INTERCEPTOR_ERROR, "", 1);
        }
    }

    @Override
    public void onFetchPhase(SearchContext searchContext, long tookInNanos) {
        try {
            getSearchListener().fetchPhase(searchContext, tookInNanos);
        } catch (Exception ex) {
            LOG.error(ex);
            CommonStats.WRITER_METRICS_AGGREGATOR.updateStat(
                    ExceptionsAndErrors.OPENSEARCH_REQUEST_INTERCEPTOR_ERROR, "", 1);
        }
    }

    @Override
    public void onFailedFetchPhase(SearchContext searchContext) {
        try {
            getSearchListener().failedFetchPhase(searchContext);
        } catch (Exception ex) {
            LOG.error(ex);
            CommonStats.WRITER_METRICS_AGGREGATOR.updateStat(
                    ExceptionsAndErrors.OPENSEARCH_REQUEST_INTERCEPTOR_ERROR, "", 1);
        }
    }

    @Override
    public void preQueryPhase(SearchContext searchContext) {
        long currTime = System.currentTimeMillis();
        saveMetricValues(
                generateStartMetrics(
                        currTime,
                        searchContext.request().shardId().getIndexName(),
                        searchContext.request().shardId().getId()),
                currTime,
                String.valueOf(ThreadIDUtil.INSTANCE.getNativeCurrentThreadId()),
                PerformanceAnalyzerMetrics.sShardQueryPath,
                String.valueOf(searchContext.id()),
                PerformanceAnalyzerMetrics.START_FILE_NAME);
    }

    @Override
    public void queryPhase(SearchContext searchContext, long tookInNanos) {
        long currTime = System.currentTimeMillis();
        saveMetricValues(
                generateFinishMetrics(
                        currTime,
                        false,
                        searchContext.request().shardId().getIndexName(),
                        searchContext.request().shardId().getId()),
                currTime,
                String.valueOf(ThreadIDUtil.INSTANCE.getNativeCurrentThreadId()),
                PerformanceAnalyzerMetrics.sShardQueryPath,
                String.valueOf(searchContext.id()),
                PerformanceAnalyzerMetrics.FINISH_FILE_NAME);
    }

    @Override
    public void failedQueryPhase(SearchContext searchContext) {
        long currTime = System.currentTimeMillis();
        saveMetricValues(
                generateFinishMetrics(
                        currTime,
                        true,
                        searchContext.request().shardId().getIndexName(),
                        searchContext.request().shardId().getId()),
                currTime,
                String.valueOf(ThreadIDUtil.INSTANCE.getNativeCurrentThreadId()),
                PerformanceAnalyzerMetrics.sShardQueryPath,
                String.valueOf(searchContext.id()),
                PerformanceAnalyzerMetrics.FINISH_FILE_NAME);
    }

    @Override
    public void preFetchPhase(SearchContext searchContext) {
        long currTime = System.currentTimeMillis();
        saveMetricValues(
                generateStartMetrics(
                        currTime,
                        searchContext.request().shardId().getIndexName(),
                        searchContext.request().shardId().getId()),
                currTime,
                String.valueOf(ThreadIDUtil.INSTANCE.getNativeCurrentThreadId()),
                PerformanceAnalyzerMetrics.sShardFetchPath,
                String.valueOf(searchContext.id()),
                PerformanceAnalyzerMetrics.START_FILE_NAME);
    }

    @Override
    public void fetchPhase(SearchContext searchContext, long tookInNanos) {
        long currTime = System.currentTimeMillis();
        saveMetricValues(
                generateFinishMetrics(
                        currTime,
                        false,
                        searchContext.request().shardId().getIndexName(),
                        searchContext.request().shardId().getId()),
                currTime,
                String.valueOf(ThreadIDUtil.INSTANCE.getNativeCurrentThreadId()),
                PerformanceAnalyzerMetrics.sShardFetchPath,
                String.valueOf(searchContext.id()),
                PerformanceAnalyzerMetrics.FINISH_FILE_NAME);
    }

    @Override
    public void failedFetchPhase(SearchContext searchContext) {
        long currTime = System.currentTimeMillis();
        saveMetricValues(
                generateFinishMetrics(
                        currTime,
                        true,
                        searchContext.request().shardId().getIndexName(),
                        searchContext.request().shardId().getId()),
                currTime,
                String.valueOf(ThreadIDUtil.INSTANCE.getNativeCurrentThreadId()),
                PerformanceAnalyzerMetrics.sShardFetchPath,
                String.valueOf(searchContext.id()),
                PerformanceAnalyzerMetrics.FINISH_FILE_NAME);
    }

    @SuppressWarnings("checkstyle:magicnumber")
    @Override
    public String getMetricsPath(long startTime, String... keysPath) {
        // throw exception if keys.length is not equal to 4 (Keys should be threadID, SearchType,
        // ShardSearchID, start/finish)
        if (keysPath.length != KEYS_PATH_LENGTH) {
            throw new RuntimeException("keys length should be " + KEYS_PATH_LENGTH);
        }

        return PerformanceAnalyzerMetrics.generatePath(
                startTime,
                PerformanceAnalyzerMetrics.sThreadsPath,
                keysPath[0],
                keysPath[1],
                keysPath[2],
                keysPath[3]);
    }

    public static String generateStartMetrics(long startTime, String indexName, int shardId) {
        return new StringBuilder()
                .append(PerformanceAnalyzerMetrics.getCurrentTimeMetric())
                .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                .append(CommonMetric.START_TIME.toString())
                .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                .append(startTime)
                .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                .append(CommonDimension.INDEX_NAME.toString())
                .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                .append(indexName)
                .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                .append(CommonDimension.SHARD_ID.toString())
                .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                .append(shardId)
                .toString();
    }

    public static String generateFinishMetrics(
            long finishTime, boolean failed, String indexName, int shardId) {
        return new StringBuilder()
                .append(PerformanceAnalyzerMetrics.getCurrentTimeMetric())
                .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                .append(CommonMetric.FINISH_TIME.toString())
                .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                .append(finishTime)
                .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                .append(CommonDimension.FAILED.toString())
                .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                .append(failed)
                .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                .append(CommonDimension.INDEX_NAME.toString())
                .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                .append(indexName)
                .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                .append(CommonDimension.SHARD_ID.toString())
                .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                .append(shardId)
                .toString();
    }
}
