/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.integ_test;


import java.util.List;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.performanceanalyzer.http_action.config.RestConfig;
import org.opensearch.performanceanalyzer.integ_test.json.JsonResponseData;
import org.opensearch.performanceanalyzer.integ_test.json.JsonResponseField;
import org.opensearch.performanceanalyzer.integ_test.json.JsonResponseNode;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.OSMetrics;

public class PageFaultMetricsIT extends MetricCollectorIntegTestBase {

    @Before
    public void init() throws Exception {
        initNodes();
    }

    @Test
    public void checkPaging_MajfltRate() throws Exception {
        checkPaging_MajfltRate(RestConfig.PA_BASE_URI);
    }

    @Test
    public void checkLegacyPaging_MajfltRate() throws Exception {
        checkPaging_MajfltRate(RestConfig.LEGACY_PA_BASE_URI);
    }

    public void checkPaging_MajfltRate(String paBaseUri) throws Exception {
        // read metric from local node
        List<JsonResponseNode> responseNodeList =
                readMetric(paBaseUri + "/_agent/metrics?metrics=Paging_MajfltRate&agg=max");
        Assert.assertEquals(1, responseNodeList.size());
        validateMajorPageFaultMetric(responseNodeList.get(0));

        // read metric from all nodes in cluster
        responseNodeList =
                readMetric(
                        paBaseUri + "/_agent/metrics?metrics=Paging_MajfltRate&agg=max&nodes=all");
        int nodeNum = getNodeIDs().size();
        Assert.assertEquals(nodeNum, responseNodeList.size());
        for (int i = 0; i < nodeNum; i++) {
            validateMajorPageFaultMetric(responseNodeList.get(i));
        }
    }

    @Test
    public void checkPaging_MinfltRate() throws Exception {
        checkPaging_MinfltRate(RestConfig.PA_BASE_URI);
    }

    @Test
    public void checkLegacyPaging_MinfltRate() throws Exception {
        checkPaging_MinfltRate(RestConfig.LEGACY_PA_BASE_URI);
    }

    public void checkPaging_MinfltRate(String paBaseUri) throws Exception {
        // read metric from local node
        List<JsonResponseNode> responseNodeList =
                readMetric(paBaseUri + "/_agent/metrics?metrics=Paging_MinfltRate&agg=max");
        Assert.assertEquals(1, responseNodeList.size());
        validateMinorPageFaultMetric(responseNodeList.get(0));

        // read metric from all nodes in cluster
        responseNodeList =
                readMetric(
                        paBaseUri + "/_agent/metrics?metrics=Paging_MinfltRate&agg=max&nodes=all");
        int nodeNum = getNodeIDs().size();
        Assert.assertEquals(nodeNum, responseNodeList.size());
        for (int i = 0; i < nodeNum; i++) {
            validateMinorPageFaultMetric(responseNodeList.get(i));
        }
    }

    @Test
    public void checkPaging_RSS() throws Exception {
        checkPaging_RSS(RestConfig.PA_BASE_URI);
    }

    @Test
    public void checkLegacyPaging_RSS() throws Exception {
        checkPaging_RSS(RestConfig.LEGACY_PA_BASE_URI);
    }

    public void checkPaging_RSS(String paBaseUri) throws Exception {
        // read metric from local node
        List<JsonResponseNode> responseNodeList =
                readMetric(paBaseUri + "/_agent/metrics?metrics=Paging_RSS&agg=max");
        Assert.assertEquals(1, responseNodeList.size());
        validatePagingRSSMetric(responseNodeList.get(0));

        // read metric from all nodes in cluster
        responseNodeList =
                readMetric(paBaseUri + "/_agent/metrics?metrics=Paging_RSS&agg=max&nodes=all");
        int nodeNum = getNodeIDs().size();
        Assert.assertEquals(nodeNum, responseNodeList.size());
        for (int i = 0; i < nodeNum; i++) {
            validatePagingRSSMetric(responseNodeList.get(i));
        }
    }

    /**
     * check if major page fault is greater or equals to 0. major page fault heavily depends on the
     * workload on OS. if docker image is running on a OS without heavy workload, we might unlikely
     * observe any major page fault during the 5s interval. We might want to revisit this and see if
     * we can run some workload to trigger page fault { "JtlEoRowSI6iNpzpjlbp_Q": { "data": {
     * "fields": [ { "name": "Paging_MajfltRate", "type": "DOUBLE" } ], "records": [ [ 0.0 ] ] },
     * "timestamp": 1606861150000 } }
     */
    private void validateMajorPageFaultMetric(JsonResponseNode responseNode) throws Exception {
        Assert.assertTrue(responseNode.getTimestamp() > 0);
        JsonResponseData responseData = responseNode.getData();
        Assert.assertEquals(1, responseData.getFieldDimensionSize());
        Assert.assertEquals(
                OSMetrics.PAGING_MAJ_FLT_RATE.toString(), responseData.getField(0).getName());
        Assert.assertEquals(
                JsonResponseField.Type.Constants.DOUBLE, responseData.getField(0).getType());
        Assert.assertEquals(1, responseData.getRecordSize());
        Assert.assertTrue(
                responseData.getRecordAsDouble(0, OSMetrics.PAGING_MAJ_FLT_RATE.toString()) >= 0);
    }

    /**
     * { "JtlEoRowSI6iNpzpjlbp_Q": { "data": { "fields": [ { "name": "Paging_MinfltRate", "type":
     * "DOUBLE" } ], "records": [ [ 0.28116752649470106 ] ] }, "timestamp": 1606861625000 } }
     */
    private void validateMinorPageFaultMetric(JsonResponseNode responseNode) throws Exception {
        Assert.assertTrue(responseNode.getTimestamp() > 0);
        JsonResponseData responseData = responseNode.getData();
        Assert.assertEquals(1, responseData.getFieldDimensionSize());
        Assert.assertEquals(
                OSMetrics.PAGING_MIN_FLT_RATE.toString(), responseData.getField(0).getName());
        Assert.assertEquals(
                JsonResponseField.Type.Constants.DOUBLE, responseData.getField(0).getType());
        Assert.assertEquals(1, responseData.getRecordSize());
        Assert.assertTrue(
                responseData.getRecordAsDouble(0, OSMetrics.PAGING_MIN_FLT_RATE.toString()) >= 0);
    }

    /**
     * number of pages in OS should be a non-zero value { "JtlEoRowSI6iNpzpjlbp_Q": { "data": {
     * "fields": [ { "name": "Paging_RSS", "type": "DOUBLE" } ], "records": [ [ 666034.0 ] ] },
     * "timestamp": 1606866110000 } }
     */
    private void validatePagingRSSMetric(JsonResponseNode responseNode) throws Exception {
        Assert.assertTrue(responseNode.getTimestamp() > 0);
        JsonResponseData responseData = responseNode.getData();
        Assert.assertEquals(1, responseData.getFieldDimensionSize());
        Assert.assertEquals(OSMetrics.PAGING_RSS.toString(), responseData.getField(0).getName());
        Assert.assertEquals(
                JsonResponseField.Type.Constants.DOUBLE, responseData.getField(0).getType());
        Assert.assertEquals(1, responseData.getRecordSize());
        Assert.assertTrue(responseData.getRecordAsDouble(0, OSMetrics.PAGING_RSS.toString()) > 0);
    }
}
