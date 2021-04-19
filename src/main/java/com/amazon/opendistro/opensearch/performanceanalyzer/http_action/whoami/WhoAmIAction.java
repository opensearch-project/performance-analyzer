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

package com.amazon.opendistro.opensearch.performanceanalyzer.http_action.whoami;


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
