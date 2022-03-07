/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.integ_test.json;


import com.google.gson.annotations.SerializedName;

/** "node1": { "data": { ....... }, "timestamp": 1606861740000 } */
public class JsonResponseNode {
    private static final String DATA = "data";
    private static final String TIMESTAMP = "timestamp";

    @SerializedName(DATA)
    private JsonResponseData data;

    @SerializedName(TIMESTAMP)
    private long timestamp;

    public JsonResponseNode(JsonResponseData data, long timestamp) {
        this.data = data;
        this.timestamp = timestamp;
    }

    public JsonResponseData getData() {
        return data;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
