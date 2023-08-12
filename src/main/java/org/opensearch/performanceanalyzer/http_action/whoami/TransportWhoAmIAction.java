/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.http_action.whoami;


import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
import org.opensearch.core.action.ActionListener;
import org.opensearch.indices.IndicesService;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.tasks.Task;
import org.opensearch.transport.TransportService;

public class TransportWhoAmIAction extends HandledTransportAction<WhoAmIRequest, WhoAmIResponse> {

    @Inject
    public TransportWhoAmIAction(
            final TransportService transportService,
            final ActionFilters actionFilters,
            final IndicesService indicesService) {
        super(WhoAmIAction.NAME, transportService, actionFilters, WhoAmIRequest::new);
        OpenSearchResources.INSTANCE.setIndicesService(indicesService);
    }

    @Override
    protected void doExecute(
            Task task, WhoAmIRequest request, ActionListener<WhoAmIResponse> listener) {
        listener.onResponse(new WhoAmIResponse());
    }
}
