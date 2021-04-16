/*
 * Copyright 2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package com.amazon.opendistro.opensearch.performanceanalyzer.integ_test;


import com.amazon.opendistro.opensearch.performanceanalyzer.integ_test.json.JsonResponseData;
import com.amazon.opendistro.opensearch.performanceanalyzer.integ_test.json.JsonResponseField;
import com.amazon.opendistro.opensearch.performanceanalyzer.integ_test.json.JsonResponseNode;
import com.amazon.opendistro.opensearch.performanceanalyzer.metrics.AllMetrics;
import java.util.List;
import java.util.function.DoublePredicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * All the heap tests check the metrics are within a range. We can't check for absolute values as
 * it'll vary across runs.
 */
public class HeapMetricsIT extends MetricCollectorIntegTestBase {

    private static final Logger LOG = LogManager.getLogger(HeapMetricsIT.class);
    private double MIN_HEAP_IN_MB = 50; // 50 MB
    private double MAX_HEAP_IN_MB = 2 * 1024; // 2 GB
    private double BYTES_TO_MB = 1024 * 1024;

    @Before
    public void init() throws Exception {
        initNodes();
    }

    @Test
    public void checkHeapInit() throws Exception {
        checkHeapMetric(AllMetrics.HeapValue.HEAP_INIT, (d) -> d >= MIN_HEAP_IN_MB && d <= MAX_HEAP_IN_MB);
    }

    @Test
    public void checkHeapMax() throws Exception {
        checkHeapMetric(AllMetrics.HeapValue.HEAP_MAX, (d) -> d >= MIN_HEAP_IN_MB && d <= MAX_HEAP_IN_MB);
    }

    @Test
    public void checkHeapUsed() throws Exception {
        checkHeapMetric(AllMetrics.HeapValue.HEAP_USED, (d) -> d >= MIN_HEAP_IN_MB && d <= MAX_HEAP_IN_MB);
    }

    private void checkHeapMetric(AllMetrics.HeapValue metric, DoublePredicate metricValidator)
            throws Exception {
        // read metric from local node
        List<JsonResponseNode> responseNodeList =
                readMetric(
                        PERFORMANCE_ANALYZER_BASE_ENDPOINT
                                + "/metrics/?metrics="
                                + metric.toString()
                                + "&agg=max");
        Assert.assertEquals(1, responseNodeList.size());
        validateHeapMetric(responseNodeList.get(0), metric, metricValidator);

        // read metric from all nodes in cluster
        responseNodeList =
                readMetric(
                        PERFORMANCE_ANALYZER_BASE_ENDPOINT
                                + "/metrics/?metrics="
                                + metric.toString()
                                + "&agg=max&nodes=all");
        int nodeNum = getNodeIDs().size();
        Assert.assertEquals(nodeNum, responseNodeList.size());
        for (int i = 0; i < nodeNum; i++) {
            validateHeapMetric(responseNodeList.get(i), metric, metricValidator);
        }
    }

    private void validateHeapMetric(
            JsonResponseNode responseNode,
            AllMetrics.HeapValue metric,
            DoublePredicate metricValidator)
            throws Exception {
        Assert.assertTrue(responseNode.getTimestamp() > 0);
        JsonResponseData responseData = responseNode.getData();
        Assert.assertEquals(1, responseData.getFieldDimensionSize());
        Assert.assertEquals(metric.toString(), responseData.getField(0).getName());
        Assert.assertEquals(
                JsonResponseField.Type.Constants.DOUBLE, responseData.getField(0).getType());
        Assert.assertEquals(1, responseData.getRecordSize());
        Double metricValue = responseData.getRecordAsDouble(0, metric.toString())/BYTES_TO_MB;
        LOG.info("{} value is {}", metric.toString(), metricValue);
        Assert.assertTrue(metricValidator.test(metricValue));
    }
}
