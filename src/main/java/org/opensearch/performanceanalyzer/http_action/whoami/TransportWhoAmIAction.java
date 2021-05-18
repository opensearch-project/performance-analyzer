/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

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

package org.opensearch.performanceanalyzer.http_action.whoami;


import org.opensearch.action.ActionListener;
import org.opensearch.action.support.ActionFilters;
import org.opensearch.action.support.HandledTransportAction;
import org.opensearch.common.inject.Inject;
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
