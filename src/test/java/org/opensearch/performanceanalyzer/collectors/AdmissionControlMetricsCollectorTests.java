/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.performanceanalyzer.CustomMetricsLocationTestBase;
import org.opensearch.performanceanalyzer.commons.event_process.Event;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.util.TestUtil;

public class AdmissionControlMetricsCollectorTests extends CustomMetricsLocationTestBase {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        // clean metricQueue before running every test
        TestUtil.readEvents();
        System.setProperty("performanceanalyzer.metrics.log.enabled", "False");
    }

    @Test
    public void admissionControlMetricsCollector() {
        MetricsConfiguration.CONFIG_MAP.put(
                AdmissionControlMetricsCollector.class, MetricsConfiguration.cdefault);
        AdmissionControlMetricsCollector admissionControlMetricsCollector =
                new AdmissionControlMetricsCollector();

        long startTimeInMills = System.currentTimeMillis();
        admissionControlMetricsCollector.saveMetricValues("testMetric", startTimeInMills);

        List<Event> metrics = TestUtil.readEvents();
        assertEquals(1, metrics.size());
        assertEquals("testMetric", metrics.get(0).value);
    }
}
