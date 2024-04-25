/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.http_action.whoami;

import org.opensearch.action.ActionRequestBuilder;
import org.opensearch.client.ClusterAdminClient;
import org.opensearch.client.OpenSearchClient;

public class WhoAmIRequestBuilder extends ActionRequestBuilder<WhoAmIRequest, WhoAmIResponse> {
    public WhoAmIRequestBuilder(final ClusterAdminClient client) {
        this(client, WhoAmIAction.INSTANCE);
    }

    public WhoAmIRequestBuilder(final OpenSearchClient client, final WhoAmIAction action) {
        super(client, action, new WhoAmIRequest());
    }
}
