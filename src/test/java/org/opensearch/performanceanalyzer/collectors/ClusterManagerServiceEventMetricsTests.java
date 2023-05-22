/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import org.apache.commons.lang3.SystemUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.cluster.service.SourcePrioritizedRunnable;
import org.opensearch.common.Priority;
import org.opensearch.common.util.concurrent.PrioritizedOpenSearchThreadPoolExecutor;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.event_process.Event;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.config.PluginSettings;
import org.opensearch.performanceanalyzer.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.util.TestUtil;
import org.opensearch.test.ClusterServiceUtils;
import org.opensearch.threadpool.TestThreadPool;
import org.opensearch.threadpool.ThreadPool;

public class ClusterManagerServiceEventMetricsTests {
    private long startTimeInMills = 1153721339;
    private ClusterManagerServiceEventMetrics clusterManagerServiceEventMetrics;
    private ThreadPool threadPool;

    @BeforeClass
    public static void setup() {
        // this test only runs in Linux system
        // as some of the static members of the ThreadList class are specific to Linux
        org.junit.Assume.assumeTrue(SystemUtils.IS_OS_LINUX);
    }

    @Before
    public void init() {
        threadPool = new TestThreadPool("test");
        ClusterService clusterService = ClusterServiceUtils.createClusterService(threadPool);
        OpenSearchResources.INSTANCE.setClusterService(clusterService);

        MetricsConfiguration.CONFIG_MAP.put(
                ClusterManagerServiceEventMetrics.class, MetricsConfiguration.cdefault);
        clusterManagerServiceEventMetrics = new ClusterManagerServiceEventMetrics();

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
                        + PerformanceAnalyzerMetrics.sThreadsPath
                        + "/"
                        + "thread123"
                        + "/"
                        + PerformanceAnalyzerMetrics.sClusterManagerTaskPath
                        + "/"
                        + "task123"
                        + "/"
                        + PerformanceAnalyzerMetrics.FINISH_FILE_NAME;
        String actualPath =
                clusterManagerServiceEventMetrics.getMetricsPath(
                        startTimeInMills,
                        "thread123",
                        "task123",
                        PerformanceAnalyzerMetrics.FINISH_FILE_NAME);
        assertEquals(expectedPath, actualPath);

        try {
            clusterManagerServiceEventMetrics.getMetricsPath(
                    startTimeInMills, "thread123", "task123");
            fail("Negative scenario test: Should have been a RuntimeException");
        } catch (RuntimeException ex) {
            // - expecting exception...2 values passed; 3 expected
        }
    }

    @Test
    public void testGenerateFinishMetrics() {
        assertEquals(-1, clusterManagerServiceEventMetrics.lastTaskInsertionOrder);
        clusterManagerServiceEventMetrics.generateFinishMetrics(startTimeInMills);

        clusterManagerServiceEventMetrics.lastTaskInsertionOrder = 1;
        clusterManagerServiceEventMetrics.generateFinishMetrics(startTimeInMills);
        List<Event> metrics = TestUtil.readEvents();
        String[] jsonStrs = metrics.get(0).value.split("\n");
        assert jsonStrs.length == 2;
        assertTrue(
                jsonStrs[1].contains(AllMetrics.ClusterManagerMetricValues.FINISH_TIME.toString()));
        assertEquals(-1, clusterManagerServiceEventMetrics.lastTaskInsertionOrder);
    }

    @Test
    public void testCollectMetrics() throws Exception {
        PrioritizedOpenSearchThreadPoolExecutor prioritizedOpenSearchThreadPoolExecutor =
                (PrioritizedOpenSearchThreadPoolExecutor)
                        clusterManagerServiceEventMetrics
                                .getClusterManagerServiceTPExecutorField()
                                .get(
                                        OpenSearchResources.INSTANCE
                                                .getClusterService()
                                                .getMasterService());
        SourcePrioritizedRunnable runnable =
                new SourcePrioritizedRunnable(Priority.HIGH, "_add_listener_") {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(100L); // dummy runnable
                        } catch (InterruptedException e) {
                        }
                    }
                };

        prioritizedOpenSearchThreadPoolExecutor.submit(runnable);
        Thread.sleep(1L); // don't delete it

        clusterManagerServiceEventMetrics.collectMetrics(startTimeInMills);
        List<String> jsonStrs = TestUtil.readMetricsInJsonString(6);
        assertTrue(
                jsonStrs.get(0)
                        .contains(
                                AllMetrics.ClusterManagerMetricDimensions
                                        .CLUSTER_MANAGER_TASK_PRIORITY
                                        .toString()));
        assertTrue(
                jsonStrs.get(1)
                        .contains(AllMetrics.ClusterManagerMetricValues.START_TIME.toString()));
        assertTrue(
                jsonStrs.get(2)
                        .contains(
                                AllMetrics.ClusterManagerMetricDimensions.CLUSTER_MANAGER_TASK_TYPE
                                        .toString()));
        assertTrue(
                jsonStrs.get(3)
                        .contains(
                                AllMetrics.ClusterManagerMetricDimensions
                                        .CLUSTER_MANAGER_TASK_METADATA
                                        .toString()));
        assertTrue(
                jsonStrs.get(4)
                        .contains(
                                AllMetrics.ClusterManagerMetricDimensions
                                        .CLUSTER_MANAGER_TASK_QUEUE_TIME
                                        .toString()));
    }
}
