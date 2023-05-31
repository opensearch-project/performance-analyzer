/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.listener;

import static org.junit.Assert.*;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opensearch.index.shard.ShardId;
import org.opensearch.performanceanalyzer.commons.collectors.StatsCollector;
import org.opensearch.performanceanalyzer.commons.config.PluginSettings;
import org.opensearch.performanceanalyzer.commons.jvm.ThreadList;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.util.TestUtil;
import org.opensearch.performanceanalyzer.util.Utils;
import org.opensearch.search.internal.SearchContext;
import org.opensearch.search.internal.ShardSearchRequest;

public class PerformanceAnalyzerSearchListenerTests {
    private static final long TOOK_IN_NANOS = 10;
    private static final String EXCEPTION =
            StatExceptionCode.OPENSEARCH_REQUEST_INTERCEPTOR_ERROR.toString();

    private PerformanceAnalyzerSearchListener searchListener;
    private StatsCollector statsCollector;
    private long startTimeInMills = 1253721339;
    private final AtomicInteger errorCount = new AtomicInteger(0);

    @Mock private SearchContext searchContext;
    @Mock private ShardSearchRequest shardSearchRequest;
    @Mock private ShardId shardId;
    @Mock private PerformanceAnalyzerController controller;

    @BeforeClass
    public static void setup() {
        // this test only runs in Linux system
        // as some of the static members of the ThreadList class are specific to Linux
        org.junit.Assume.assumeTrue(SystemUtils.IS_OS_LINUX);
    }

    @Before
    public void init() {
        initMocks(this);
        Mockito.when(controller.isPerformanceAnalyzerEnabled()).thenReturn(true);
        Utils.configureMetrics();
        MetricsConfiguration.CONFIG_MAP.put(ThreadList.class, MetricsConfiguration.cdefault);
        searchListener = new PerformanceAnalyzerSearchListener(controller);
        assertEquals(
                PerformanceAnalyzerSearchListener.class.getSimpleName(), searchListener.toString());

        statsCollector = StatsCollector.instance();

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
                        + "SearchThread"
                        + "/"
                        + "ShardQuery"
                        + "/"
                        + "ShardSearchID"
                        + "/"
                        + PerformanceAnalyzerMetrics.FINISH_FILE_NAME;
        String actualPath =
                searchListener.getMetricsPath(
                        startTimeInMills,
                        "SearchThread",
                        "ShardQuery",
                        "ShardSearchID",
                        PerformanceAnalyzerMetrics.FINISH_FILE_NAME);
        assertEquals(expectedPath, actualPath);

        try {
            searchListener.getMetricsPath(
                    startTimeInMills, "SearchThread", "ShardQuery", "ShardSearchID");
            fail("Negative scenario test: Should have been a RuntimeException");
        } catch (RuntimeException ex) {
            // - expecting exception...3 values passed; 4 expected
        }
    }

    @Test
    public void testOnPreQueryPhase() {
        initializeValidSearchContext(true);
        searchListener.onPreQueryPhase(searchContext);
        List<String> jsonStrs = TestUtil.readMetricsInJsonString(4);
        assertTrue(jsonStrs.get(0).contains(AllMetrics.CommonMetric.START_TIME.toString()));
        assertTrue(jsonStrs.get(1).contains(AllMetrics.CommonDimension.INDEX_NAME.toString()));
        assertTrue(jsonStrs.get(2).contains(AllMetrics.CommonDimension.SHARD_ID.toString()));
    }

    @Test
    public void testOnQueryPhase() {
        initializeValidSearchContext(true);
        searchListener.onQueryPhase(searchContext, TOOK_IN_NANOS);
        List<String> jsonStrs = TestUtil.readMetricsInJsonString(5);
        assertTrue(jsonStrs.get(0).contains(AllMetrics.CommonMetric.FINISH_TIME.toString()));
        assertTrue(jsonStrs.get(1).contains(AllMetrics.CommonDimension.FAILED.toString()));
        assertTrue(jsonStrs.get(1).contains("false"));
        assertTrue(jsonStrs.get(2).contains(AllMetrics.CommonDimension.INDEX_NAME.toString()));
        assertTrue(jsonStrs.get(3).contains(AllMetrics.CommonDimension.SHARD_ID.toString()));
    }

    @Test
    public void testOnFailedQueryPhase() {
        initializeValidSearchContext(true);
        searchListener.onFailedQueryPhase(searchContext);
        List<String> jsonStrs = TestUtil.readMetricsInJsonString(5);
        assertTrue(jsonStrs.get(0).contains(AllMetrics.CommonMetric.FINISH_TIME.toString()));
        assertTrue(jsonStrs.get(1).contains(AllMetrics.CommonDimension.FAILED.toString()));
        assertTrue(jsonStrs.get(1).contains("true"));
        assertTrue(jsonStrs.get(2).contains(AllMetrics.CommonDimension.INDEX_NAME.toString()));
        assertTrue(jsonStrs.get(3).contains(AllMetrics.CommonDimension.SHARD_ID.toString()));
    }

    @Test
    public void testOnPreFetchPhase() {
        initializeValidSearchContext(true);
        searchListener.onPreFetchPhase(searchContext);
        List<String> jsonStrs = TestUtil.readMetricsInJsonString(4);
        assertTrue(jsonStrs.get(0).contains(AllMetrics.CommonMetric.START_TIME.toString()));
        assertTrue(jsonStrs.get(1).contains(AllMetrics.CommonDimension.INDEX_NAME.toString()));
        assertTrue(jsonStrs.get(2).contains(AllMetrics.CommonDimension.SHARD_ID.toString()));
    }

    @Test
    public void testOnFetchPhase() {
        initializeValidSearchContext(true);
        searchListener.onFetchPhase(searchContext, TOOK_IN_NANOS);
        List<String> jsonStrs = TestUtil.readMetricsInJsonString(5);
        assertTrue(jsonStrs.get(0).contains(AllMetrics.CommonMetric.FINISH_TIME.toString()));
        assertTrue(jsonStrs.get(1).contains(AllMetrics.CommonDimension.FAILED.toString()));
        assertTrue(jsonStrs.get(1).contains("false"));
        assertTrue(jsonStrs.get(2).contains(AllMetrics.CommonDimension.INDEX_NAME.toString()));
        assertTrue(jsonStrs.get(3).contains(AllMetrics.CommonDimension.SHARD_ID.toString()));
    }

    @Test
    public void testOnFailedFetchPhase() {
        initializeValidSearchContext(true);
        searchListener.onFailedFetchPhase(searchContext);
        List<String> jsonStrs = TestUtil.readMetricsInJsonString(5);
        assertTrue(jsonStrs.get(0).contains(AllMetrics.CommonMetric.FINISH_TIME.toString()));
        assertTrue(jsonStrs.get(1).contains(AllMetrics.CommonDimension.FAILED.toString()));
        assertTrue(jsonStrs.get(1).contains("true"));
        assertTrue(jsonStrs.get(2).contains(AllMetrics.CommonDimension.INDEX_NAME.toString()));
        assertTrue(jsonStrs.get(3).contains(AllMetrics.CommonDimension.SHARD_ID.toString()));
    }

    @Ignore
    @Test
    public void testInvalidSearchContext() {
        initializeValidSearchContext(false);

        searchListener.onFailedFetchPhase(searchContext);
        assertEquals(
                errorCount.incrementAndGet(),
                statsCollector.getCounters().get(EXCEPTION).intValue());
        searchListener.onPreFetchPhase(searchContext);
        assertEquals(
                errorCount.incrementAndGet(),
                statsCollector.getCounters().get(EXCEPTION).intValue());
        searchListener.onFetchPhase(searchContext, TOOK_IN_NANOS);
        assertEquals(
                errorCount.incrementAndGet(),
                statsCollector.getCounters().get(EXCEPTION).intValue());
        searchListener.onPreQueryPhase(searchContext);
        assertEquals(
                errorCount.incrementAndGet(),
                statsCollector.getCounters().get(EXCEPTION).intValue());
        searchListener.onFailedQueryPhase(searchContext);
        assertEquals(
                errorCount.incrementAndGet(),
                statsCollector.getCounters().get(EXCEPTION).intValue());
        searchListener.onQueryPhase(searchContext, TOOK_IN_NANOS);
        assertEquals(
                errorCount.incrementAndGet(),
                statsCollector.getCounters().get(EXCEPTION).intValue());
    }

    private void initializeValidSearchContext(boolean isValid) {
        if (isValid) {
            Mockito.when(searchContext.request()).thenReturn(shardSearchRequest);
            Mockito.when(shardSearchRequest.shardId()).thenReturn(shardId);
            Mockito.when(shardId.getIndexName()).thenReturn("shardIndex");
            Mockito.when(shardId.getId()).thenReturn(1);
        } else {
            Mockito.when(searchContext.request()).thenReturn(null);
        }
    }
}
