/*
 * Copyright 2019-2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.amazon.opendistro.opensearch.performanceanalyzer.collectors;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;
import static org.mockito.MockitoAnnotations.initMocks;

import com.amazon.opendistro.opensearch.performanceanalyzer.OpenSearchResources;
import com.amazon.opendistro.opensearch.performanceanalyzer.config.PluginSettings;
import com.amazon.opendistro.opensearch.performanceanalyzer.metrics.AllMetrics.MasterPendingValue;
import com.amazon.opendistro.opensearch.performanceanalyzer.metrics.MetricsConfiguration;
import com.amazon.opendistro.opensearch.performanceanalyzer.metrics.PerformanceAnalyzerMetrics;
import com.amazon.opendistro.opensearch.performanceanalyzer.reader_writer_shared.Event;
import com.amazon.opendistro.opensearch.performanceanalyzer.util.TestUtil;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opensearch.cluster.service.ClusterService;
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
