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

package com.amazon.opendistro.opensearch.performanceanalyzer.listener;

import static org.junit.Assert.assertEquals;
import static org.mockito.MockitoAnnotations.initMocks;

import com.amazon.opendistro.opensearch.performanceanalyzer.CustomMetricsLocationTestBase;
import com.amazon.opendistro.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import com.amazon.opendistro.opensearch.performanceanalyzer.config.PluginSettings;
import com.amazon.opendistro.opensearch.performanceanalyzer.metrics.PerformanceAnalyzerMetrics;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;

@Ignore
public class SearchListenerTests extends CustomMetricsLocationTestBase {
    @Mock private PerformanceAnalyzerController mockController;

    @Test
    public void testShardSearchMetrics() {
        initMocks(this);
        System.setProperty("performanceanalyzer.metrics.log.enabled", "False");
        long startTimeInMills = 1053720339;
        PerformanceAnalyzerSearchListener performanceanalyzerSearchListener =
                new PerformanceAnalyzerSearchListener(mockController);
        performanceanalyzerSearchListener.saveMetricValues(
                "dewrjcve",
                startTimeInMills,
                "SearchThread",
                "shardquery",
                "ShardSearchID",
                "start");
        String fetchedValue =
                PerformanceAnalyzerMetrics.getMetric(
                        PluginSettings.instance().getMetricsLocation()
                                + PerformanceAnalyzerMetrics.getTimeInterval(startTimeInMills)
                                + "/threads/SearchThread/shardquery/ShardSearchID/start");
        assertEquals("dewrjcve", fetchedValue);

        String startMetricsValue =
                performanceanalyzerSearchListener.generateStartMetrics(100, "index1", 1).toString();
        performanceanalyzerSearchListener.saveMetricValues(
                startMetricsValue,
                startTimeInMills,
                "SearchThread",
                "shardquery",
                "ShardSearchID1",
                "start");
        fetchedValue =
                PerformanceAnalyzerMetrics.getMetric(
                        PluginSettings.instance().getMetricsLocation()
                                + PerformanceAnalyzerMetrics.getTimeInterval(startTimeInMills)
                                + "/threads/SearchThread/shardquery/ShardSearchID1/start");
        assertEquals(startMetricsValue, fetchedValue);

        String finishMetricsValue =
                performanceanalyzerSearchListener
                        .generateFinishMetrics(123, false, "index1", 10)
                        .toString();
        performanceanalyzerSearchListener.saveMetricValues(
                finishMetricsValue,
                startTimeInMills,
                "SearchThread",
                "shardquery",
                "ShardSearchID1",
                "finish");
        fetchedValue =
                PerformanceAnalyzerMetrics.getMetric(
                        PluginSettings.instance().getMetricsLocation()
                                + PerformanceAnalyzerMetrics.getTimeInterval(startTimeInMills)
                                + "/threads/SearchThread/shardquery/ShardSearchID1/finish");
        assertEquals(finishMetricsValue, fetchedValue);

        performanceanalyzerSearchListener.saveMetricValues(
                finishMetricsValue,
                startTimeInMills,
                "SearchThread",
                "shardquery",
                "ShardSearchID2",
                "finish");
        fetchedValue =
                PerformanceAnalyzerMetrics.getMetric(
                        PluginSettings.instance().getMetricsLocation()
                                + PerformanceAnalyzerMetrics.getTimeInterval(startTimeInMills)
                                + "/threads/SearchThread/shardquery/ShardSearchID2/finish");
        assertEquals(finishMetricsValue, fetchedValue);

        PerformanceAnalyzerMetrics.removeMetrics(
                PluginSettings.instance().getMetricsLocation()
                        + PerformanceAnalyzerMetrics.getTimeInterval(startTimeInMills));
    }
}
