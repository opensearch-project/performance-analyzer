/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.transport;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opensearch.core.index.Index;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.core.transport.TransportResponse;
import org.opensearch.performanceanalyzer.util.Utils;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.tags.Tags;
import org.opensearch.transport.TransportChannel;

public class RTFPerformanceAnalyzerTransportChannelTests {
    private RTFPerformanceAnalyzerTransportChannel channel;

    @Mock private TransportChannel originalChannel;
    @Mock private TransportResponse response;
    @Mock private Histogram cpuUtilizationHistogram;
    @Mock private Histogram indexingLatencyHistogram;
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
        channel.set(
                originalChannel,
                cpuUtilizationHistogram,
                indexingLatencyHistogram,
                indexName,
                shardId,
                false);
        assertEquals("RTFPerformanceAnalyzerTransportChannelProfile", channel.getProfileName());
        assertEquals("RTFPerformanceAnalyzerTransportChannelType", channel.getChannelType());
        assertEquals(originalChannel, channel.getInnerChannel());
    }

    @Test
    public void testResponse() throws IOException {
        channel.sendResponse(response);
        verify(originalChannel).sendResponse(response);
        verify(cpuUtilizationHistogram, times(1)).record(anyDouble(), any(Tags.class));
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
        RTFPerformanceAnalyzerTransportChannel channel =
                new RTFPerformanceAnalyzerTransportChannel();
        channel.set(
                originalChannel,
                cpuUtilizationHistogram,
                indexingLatencyHistogram,
                "testIndex",
                mockedShardId,
                false);
        Mockito.when(mockedShardId.getIndex()).thenReturn(index);
        Mockito.when(index.getName()).thenReturn("myTestIndex");
        Mockito.when(index.getUUID()).thenReturn("abc-def");
        channel.recordCPUUtilizationMetric(mockedShardId, 10l, "bulkShard", false);
        Mockito.verify(cpuUtilizationHistogram)
                .record(Mockito.anyDouble(), Mockito.any(Tags.class));
    }

    @Test
    public void testRecordIndexingLatencyMetric() {
        RTFPerformanceAnalyzerTransportChannel channel =
                new RTFPerformanceAnalyzerTransportChannel();
        channel.set(
                originalChannel,
                cpuUtilizationHistogram,
                indexingLatencyHistogram,
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
}
