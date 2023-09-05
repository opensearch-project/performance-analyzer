/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.http_action.whoami;


import org.opensearch.action.support.nodes.BaseNodesRequest;
import org.opensearch.core.common.io.stream.StreamInput;

public class WhoAmIRequest extends BaseNodesRequest<WhoAmIRequest> {

    private static final String[] EMPTY_NODE_ID_ARRAY = {};

    public WhoAmIRequest() {
        this(null);
    }

    public WhoAmIRequest(StreamInput input) {
        // Adding a ctor which matches the FunctionalInterface Writable.Reader<> so that ctor
        // reference succeeds.
        // The ctor itself calls the BaseNodesRequest<>(String... nodeIds) ctor with empty string
        // array(same functionality as 7.3.2's default ctor) since it was removed in 7.4.2.
        super(EMPTY_NODE_ID_ARRAY);
    }
}
