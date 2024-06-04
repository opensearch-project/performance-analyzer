/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paranamer.ParanamerModule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.core.common.breaker.CircuitBreaker;
import org.opensearch.indices.IndicesService;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.config.PluginSettings;
import org.opensearch.performanceanalyzer.commons.event_process.Event;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.util.TestUtil;
import org.opensearch.test.OpenSearchSingleNodeTestCase;

public class CircuitBreakerCollectorTests extends OpenSearchSingleNodeTestCase {
    private static final String TEST_INDEX = "test";
    private CircuitBreakerCollector collector;
    private long startTimeInMills = 1153721339;

    @Before
    public void init() {
        IndicesService indicesService = getInstanceFromNode(IndicesService.class);
        OpenSearchResources.INSTANCE.setIndicesService(indicesService);
        OpenSearchResources.INSTANCE.setCircuitBreakerService(
                indicesService.getCircuitBreakerService());

        MetricsConfiguration.CONFIG_MAP.put(
                CircuitBreakerCollector.class, MetricsConfiguration.cdefault);
        collector = new CircuitBreakerCollector();

        // clean metricQueue before running every test
        TestUtil.readEvents();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testGetMetricsPath() {
        String expectedPath =
                PluginSettings.instance().getMetricsLocation()
                        + PerformanceAnalyzerMetrics.getTimeInterval(startTimeInMills)
                        + "/"
                        + PerformanceAnalyzerMetrics.sCircuitBreakerPath;
        String actualPath = collector.getMetricsPath(startTimeInMills);
        assertEquals(expectedPath, actualPath);

        try {
            collector.getMetricsPath(startTimeInMills, "circuitBreakerPath");
            fail("Negative scenario test: Should have been a RuntimeException");
        } catch (RuntimeException ex) {
            // - expecting exception...1 values passed; 0 expected
        }
    }

    @Test
    public void testCollectMetrics() throws IOException {
        createIndex(TEST_INDEX);
        collector.collectMetrics(startTimeInMills);
        List<CircuitBreakerCollector.CircuitBreakerStatus> metrics = readMetrics();
        assertEquals(4, metrics.size());
        assertEquals(CircuitBreaker.REQUEST, metrics.get(0).getType());
        assertEquals(CircuitBreaker.FIELDDATA, metrics.get(1).getType());
        assertEquals(CircuitBreaker.IN_FLIGHT_REQUESTS, metrics.get(2).getType());
        assertEquals(CircuitBreaker.PARENT, metrics.get(3).getType());
    }

    private List<CircuitBreakerCollector.CircuitBreakerStatus> readMetrics() throws IOException {
        List<Event> metrics = TestUtil.readEvents();
        assert metrics.size() == 1;
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new ParanamerModule());
        String[] jsonStrs = metrics.get(0).value.split("\n");
        assert jsonStrs.length == 5;
        List<CircuitBreakerCollector.CircuitBreakerStatus> list = new ArrayList<>();
        for (int i = 1; i < 5; i++) {
            list.add(
                    objectMapper.readValue(
                            jsonStrs[i], CircuitBreakerCollector.CircuitBreakerStatus.class));
        }
        return list;
    }
}
