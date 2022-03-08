/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.config.PluginSettings;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.MasterPendingValue;
import org.opensearch.performanceanalyzer.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.reader_writer_shared.Event;
import org.opensearch.performanceanalyzer.util.TestUtil;
import org.opensearch.test.ClusterServiceUtils;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class MasterServiceMetricsTests {
    private MasterServiceMetrics masterServiceMetrics;
    private long startTimeInMills = 1153721339;
    private ThreadPool threadPool;

    @Mock private ClusterService mockedClusterService;

    @Before
    public void init() {
        initMocks(this);
        System.setProperty("performanceanalyzer.metrics.log.enabled", "False");
        threadPool = new TestThreadPool("test");
        ClusterService clusterService = ClusterServiceUtils.createClusterService(threadPool);
        OpenSearchResources.INSTANCE.setClusterService(clusterService);

        MetricsConfiguration.CONFIG_MAP.put(
                MasterServiceMetrics.class, MetricsConfiguration.cdefault);
        masterServiceMetrics = new MasterServiceMetrics();

        // clean metricQueue before running every test
        TestUtil.readEvents();
    }

    @After
    public void tearDown() {
        threadPool.shutdownNow();
    }

    @Test
    public void testGetMetricsPath() {
        String expectedPath =
                PluginSettings.instance().getMetricsLocation()
                        + PerformanceAnalyzerMetrics.getTimeInterval(startTimeInMills)
                        + "/"
                        + PerformanceAnalyzerMetrics.sPendingTasksPath
                        + "/"
                        + "current"
                        + "/"
                        + PerformanceAnalyzerMetrics.FINISH_FILE_NAME;
        String actualPath =
                masterServiceMetrics.getMetricsPath(
                        startTimeInMills, "current", PerformanceAnalyzerMetrics.FINISH_FILE_NAME);
        assertEquals(expectedPath, actualPath);

        try {
            masterServiceMetrics.getMetricsPath(startTimeInMills, "current");
            fail("Negative scenario test: Should have been a RuntimeException");
        } catch (RuntimeException ex) {
            // - expecting exception...1 values passed; 2 expected
        }
    }

    @Test
    public void testCollectMetrics() {
        masterServiceMetrics.collectMetrics(startTimeInMills);
        String jsonStr = readMetricsInJsonString(1);
        assertFalse(jsonStr.contains(MasterPendingValue.Constants.PENDING_TASKS_COUNT_VALUE));
    }

    @Test
    public void testWithMockClusterService() {
        OpenSearchResources.INSTANCE.setClusterService(mockedClusterService);
        masterServiceMetrics.collectMetrics(startTimeInMills);
        String jsonStr = readMetricsInJsonString(0);
        assertNull(jsonStr);

        OpenSearchResources.INSTANCE.setClusterService(mockedClusterService);
        when(mockedClusterService.getMasterService()).thenThrow(new RuntimeException());
        masterServiceMetrics.collectMetrics(startTimeInMills);
        jsonStr = readMetricsInJsonString(0);
        assertNull(jsonStr);

        OpenSearchResources.INSTANCE.setClusterService(null);
        masterServiceMetrics.collectMetrics(startTimeInMills);
        jsonStr = readMetricsInJsonString(0);
        assertNull(jsonStr);
    }

    private String readMetricsInJsonString(int size) {
        List<Event> metrics = TestUtil.readEvents();
        assert metrics.size() == size;
        if (size != 0) {
            String[] jsonStrs = metrics.get(0).value.split("\n");
            assert jsonStrs.length == 1;
            return jsonStrs[0];
        } else {
            return null;
        }
    }
}
