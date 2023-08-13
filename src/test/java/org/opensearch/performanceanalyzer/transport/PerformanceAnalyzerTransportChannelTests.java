/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.transport;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import org.apache.commons.lang3.SystemUtils;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opensearch.core.transport.TransportResponse;
import org.opensearch.performanceanalyzer.util.Utils;
import org.opensearch.transport.TransportChannel;

public class PerformanceAnalyzerTransportChannelTests {
    private PerformanceAnalyzerTransportChannel channel;

    @Mock private TransportChannel originalChannel;
    @Mock private TransportResponse response;

    @Before
    public void init() {
        // this test only runs in Linux system
        // as some of the static members of the ThreadList class are specific to Linux
        org.junit.Assume.assumeTrue(SystemUtils.IS_OS_LINUX);
        Utils.configureMetrics();
        initMocks(this);
        channel = new PerformanceAnalyzerTransportChannel();
        channel.set(originalChannel, 0, "testIndex", 1, 0, false);
        assertEquals("PerformanceAnalyzerTransportChannelProfile", channel.getProfileName());
        assertEquals("PerformanceAnalyzerTransportChannelType", channel.getChannelType());
        assertEquals(originalChannel, channel.getInnerChannel());
    }

    @Test
    public void testResponse() throws IOException {
        channel.sendResponse(response);
        verify(originalChannel).sendResponse(response);

        Exception exception = new Exception("dummy exception");
        channel.sendResponse(exception);
        verify(originalChannel).sendResponse(exception);
    }
}
