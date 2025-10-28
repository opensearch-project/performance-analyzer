/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.listener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.MockitoAnnotations.initMocks;

import org.apache.commons.lang3.SystemUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opensearch.action.search.SearchShardTask;
import org.opensearch.core.action.NotifyOnceListener;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.tasks.resourcetracker.TaskResourceUsage;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.ShardMetricsCollector;
import org.opensearch.performanceanalyzer.commons.metrics.RTFMetrics;
import org.opensearch.performanceanalyzer.commons.util.Util;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.util.Utils;
import org.opensearch.search.internal.SearchContext;
import org.opensearch.search.internal.ShardSearchRequest;
import org.opensearch.tasks.Task;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;

public class RTFPerformanceAnalyzerSearchListenerTests {

    private RTFPerformanceAnalyzerSearchListener searchListener;

    @Mock private SearchContext searchContext;
    @Mock private ShardSearchRequest shardSearchRequest;
    @Mock private ShardId shardId;
    @Mock private PerformanceAnalyzerController controller;
    @Mock private SearchShardTask task;
    @Mock private MetricsRegistry metricsRegistry;
    @Mock private Histogram cpuUtilizationHistogram;
    @Mock private Histogram heapUsedHistogram;
    @Mock private Histogram searchLatencyHistogram;
    @Mock private Histogram shardMetricsCpuHistogram;
    @Mock private Histogram shardMetricsHeapHistogram;
    @Mock private Index index;

    @Mock private TaskResourceUsage taskResourceUsage;

    @BeforeClass
    public static void setup() {
        // this test only runs in Linux system
        // as some of the static members of the ThreadList class are specific to Linux
        org.junit.Assume.assumeTrue(SystemUtils.IS_OS_LINUX);
    }

    @Before
    public void init() {
        initMocks(this);
        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry);
        Mockito.when(controller.isPerformanceAnalyzerEnabled()).thenReturn(true);

        // First set up metrics registry with most lenient matching
        Mockito.when(
                        metricsRegistry.createHistogram(
                                Mockito.anyString(), Mockito.anyString(), Mockito.anyString()))
                .thenAnswer(
                        invocation -> {
                            String name = invocation.getArgument(0);
                            if (name.equals(ShardMetricsCollector.SHARD_CPU_UTILIZATION)) {
                                return shardMetricsCpuHistogram;
                            } else if (name.equals(ShardMetricsCollector.SHARD_HEAP_ALLOCATED)) {
                                return shardMetricsHeapHistogram;
                            } else if (name.equals(
                                    RTFMetrics.OSMetrics.CPU_UTILIZATION.toString())) {
                                return cpuUtilizationHistogram;
                            } else if (name.equals(
                                    RTFMetrics.OSMetrics.HEAP_ALLOCATED.toString())) {
                                return heapUsedHistogram;
                            } else if (name.equals(
                                    RTFMetrics.ShardOperationsValue.SHARD_SEARCH_LATENCY
                                            .toString())) {
                                return searchLatencyHistogram;
                            }
                            return null;
                        });
        searchListener = new RTFPerformanceAnalyzerSearchListener(controller);
        assertEquals(
                RTFPerformanceAnalyzerSearchListener.class.getSimpleName(),
                searchListener.toString());
    }

    @Test
    public void tesSearchListener() {
        Mockito.when(controller.getCollectorsRunModeValue())
                .thenReturn(Util.CollectorMode.RCA.getValue());
        assertTrue(searchListener.getSearchListener() instanceof NoOpSearchListener);

        Mockito.when(controller.getCollectorsRunModeValue())
                .thenReturn(Util.CollectorMode.TELEMETRY.getValue());
        assertTrue(
                searchListener.getSearchListener() instanceof RTFPerformanceAnalyzerSearchListener);

        Mockito.when(controller.getCollectorsRunModeValue())
                .thenReturn(Util.CollectorMode.DUAL.getValue());
        assertTrue(
                searchListener.getSearchListener() instanceof RTFPerformanceAnalyzerSearchListener);
    }

    @Test
    public void testQueryPhase() {
        initializeValidSearchContext(true);
        Mockito.when(controller.getCollectorsRunModeValue())
                .thenReturn(Util.CollectorMode.TELEMETRY.getValue());
        Mockito.when(shardId.getIndex()).thenReturn(index);
        Mockito.when(index.getName()).thenReturn("myTestIndex");
        Mockito.when(index.getUUID()).thenReturn("abc-def");
        searchListener.preQueryPhase(searchContext);
        searchListener.queryPhase(searchContext, 0l);
        Mockito.verify(task).addResourceTrackingCompletionListener(Mockito.any());
        Mockito.verify(searchLatencyHistogram).record(Mockito.anyDouble(), Mockito.any(Tags.class));
    }

    @Test
    public void testQueryPhaseFailed() {
        initializeValidSearchContext(true);
        Mockito.when(controller.getCollectorsRunModeValue())
                .thenReturn(Util.CollectorMode.TELEMETRY.getValue());
        Mockito.when(shardId.getIndex()).thenReturn(index);
        Mockito.when(index.getName()).thenReturn("myTestIndex");
        Mockito.when(index.getUUID()).thenReturn("abc-def");
        searchListener.preQueryPhase(searchContext);
        searchListener.failedQueryPhase(searchContext);
        Mockito.verify(task).addResourceTrackingCompletionListener(Mockito.any());
    }

    @Test
    public void testFetchPhase() {
        initializeValidSearchContext(true);
        Mockito.when(controller.getCollectorsRunModeValue())
                .thenReturn(Util.CollectorMode.TELEMETRY.getValue());
        Mockito.when(shardId.getIndex()).thenReturn(index);
        Mockito.when(index.getName()).thenReturn("myTestIndex");
        Mockito.when(index.getUUID()).thenReturn("abc-def");
        searchListener.preFetchPhase(searchContext);
        searchListener.fetchPhase(searchContext, 0l);
        Mockito.verify(task).addResourceTrackingCompletionListener(Mockito.any());
        Mockito.verify(searchLatencyHistogram).record(Mockito.anyDouble(), Mockito.any(Tags.class));
    }

    @Test
    public void testFetchPhaseFailed() {
        initializeValidSearchContext(true);
        Mockito.when(controller.getCollectorsRunModeValue())
                .thenReturn(Util.CollectorMode.TELEMETRY.getValue());
        Mockito.when(shardId.getIndex()).thenReturn(index);
        Mockito.when(index.getName()).thenReturn("myTestIndex");
        Mockito.when(index.getUUID()).thenReturn("abc-def");
        searchListener.preFetchPhase(searchContext);
        searchListener.failedFetchPhase(searchContext);
        Mockito.verify(task).addResourceTrackingCompletionListener(Mockito.any());
    }

    @Test
    public void testOperationShareFactor() {
        assertEquals(Double.valueOf(10.0 / 15), Utils.computeShareFactor(10, 15), 0);
        assertEquals(Double.valueOf(1), Utils.computeShareFactor(15, 10), 0);
    }

    @Test
    public void testTaskCompletionListener() {
        Histogram shardCpu = ShardMetricsCollector.INSTANCE.getCpuUtilizationHistogram();
        Histogram shardHeap = ShardMetricsCollector.INSTANCE.getHeapUsedHistogram();

        if (shardCpu != null && shardHeap != null) {
            // Clear any previous mock interactions
            Mockito.clearInvocations(shardCpu, shardHeap);
        }
        ShardMetricsCollector.INSTANCE.initialize();
        initializeValidSearchContext(true);
        RTFPerformanceAnalyzerSearchListener rtfSearchListener =
                new RTFPerformanceAnalyzerSearchListener(controller);

        Mockito.when(shardId.getIndex()).thenReturn(index);
        Mockito.when(index.getName()).thenReturn("myTestIndex");
        Mockito.when(index.getUUID()).thenReturn("abc-def");
        Mockito.when(task.getTotalResourceStats()).thenReturn(taskResourceUsage);
        Mockito.when(taskResourceUsage.getCpuTimeInNanos()).thenReturn(10l);

        NotifyOnceListener<Task> taskCompletionListener =
                rtfSearchListener.createListener(searchContext, 0l, 0l, "test", false);
        taskCompletionListener.onResponse(task);

        Mockito.verify(cpuUtilizationHistogram)
                .record(Mockito.anyDouble(), Mockito.any(Tags.class));
        Mockito.verify(heapUsedHistogram).record(Mockito.anyDouble(), Mockito.any(Tags.class));
        Mockito.verify(shardCpu).record(Mockito.anyDouble(), Mockito.any(Tags.class));
        Mockito.verify(shardHeap).record(Mockito.anyDouble(), Mockito.any(Tags.class));
    }

    private void initializeValidSearchContext(boolean isValid) {
        if (isValid) {
            Mockito.when(searchContext.request()).thenReturn(shardSearchRequest);
            Mockito.when(searchContext.getTask()).thenReturn(task);
            Mockito.when(shardSearchRequest.shardId()).thenReturn(shardId);
        } else {
            Mockito.when(searchContext.request()).thenReturn(null);
        }
    }
}
