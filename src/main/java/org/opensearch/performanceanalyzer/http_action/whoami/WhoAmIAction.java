/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.http_action.whoami;


import org.opensearch.action.ActionType;
import org.opensearch.common.io.stream.Writeable;

public class WhoAmIAction extends ActionType<WhoAmIResponse> {

    public static final String NAME = "cluster:admin/performanceanalyzer/whoami";
    public static final Writeable.Reader<WhoAmIResponse> responseReader = null;
    public static final WhoAmIAction INSTANCE = new WhoAmIAction();

    private WhoAmIAction() {
        super(NAME, responseReader);
    }

    @Override
    public Writeable.Reader<WhoAmIResponse> getResponseReader() {
        return responseReader;
    }
}
