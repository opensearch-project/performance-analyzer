/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.transport;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import org.apache.commons.lang3.SystemUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opensearch.action.bulk.BulkItemRequest;
import org.opensearch.action.bulk.BulkShardRequest;
import org.opensearch.action.support.replication.TransportReplicationAction.ConcreteShardRequest;
import org.opensearch.core.index.shard.ShardId;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.util.Util;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.util.Utils;
import org.opensearch.tasks.Task;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.transport.TransportChannel;
import org.opensearch.transport.TransportRequest;
import org.opensearch.transport.TransportRequestHandler;

@SuppressWarnings("unchecked")
public class RTFPerformanceAnalyzerTransportRequestHandlerTests {
    private RTFPerformanceAnalyzerTransportRequestHandler handler;
    private ConcreteShardRequest concreteShardRequest;

    @Mock private TransportRequestHandler transportRequestHandler;
    @Mock private PerformanceAnalyzerController controller;
    @Mock private TransportChannel channel;
    @Mock private TransportRequest request;
    @Mock private BulkShardRequest bulkShardRequest;
    @Mock private Task task;
    @Mock private ShardId shardId;
    @Mock private MetricsRegistry metricsRegistry;

    @Before
    public void init() {
        // this test only runs in Linux system
        // as some of the static members of the ThreadList class are specific to Linux
        org.junit.Assume.assumeTrue(SystemUtils.IS_OS_LINUX);
        Utils.configureMetrics();
        initMocks(this);
        handler =
                new RTFPerformanceAnalyzerTransportRequestHandler(
                        transportRequestHandler, controller);
        Mockito.when(controller.isPerformanceAnalyzerEnabled()).thenReturn(true);
    }

    @Test
    public void testMessageReceived() throws Exception {
        handler.messageReceived(request, channel, task);
        verify(transportRequestHandler).messageReceived(request, channel, task);
    }

    @Test
    public void testGetChannel() {
        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry);
        Mockito.when(controller.getCollectorsSettingValue())
                .thenReturn(Util.CollectorMode.TELEMETRY.getValue());
        concreteShardRequest = new ConcreteShardRequest(bulkShardRequest, "id", 1);
        handler.getChannel(concreteShardRequest, channel, task);

        Mockito.when(bulkShardRequest.shardId()).thenReturn(shardId);
        Mockito.when(bulkShardRequest.items()).thenReturn(new BulkItemRequest[1]);
        TransportChannel actualChannel = handler.getChannel(concreteShardRequest, channel, task);
        assertTrue(actualChannel instanceof RTFPerformanceAnalyzerTransportChannel);
    }

    @Test
    public void testGetChannelTelemetryIsDisabled() {
        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry);
        Mockito.when(controller.getCollectorsSettingValue())
                .thenReturn(Util.CollectorMode.RCA.getValue());
        concreteShardRequest = new ConcreteShardRequest(bulkShardRequest, "id", 1);
        handler.getChannel(concreteShardRequest, channel, task);

        Mockito.when(bulkShardRequest.shardId()).thenReturn(shardId);
        Mockito.when(bulkShardRequest.items()).thenReturn(new BulkItemRequest[1]);
        TransportChannel actualChannel = handler.getChannel(concreteShardRequest, channel, task);
        assertEquals(channel, actualChannel);
    }

    @Test
    public void testGetChannelDualMode() {
        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry);
        Mockito.when(controller.getCollectorsSettingValue())
                .thenReturn(Util.CollectorMode.DUAL.getValue());
        concreteShardRequest = new ConcreteShardRequest(bulkShardRequest, "id", 1);
        handler.getChannel(concreteShardRequest, channel, task);

        Mockito.when(bulkShardRequest.shardId()).thenReturn(shardId);
        Mockito.when(bulkShardRequest.items()).thenReturn(new BulkItemRequest[1]);
        TransportChannel actualChannel = handler.getChannel(concreteShardRequest, channel, task);
        assertTrue(actualChannel instanceof RTFPerformanceAnalyzerTransportChannel);
    }

    @Test
    public void testGetChannelMetricRegistryIsNull() {
        OpenSearchResources.INSTANCE.setMetricsRegistry(null);
        Mockito.when(controller.getCollectorsSettingValue())
                .thenReturn(Util.CollectorMode.RCA.getValue());
        concreteShardRequest = new ConcreteShardRequest(bulkShardRequest, "id", 1);
        handler.getChannel(concreteShardRequest, channel, task);

        Mockito.when(bulkShardRequest.shardId()).thenReturn(shardId);
        Mockito.when(bulkShardRequest.items()).thenReturn(new BulkItemRequest[1]);
        TransportChannel actualChannel = handler.getChannel(concreteShardRequest, channel, task);
        assertEquals(channel, actualChannel);
    }
}
