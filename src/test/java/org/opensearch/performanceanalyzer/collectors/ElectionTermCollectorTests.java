/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.MockitoAnnotations.initMocks;

import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.config.PluginSettings;
import org.opensearch.performanceanalyzer.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.ElectionTermValue;
import org.opensearch.performanceanalyzer.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.reader_writer_shared.Event;
import org.opensearch.performanceanalyzer.util.TestUtil;
import org.opensearch.test.ClusterServiceUtils;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class ElectionTermCollectorTests {
    private ElectionTermCollector electionTermCollector;
    private long startTimeInMills = 1153721339;
    private ThreadPool threadPool;
    private PerformanceAnalyzerController controller;
    private ConfigOverridesWrapper configOverrides;

    @Mock private ClusterService mockedClusterService;

    @Before
    public void init() {
        initMocks(this);
        System.setProperty("performanceanalyzer.metrics.log.enabled", "False");
        threadPool = new TestThreadPool("test");
        ClusterService clusterService = ClusterServiceUtils.createClusterService(threadPool);
        OpenSearchResources.INSTANCE.setClusterService(clusterService);
        controller = Mockito.mock(PerformanceAnalyzerController.class);
        configOverrides = Mockito.mock(ConfigOverridesWrapper.class);

        MetricsConfiguration.CONFIG_MAP.put(
                ElectionTermCollector.class, MetricsConfiguration.cdefault);
        electionTermCollector = new ElectionTermCollector(controller, configOverrides);

        // clean metricQueue before running every test
        TestUtil.readEvents();
    }

    @After
    public void tearDown() {
        threadPool.shutdownNow();
    }

    @Test
    public void testGetMetricPath() {
        String expectedPath =
                PluginSettings.instance().getMetricsLocation()
                        + PerformanceAnalyzerMetrics.getTimeInterval(startTimeInMills)
                        + "/"
                        + PerformanceAnalyzerMetrics.sElectionTermPath;
        String actualPath = electionTermCollector.getMetricsPath(startTimeInMills);
        assertEquals(expectedPath, actualPath);

        try {
            electionTermCollector.getMetricsPath(startTimeInMills, "current");
            fail("Negative scenario test: Should have been a RuntimeException");
        } catch (RuntimeException ex) {
            // - expecting exception...1 value passed; 0 expected
        }
    }

    @Test
    public void testCollectMetrics() {
        Mockito.when(controller.isCollectorEnabled(configOverrides, "ElectionTermCollector"))
                .thenReturn(true);
        electionTermCollector.collectMetrics(startTimeInMills);
        String jsonStr = readMetricsInJsonString(1);
        String[] jsonStrArray = jsonStr.split(":", 2);
        assertTrue(jsonStrArray[0].contains(ElectionTermValue.Constants.ELECTION_TERM_VALUE));
        assertTrue(jsonStrArray[1].contains("0"));
    }

    @Test
    public void testWithMockClusterService() {
        OpenSearchResources.INSTANCE.setClusterService(mockedClusterService);
        electionTermCollector.collectMetrics(startTimeInMills);
        String jsonStr = readMetricsInJsonString(0);
        assertNull(jsonStr);

        OpenSearchResources.INSTANCE.setClusterService(null);
        electionTermCollector.collectMetrics(startTimeInMills);
        jsonStr = readMetricsInJsonString(0);
        assertNull(jsonStr);
    }

    private String readMetricsInJsonString(int size) {
        List<Event> metrics = TestUtil.readEvents();
        assert metrics.size() == size;
        if (size != 0) {
            String[] jsonStrs = metrics.get(0).value.split("\n");
            assert jsonStrs.length == 2;
            return jsonStrs[1];
        } else {
            return null;
        }
    }
}
