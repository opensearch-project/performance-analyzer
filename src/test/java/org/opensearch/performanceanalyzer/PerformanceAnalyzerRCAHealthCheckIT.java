/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpStatus;
import org.apache.http.util.EntityUtils;
import org.junit.Assert;
import org.junit.Test;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.performanceanalyzer.http_action.config.RestConfig;
import org.opensearch.performanceanalyzer.util.WaitFor;

public class PerformanceAnalyzerRCAHealthCheckIT extends PerformanceAnalyzerIntegTestBase {
    @Test
    public void checkMetrics() throws Exception {
        checkMetrics(RestConfig.PA_BASE_URI);
    }

    @Test
    public void checkLegacyMetrics() throws Exception {
        checkMetrics(RestConfig.LEGACY_PA_BASE_URI);
    }

    public void checkMetrics(String paBaseUri) throws Exception {
        ensurePaAndRcaEnabled(paBaseUri);
        final String[] jsonString = new String[1];
        WaitFor.waitFor(
                () -> {
                    Request request =
                            new Request(
                                    "GET",
                                    paBaseUri
                                            + "/_agent/metrics/?metrics=Disk_Utilization&agg=max&dim=&nodes=all");
                    Response resp = paClient.performRequest(request);
                    Assert.assertEquals(HttpStatus.SC_OK, resp.getStatusLine().getStatusCode());
                    jsonString[0] = EntityUtils.toString(resp.getEntity());
                    JsonNode root = mapper.readTree(jsonString[0]);
                    for (Iterator<JsonNode> it = root.elements(); it.hasNext(); ) {
                        JsonNode entry = it.next();
                        JsonNode data = entry.get(TestUtils.DATA);
                        if (data.get(TestUtils.FIELDS) == null) {
                            return false;
                        }
                    }
                    return jsonString[0] != null && !jsonString[0].isEmpty();
                },
                1,
                TimeUnit.MINUTES);
        logger.info("jsonString is {}", jsonString[0]);
        JsonNode root = mapper.readTree(jsonString[0]);
        root.forEach(
                entry -> {
                    JsonNode data = entry.get(TestUtils.DATA);
                    Assert.assertEquals(1, data.get(TestUtils.FIELDS).size());
                    JsonNode field = data.get(TestUtils.FIELDS).get(0);
                    Assert.assertEquals(
                            TestUtils.M_DISKUTIL, field.get(TestUtils.FIELD_NAME).asText());
                    Assert.assertEquals(
                            TestUtils.DOUBLE_TYPE, field.get(TestUtils.FIELD_TYPE).asText());
                    JsonNode records = data.get(TestUtils.RECORDS);
                    Assert.assertEquals(1, records.size());
                    records.get(0).forEach(record -> Assert.assertTrue(record.asDouble() >= 0));
                });
    }

    @Test
    public void testRcaIsRunning() throws Exception {
        testRcaIsRunning(RestConfig.PA_BASE_URI);
    }

    @Test
    public void testLegacyRcaIsRunning() throws Exception {
        testRcaIsRunning(RestConfig.LEGACY_PA_BASE_URI);
    }

    public void testRcaIsRunning(String paBaseUri) throws Exception {
        ensurePaAndRcaEnabled(paBaseUri);
        WaitFor.waitFor(
                () -> {
                    Request request = new Request("GET", paBaseUri + "/rca");
                    try {
                        Response resp = paClient.performRequest(request);
                        return Objects.equals(
                                HttpStatus.SC_OK, resp.getStatusLine().getStatusCode());
                    } catch (Exception e) { // 404, RCA context hasn't been set up yet
                        return false;
                    }
                },
                2,
                TimeUnit.MINUTES);
    }
}
