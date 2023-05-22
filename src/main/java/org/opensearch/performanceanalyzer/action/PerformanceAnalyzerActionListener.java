/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.action;


import org.opensearch.OpenSearchException;
import org.opensearch.action.ActionListener;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.HttpDimension;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.HttpMetric;

public class PerformanceAnalyzerActionListener<Response>
        implements ActionListener<Response>, MetricsProcessor {

    private RequestType type;
    private ActionListener<Response> original;
    private String id;
    private static final int KEYS_PATH_LENGTH = 3;

    void set(RequestType type, String id, ActionListener<Response> original) {
        this.type = type;
        this.id = id;
        this.original = original;
    }

    @Override
    public void onResponse(Response response) {
        int responseStatus = -1;

        if (response instanceof BulkResponse) {
            BulkResponse bulk = (BulkResponse) response;
            responseStatus = bulk.status().getStatus();
        } else if (response instanceof SearchResponse) {
            SearchResponse search = (SearchResponse) response;
            responseStatus = search.status().getStatus();
        }

        // - If response type is BulkResponse/SearchResponse, responseStatus will not be -1
        if (responseStatus != -1) {
            long currTime = System.currentTimeMillis();
            saveMetricValues(
                    generateFinishMetrics(currTime, responseStatus, ""),
                    currTime,
                    type.toString(),
                    id,
                    PerformanceAnalyzerMetrics.FINISH_FILE_NAME);
        }

        original.onResponse(response);
    }

    @Override
    public void onFailure(Exception exception) {
        long currTime = System.currentTimeMillis();

        if (exception instanceof OpenSearchException) {
            saveMetricValues(
                    generateFinishMetrics(
                            currTime,
                            ((OpenSearchException) exception).status().getStatus(),
                            exception.getClass().getName()),
                    currTime,
                    type.toString(),
                    id,
                    PerformanceAnalyzerMetrics.FINISH_FILE_NAME);
        } else {
            saveMetricValues(
                    generateFinishMetrics(currTime, -1, exception.getClass().getName()),
                    currTime,
                    type.toString(),
                    id,
                    PerformanceAnalyzerMetrics.FINISH_FILE_NAME);
        }

        original.onFailure(exception);
    }

    static String generateStartMetrics(long startTime, String indices, int itemCount) {
        return new StringBuilder()
                .append(PerformanceAnalyzerMetrics.getCurrentTimeMetric())
                .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                .append(HttpMetric.START_TIME.toString())
                .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                .append(startTime)
                .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                .append(HttpDimension.INDICES.toString())
                .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                .append(indices)
                .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                .append(HttpMetric.HTTP_REQUEST_DOCS.toString())
                .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                .append(itemCount)
                .toString();
    }

    static String generateFinishMetrics(long finishTime, int status, String exception) {
        return new StringBuilder()
                .append(PerformanceAnalyzerMetrics.getCurrentTimeMetric())
                .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                .append(HttpMetric.FINISH_TIME.toString())
                .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                .append(finishTime)
                .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                .append(HttpDimension.HTTP_RESP_CODE.toString())
                .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                .append(status)
                .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                .append(HttpDimension.EXCEPTION.toString())
                .append(PerformanceAnalyzerMetrics.sKeyValueDelimitor)
                .append(exception)
                .toString();
    }

    @Override
    public String getMetricsPath(long startTime, String... keysPath) {
        // throw exception if keys.length is not equal to 3 (Keys should be requestType, requestID,
        // start/finish)
        if (keysPath.length != KEYS_PATH_LENGTH) {
            throw new RuntimeException("keys length should be " + KEYS_PATH_LENGTH);
        }

        return PerformanceAnalyzerMetrics.generatePath(
                startTime,
                PerformanceAnalyzerMetrics.sThreadsPath,
                PerformanceAnalyzerMetrics.sHttpPath,
                keysPath[0],
                keysPath[1],
                keysPath[2]);
    }
}
