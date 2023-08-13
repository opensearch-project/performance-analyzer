/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.http_action.whoami;


import java.io.IOException;
import org.opensearch.core.action.ActionResponse;
import org.opensearch.core.common.io.stream.StreamOutput;
import org.opensearch.core.xcontent.ToXContent;
import org.opensearch.core.xcontent.XContentBuilder;

public class WhoAmIResponse extends ActionResponse implements ToXContent {
    @Override
    public XContentBuilder toXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject().field("whoami", "whoami").endObject();
        return builder;
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        // This is a no-op call just like how it was in 7.3.2(inherited from TransportMessage
        // .writeTo)
    }
}
