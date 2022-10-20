/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.integ_test;


import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.junit.Assert;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.performanceanalyzer.PerformanceAnalyzerIntegTestBase;
import org.opensearch.performanceanalyzer.integ_test.json.JsonResponseNode;

public class MetricCollectorIntegTestBase extends PerformanceAnalyzerIntegTestBase {

    private List<String> nodeIDs;

    protected List<JsonResponseNode> readMetric(String endpoint) throws Exception {
        String jsonString;
        // read metric from local node
        Request request = new Request("GET", endpoint);
        Response resp = paClient.performRequest(request);
        Assert.assertEquals(HttpStatus.SC_OK, resp.getStatusLine().getStatusCode());
        jsonString = EntityUtils.toString(resp.getEntity());
        JsonObject jsonObject = new JsonParser().parse(jsonString).getAsJsonObject();
        return parseJsonResponse(jsonObject);
    }

    protected void initNodes() throws Exception {
        final Request request = new Request("GET", "/_cat/nodes?full_id&h=id");
        final Response response = adminClient().performRequest(request);
        nodeIDs = new ArrayList<>();
        if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
            try (BufferedReader responseReader =
                    new BufferedReader(
                            new InputStreamReader(
                                    response.getEntity().getContent(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = responseReader.readLine()) != null) {
                    nodeIDs.add(line);
                }
            }
        }
    }

    protected List<String> getNodeIDs() {
        return nodeIDs;
    }

    private List<JsonResponseNode> parseJsonResponse(JsonObject jsonObject)
            throws JsonParseException {
        List<JsonResponseNode> responseNodeList = new ArrayList<>();
        jsonObject
                .entrySet()
                .forEach(
                        n -> {
                            JsonResponseNode responseNode =
                                    new Gson().fromJson(n.getValue(), JsonResponseNode.class);
                            responseNodeList.add(responseNode);
                        });
        return responseNodeList;
    }
}
