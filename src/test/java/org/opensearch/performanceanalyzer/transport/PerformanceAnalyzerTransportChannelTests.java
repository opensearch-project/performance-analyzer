/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.transport;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.MockitoAnnotations.initMocks;

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

    @Test
    public void testPAChannelDelegatesToOriginal()
            throws IOException, InvocationTargetException, IllegalAccessException {
        TransportChannel handlerSpy = spy(originalChannel);
        PerformanceAnalyzerTransportChannel paChannel = new PerformanceAnalyzerTransportChannel();
        paChannel.set(handlerSpy, 0, "testIndex", 1, 0, false);

        List<Method> overridableMethods =
                Arrays.stream(TransportChannel.class.getMethods())
                        .filter(
                                m ->
                                        !(Modifier.isPrivate(m.getModifiers())
                                                || Modifier.isStatic(m.getModifiers())
                                                || Modifier.isFinal(m.getModifiers())))
                        .collect(Collectors.toList());

        for (Method method : overridableMethods) {
            if (Set.of("getProfileName", "getChannelType").contains(method.getName())) {
                continue;
            }
            int argCount = method.getParameterCount();
            Object[] args = new Object[argCount];
            for (int i = 0; i < argCount; i++) {
                args[i] = any();
            }
            if (args.length > 0) {
                method.invoke(paChannel, args);
            } else {
                method.invoke(paChannel);
            }
            method.invoke(verify(handlerSpy, times(1)), args);
        }
        verifyNoMoreInteractions(handlerSpy);
    }
}
