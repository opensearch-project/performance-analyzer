/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.config.PluginSettings;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.event_process.Event;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.ClusterManagerPendingValue;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.util.TestUtil;
import org.opensearch.test.ClusterServiceUtils;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class ClusterManagerServiceMetricsTests {
    private ClusterManagerServiceMetrics clusterManagerServiceMetrics;
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
                ClusterManagerServiceMetrics.class, MetricsConfiguration.cdefault);
        PerformanceAnalyzerController mockController = mock(PerformanceAnalyzerController.class);
        ConfigOverridesWrapper mockWrapper = mock(ConfigOverridesWrapper.class);
        Mockito.when(mockController.isCollectorDisabled(any(), anyString())).thenReturn(false);
        Mockito.when(mockController.rcaCollectorsEnabled()).thenReturn(true);
        Mockito.when(mockController.telemetryCollectorsEnabled()).thenReturn(true);
        clusterManagerServiceMetrics =
                new ClusterManagerServiceMetrics(mockController, mockWrapper);

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
                clusterManagerServiceMetrics.getMetricsPath(
                        startTimeInMills, "current", PerformanceAnalyzerMetrics.FINISH_FILE_NAME);
        assertEquals(expectedPath, actualPath);

        try {
            clusterManagerServiceMetrics.getMetricsPath(startTimeInMills, "current");
            fail("Negative scenario test: Should have been a RuntimeException");
        } catch (RuntimeException ex) {
            // - expecting exception...1 values passed; 2 expected
        }
    }

    @Test
    public void testCollectMetrics() {
        clusterManagerServiceMetrics.run();
        String jsonStr = readMetricsInJsonString(1);
        assertFalse(
                jsonStr.contains(ClusterManagerPendingValue.Constants.PENDING_TASKS_COUNT_VALUE));
    }

    @Test
    public void testWithMockClusterService() {
        OpenSearchResources.INSTANCE.setClusterService(mockedClusterService);
        clusterManagerServiceMetrics.run();
        String jsonStr = readMetricsInJsonString(0);
        assertNull(jsonStr);

        OpenSearchResources.INSTANCE.setClusterService(mockedClusterService);
        when(mockedClusterService.getClusterManagerService()).thenThrow(new RuntimeException());
        clusterManagerServiceMetrics.run();
        jsonStr = readMetricsInJsonString(0);
        assertNull(jsonStr);

        OpenSearchResources.INSTANCE.setClusterService(null);
        clusterManagerServiceMetrics.run();
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
