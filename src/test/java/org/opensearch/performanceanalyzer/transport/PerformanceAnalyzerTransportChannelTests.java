/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.transport;

import static org.junit.Assert.assertEquals;
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
        assertEquals(originalChannel, channel.getInnerChannel());
    }

    @Test
    public void testProfileName() {
        assertEquals(originalChannel.getProfileName(), channel.getProfileName());
    }

    @Test
    public void testChannelType() {
        assertEquals(originalChannel.getChannelType(), channel.getChannelType());
    }

    @Test
    public void testVersion() {
        assertEquals(originalChannel.getVersion(), channel.getVersion());
    }

    @Test
    public void testGet() {
        assertEquals(originalChannel.get("test", Object.class), channel.get("test", Object.class));
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
            throws InvocationTargetException, IllegalAccessException {
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
            //            completeStream Method is experimental and not implemented in PAChannel
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
                method.invoke(paChannel, args);
            } else {
                method.invoke(paChannel);
            }
            method.invoke(verify(handlerSpy, times(1)), args);
        }
        verifyNoMoreInteractions(handlerSpy);
    }
}
