/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.http_action.whoami;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opensearch.action.ActionListener;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.common.io.stream.Writeable.Reader;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.core.xcontent.XContentBuilder;
import org.opensearch.indices.IndicesService;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

@SuppressWarnings("unchecked")
public class WhoAmITests {
    private TransportWhoAmIAction transportWhoAmIAction;
    private WhoAmIAction whoAmIAction;
    private WhoAmIResponse response;

    @Mock private IndicesService indicesService;
    @Mock private ActionListener listener;
    @Mock private TransportService transportService;
    @Mock private ActionFilters actionFilters;
    @Mock private Task task;

    @Before
    public void init() {
        initMocks(this);

        transportWhoAmIAction =
                new TransportWhoAmIAction(transportService, actionFilters, indicesService);
        whoAmIAction = WhoAmIAction.INSTANCE;
        response = new WhoAmIResponse();
    }

    @Test
    public void testDoExecute() {
        WhoAmIRequestBuilder builder = new WhoAmIRequestBuilder(null);
        transportWhoAmIAction.doExecute(task, new WhoAmIRequest(), listener);
        verify(listener).onResponse(any());
        Assert.assertEquals(indicesService, OpenSearchResources.INSTANCE.getIndicesService());
    }

    @Test
    public void testWhoAmIActionReader() {
        Reader<WhoAmIResponse> reader = whoAmIAction.getResponseReader();
        assertEquals(WhoAmIAction.responseReader, reader);
    }

    @Test
    public void testWhoAmIActionResponse() throws IOException {
        XContentBuilder contentBuilder = XContentBuilder.builder(XContentType.JSON.xContent());
        XContentBuilder builder = response.toXContent(contentBuilder, null);
        response.writeTo(null);
        assertEquals(contentBuilder, builder);
    }
}
