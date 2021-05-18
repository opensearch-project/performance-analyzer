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

package org.opensearch.performanceanalyzer.collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.mockito.Mockito;
import org.opensearch.performanceanalyzer.CustomMetricsLocationTestBase;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.reader_writer_shared.Event;

public class MasterThrottlingMetricsCollectorTests extends CustomMetricsLocationTestBase {

    @Test
    public void testMasterThrottlingMetrics() {
        MetricsConfiguration.CONFIG_MAP.put(
                MasterThrottlingMetricsCollector.class, MetricsConfiguration.cdefault);
        System.setProperty("performanceanalyzer.metrics.log.enabled", "False");

        long startTimeInMills = 1153721339;
        PerformanceAnalyzerController controller =
                Mockito.mock(PerformanceAnalyzerController.class);
        ConfigOverridesWrapper configOverrides = Mockito.mock(ConfigOverridesWrapper.class);
        Mockito.when(
                        controller.isCollectorEnabled(
                                configOverrides, "MasterThrottlingMetricsCollector"))
                .thenReturn(true);
        MasterThrottlingMetricsCollector throttlingMetricsCollectorCollector =
                new MasterThrottlingMetricsCollector(controller, configOverrides);
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
