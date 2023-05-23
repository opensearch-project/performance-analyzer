/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.listener;

import static org.junit.Assert.assertEquals;
import static org.mockito.MockitoAnnotations.initMocks;

import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mock;
import org.opensearch.performanceanalyzer.CustomMetricsLocationTestBase;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.config.PluginSettings;

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
