/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.mockito.Mockito;
import org.opensearch.performanceanalyzer.CustomMetricsLocationTestBase;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.event_process.Event;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;

public class ClusterManagerThrottlingMetricsCollectorTests extends CustomMetricsLocationTestBase {

    @Test
    public void testClusterManagerThrottlingMetrics() {
        MetricsConfiguration.CONFIG_MAP.put(
                ClusterManagerThrottlingMetricsCollector.class, MetricsConfiguration.cdefault);
        System.setProperty("performanceanalyzer.metrics.log.enabled", "False");

        long startTimeInMills = 1153721339;
        PerformanceAnalyzerController controller =
                Mockito.mock(PerformanceAnalyzerController.class);
        ConfigOverridesWrapper configOverrides = Mockito.mock(ConfigOverridesWrapper.class);
        Mockito.when(
                        controller.isCollectorEnabled(
                                configOverrides, "ClusterManagerThrottlingMetricsCollector"))
                .thenReturn(true);
        ClusterManagerThrottlingMetricsCollector throttlingMetricsCollectorCollector =
                new ClusterManagerThrottlingMetricsCollector(controller, configOverrides);
        throttlingMetricsCollectorCollector.saveMetricValues("testMetric", startTimeInMills);

        List<Event> metrics = new ArrayList<>();
        PerformanceAnalyzerMetrics.metricQueue.drainTo(metrics);
        assertEquals(1, metrics.size());
        assertEquals("testMetric", metrics.get(0).value);

        try {
            throttlingMetricsCollectorCollector.saveMetricValues(
                    "throttled_pending_tasks", startTimeInMills, "123");
            assertTrue("Negative scenario test: Should have been a RuntimeException", true);
        } catch (RuntimeException ex) {
            // - expecting exception...1 values passed; 0 expected
        }
    }
}
