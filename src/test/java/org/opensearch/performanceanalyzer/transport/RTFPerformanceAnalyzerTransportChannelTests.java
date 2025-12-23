/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.transport;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.opensearch.performanceanalyzer.transport.TestUtils.createDummyValue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.transport.TransportResponse;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.ShardMetricsCollector;
import org.opensearch.performanceanalyzer.util.Utils;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;
import org.opensearch.transport.TransportChannel;

public class RTFPerformanceAnalyzerTransportChannelTests {
    private RTFPerformanceAnalyzerTransportChannel channel;

    @Mock private TransportChannel originalChannel;
    @Mock private TransportResponse response;
    @Mock private Histogram cpuUtilizationHistogram;
    @Mock private Histogram indexingLatencyHistogram;
    @Mock private Histogram heapUsedHistogram;
    @Mock private Histogram shardMetricsCpuHistogram;
    @Mock private Histogram shardMetricsHeapHistogram;

    private ShardId shardId;
    @Mock private ShardId mockedShardId;
    @Mock private Index index;

    @Before
    public void init() {
        // this test only runs in Linux system
        // as some of the static members of the ThreadList class are specific to Linux
        org.junit.Assume.assumeTrue(SystemUtils.IS_OS_LINUX);
        Utils.configureMetrics();
        initMocks(this);
        String indexName = "testIndex";
        shardId = new ShardId(new Index(indexName, "uuid"), 1);
        channel = new RTFPerformanceAnalyzerTransportChannel();

        // Setup metrics registry to return our mock histograms
        MetricsRegistry metricsRegistry = Mockito.mock(MetricsRegistry.class);
        Mockito.when(
                        metricsRegistry.createHistogram(
                                Mockito.eq(ShardMetricsCollector.SHARD_CPU_UTILIZATION),
                                Mockito.anyString(),
                                Mockito.anyString()))
                .thenReturn(shardMetricsCpuHistogram);
        Mockito.when(
                        metricsRegistry.createHistogram(
                                Mockito.eq(ShardMetricsCollector.SHARD_HEAP_ALLOCATED),
                                Mockito.anyString(),
                                Mockito.anyString()))
                .thenReturn(shardMetricsHeapHistogram);

        // Set the metrics registry
        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry);

        // Initialize ShardMetricsCollector
        ShardMetricsCollector.INSTANCE.initialize();
        channel.set(
                originalChannel,
                cpuUtilizationHistogram,
                indexingLatencyHistogram,
                heapUsedHistogram,
                indexName,
                shardId,
                false);
    }

    @Test
    public void testResponse() throws IOException {
        channel.sendResponse(response);
        verify(originalChannel).sendResponse(response);
        verify(cpuUtilizationHistogram, times(1)).record(anyDouble(), any(Tags.class));
    }

    @Test
    public void testGetProfileName() {
        assertEquals(originalChannel.getProfileName(), channel.getProfileName());
    }

    @Test
    public void testGetChannelType() {
        assertEquals(originalChannel.getChannelType(), channel.getChannelType());
    }

    @Test
    public void testGetVersion() {
        assertEquals(originalChannel.getVersion(), channel.getVersion());
    }

    @Test
    public void testGet() throws IOException {
        assertEquals(originalChannel.get("test", Object.class), channel.get("test", Object.class));
    }

    @Test
    public void testResponseWithException() throws IOException {
        Exception exception = new Exception("dummy exception");
        channel.sendResponse(exception);
        verify(originalChannel).sendResponse(exception);
        verify(cpuUtilizationHistogram, times(1)).record(anyDouble(), any(Tags.class));
    }

    @Test
    public void testRecordCPUUtilizationMetric() {
        Histogram shardCpu = ShardMetricsCollector.INSTANCE.getCpuUtilizationHistogram();
        if (shardCpu != null) {
            // Clear any previous mock interactions
            Mockito.clearInvocations(shardCpu);
        }
        RTFPerformanceAnalyzerTransportChannel channel =
                new RTFPerformanceAnalyzerTransportChannel();
        channel.set(
                originalChannel,
                cpuUtilizationHistogram,
                indexingLatencyHistogram,
                heapUsedHistogram,
                "testIndex",
                mockedShardId,
                false);
        Mockito.when(mockedShardId.getIndex()).thenReturn(index);
        Mockito.when(index.getName()).thenReturn("myTestIndex");
        Mockito.when(index.getUUID()).thenReturn("abc-def");
        channel.recordCPUUtilizationMetric(mockedShardId, 10l, "bulkShard", false);
        Mockito.verify(cpuUtilizationHistogram)
                .record(Mockito.anyDouble(), Mockito.any(Tags.class));
        Mockito.verify(shardCpu).record(anyDouble(), any(Tags.class));
    }

    @Test
    public void testRecordIndexingLatencyMetric() {
        RTFPerformanceAnalyzerTransportChannel channel =
                new RTFPerformanceAnalyzerTransportChannel();
        channel.set(
                originalChannel,
                cpuUtilizationHistogram,
                indexingLatencyHistogram,
                heapUsedHistogram,
                "testIndex",
                mockedShardId,
                false);
        Mockito.when(mockedShardId.getIndex()).thenReturn(index);
        Mockito.when(index.getName()).thenReturn("myTestIndex");
        Mockito.when(index.getUUID()).thenReturn("abc-def");
        channel.recordIndexingLatencyMetric(mockedShardId, 123.456, "bulkShard", false);
        Mockito.verify(indexingLatencyHistogram)
                .record(Mockito.anyDouble(), Mockito.any(Tags.class));
    }

    @Test
    public void testRecordHeapUsedMetric() {
        Histogram shardHeap = ShardMetricsCollector.INSTANCE.getHeapUsedHistogram();
        if (shardHeap != null) {
            // Clear any previous mock interactions
            Mockito.clearInvocations(shardHeap);
        }
        RTFPerformanceAnalyzerTransportChannel channel =
                new RTFPerformanceAnalyzerTransportChannel();
        channel.set(
                originalChannel,
                cpuUtilizationHistogram,
                indexingLatencyHistogram,
                heapUsedHistogram,
                "testIndex",
                mockedShardId,
                false);
        Mockito.when(mockedShardId.getIndex()).thenReturn(index);
        Mockito.when(index.getName()).thenReturn("myTestIndex");
        Mockito.when(index.getUUID()).thenReturn("abc-def");
        channel.recordHeapUsedMetric(mockedShardId, 10l, "bulkShard", false);
        Mockito.verify(heapUsedHistogram).record(Mockito.anyDouble(), Mockito.any(Tags.class));
        // Verify the shard metrics histogram
        Mockito.verify(shardHeap).record(anyDouble(), any(Tags.class));
    }

    public void testRTFPAChannelDelegatesToOriginal()
            throws InvocationTargetException, IllegalAccessException {
        TransportChannel handlerSpy = spy(originalChannel);
        RTFPerformanceAnalyzerTransportChannel rtfChannel =
                new RTFPerformanceAnalyzerTransportChannel();
        rtfChannel.set(
                handlerSpy,
                cpuUtilizationHistogram,
                indexingLatencyHistogram,
                heapUsedHistogram,
                index.getName(),
                shardId,
                false);

        List<Method> overridableMethods =
                Arrays.stream(TransportChannel.class.getMethods())
                        .filter(
                                m ->
                                        !(Modifier.isPrivate(m.getModifiers())
                                                || Modifier.isStatic(m.getModifiers())
                                                || Modifier.isFinal(m.getModifiers())))
                        .collect(Collectors.toList());

        for (Method method : overridableMethods) {
            // completeStream and sendresponsebatch Methods are experimental and not
            // implemented in PAChannel
            if (Set.of("sendresponsebatch", "completestream")
                    .contains(method.getName().toLowerCase())) {
                continue;
            }

            int argCount = method.getParameterCount();
            Object[] args = new Object[argCount];
            Class<?>[] parameterTypes = method.getParameterTypes();
            for (int i = 0; i < argCount; i++) {
                args[i] = createDummyValue(parameterTypes[i]);
                ;
            }
            if (args.length > 0) {
                method.invoke(rtfChannel, args);
            } else {
                method.invoke(rtfChannel);
            }
            method.invoke(verify(handlerSpy, times(1)), args);
        }
        verifyNoMoreInteractions(handlerSpy);
    }
}
