/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.action;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.opensearch.OpenSearchException;
import org.opensearch.action.ActionListener;
import org.opensearch.action.ActionResponse;
import org.opensearch.action.bulk.BulkResponse;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.performanceanalyzer.commons.event_process.Event;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.config.PluginSettings;
import org.opensearch.performanceanalyzer.util.TestUtil;
import org.opensearch.rest.RestStatus;

@SuppressWarnings("unchecked")
public class PerformanceAnalyzerActionListenerTests {
    private static final String listenerId = "12345";
    private long startTimeInMills = 1153721339;
    private PerformanceAnalyzerActionListener actionListener;
    private ActionListener originalActionListener;

    @Before
    public void init() {
        originalActionListener = Mockito.mock(ActionListener.class);
        actionListener = new PerformanceAnalyzerActionListener();

        // clean metricQueue before running every test
        TestUtil.readEvents();
    }

    @Test
    public void testGetMetricsPath() {
        String expectedPath =
                PluginSettings.instance().getMetricsLocation()
                        + PerformanceAnalyzerMetrics.getTimeInterval(startTimeInMills)
                        + "/"
                        + PerformanceAnalyzerMetrics.sThreadsPath
                        + "/"
                        + PerformanceAnalyzerMetrics.sHttpPath
                        + "/"
                        + "bulk/bulkId/start";
        String actualPath =
                actionListener.getMetricsPath(startTimeInMills, "bulk", "bulkId", "start");
        assertEquals(expectedPath, actualPath);

        try {
            actionListener.getMetricsPath(startTimeInMills, "bulk");
            fail("Negative scenario test: Should have been a RuntimeException");
        } catch (RuntimeException ex) {
            // - expecting exception...1 values passed; 3 expected
        }
    }

    @Test
    public void testOnResponseWithBulkResponse() {
        BulkResponse bulkResponse = Mockito.mock(BulkResponse.class);
        Mockito.when(bulkResponse.status()).thenReturn(RestStatus.OK);
        actionListener.set(RequestType.bulk, listenerId, originalActionListener);
        testOnResponse(bulkResponse);
    }

    @Test
    public void testOnResponseWithSearchResponse() {
        SearchResponse searchResponse = Mockito.mock(SearchResponse.class);
        Mockito.when(searchResponse.status()).thenReturn(RestStatus.OK);
        actionListener.set(RequestType.search, listenerId, originalActionListener);
        testOnResponse(searchResponse);
    }

    @Test
    public void testOnFailureWithOpenSearchException() {
        OpenSearchException exception = Mockito.mock(OpenSearchException.class);
        Mockito.when(exception.status()).thenReturn(RestStatus.INTERNAL_SERVER_ERROR);
        actionListener.set(RequestType.search, listenerId, originalActionListener);
        actionListener.onFailure(exception);
        String[] metricsValues = readMetricsValue();
        assertEquals("HTTPRespCode:500", metricsValues[2]);
        assertTrue(metricsValues[3].contains("Exception:org.opensearch.OpenSearchException"));
    }

    @Test
    public void testOnFailureWithException() {
        NullPointerException exception = new NullPointerException();
        actionListener.set(RequestType.search, listenerId, originalActionListener);
        actionListener.onFailure(exception);
        String[] metricsValues = readMetricsValue();
        assertEquals("HTTPRespCode:-1", metricsValues[2]);
        assertTrue(metricsValues[3].contains("Exception:java.lang.NullPointerException"));
    }

    private void testOnResponse(ActionResponse response) {
        actionListener.onResponse(response);

        String[] metricsValues = readMetricsValue();
        assertEquals("HTTPRespCode:200", metricsValues[2]);
        assertEquals("Exception:", metricsValues[3]);
    }

    private String[] readMetricsValue() {
        List<Event> metrics = TestUtil.readEvents();
        assert metrics.size() == 1;
        String[] metricsValues = metrics.get(0).value.split("\n");
        assert metricsValues.length == 4;
        return metricsValues;
    }
}
