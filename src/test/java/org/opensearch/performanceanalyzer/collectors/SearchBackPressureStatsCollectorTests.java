/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.event_process.Event;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
public class SearchBackPressureStatsCollectorTests {
    private ObjectMapper mapper;
    private long startTimeInMills;
    private PerformanceAnalyzerController controller;
    private ConfigOverridesWrapper configOverrides;
    private SearchBackPressureStatsCollector searchBackPressureStatsCollector;

    // Required fields needed for search back pressure stats
    private List<String> required_fields_for_searchBackPressureStats =
            Arrays.asList(
                    "searchbp_shard_task_stats_cancellationCount",
                    "searchbp_shard_task_stats_limitReachedCount",
                    "searchbp_shard_task_stats_resource_heap_usage_cancellationCount",
                    "searchbp_shard_task_stats_resource_heap_usage_currentMax",
                    "searchbp_shard_task_stats_resource_heap_usage_rollingAvg",
                    "searchbp_shard_task_stats_resource_cpu_usage_cancellationCount",
                    "searchbp_shard_task_stats_resource_cpu_usage_currentMax",
                    "searchbp_shard_task_stats_resource_cpu_usage_currentAvg",
                    "searchbp_shard_task_stats_resource_elaspedtime_usage_cancellationCount",
                    "searchbp_shard_task_stats_resource_elaspedtime_usage_currentMax",
                    "searchbp_shard_task_stats_resource_elaspedtime_usage_currentAvg",
                    "searchbp_search_task_stats_cancellationCount",
                    "searchbp_search_task_stats_limitReachedCount",
                    "searchbp_search_task_stats_resource_heap_usage_cancellationCount",
                    "searchbp_search_task_stats_resource_heap_usage_currentMax",
                    "searchbp_search_task_stats_resource_heap_usage_rollingAvg",
                    "searchbp_search_task_stats_resource_cpu_usage_cancellationCount",
                    "searchbp_search_task_stats_resource_cpu_usage_currentMax",
                    "searchbp_search_task_stats_resource_cpu_usage_currentAvg",
                    "searchbp_search_task_stats_resource_elaspedtime_usage_cancellationCount",
                    "searchbp_search_task_stats_resource_elaspedtime_usage_currentMax",
                    "searchbp_search_task_stats_resource_elaspedtime_usage_currentAvg",
                    "searchbp_mode",
                    "searchbp_nodeid");

    // Mock Instance for HEAP/CPU/ELAPSED_TIME usage
    SearchBackPressureStatsCollector.ResourceUsageTrackerStats HEAP_USAGE_TRACKER_MOCK_STATS;
    SearchBackPressureStatsCollector.ResourceUsageTrackerStats CPU_USAGE_TRACKER_MOCK_STATS;
    SearchBackPressureStatsCollector.ResourceUsageTrackerStats ELAPSED_TIME_TRACKER_MOCK_STATS;

    /*
     *  Required Config to be initialized
     *  Set the LOG property to be false
     *  Set the controller to be a Mock PerforamcneAnalyzerController.class
     *  Set the configWrapper to be a Mock ConfigOverridesWrapper
     *  Set the Collector to be a new SearchBackPressureServiceCollector
     *  Set the ObjectMapper to be a new ObjectMapper() instance
     */
    @Before
    public void init() {
        mapper = new ObjectMapper();
        MetricsConfiguration.CONFIG_MAP.put(
                SearchBackPressureStatsCollector.class, MetricsConfiguration.cdefault);
        System.setProperty("performanceanalyzer.metrics.log.enabled", "False");
        startTimeInMills = 1153721339;
        controller = Mockito.mock(PerformanceAnalyzerController.class);
        configOverrides = Mockito.mock(ConfigOverridesWrapper.class);
        searchBackPressureStatsCollector =
                new SearchBackPressureStatsCollector(controller, configOverrides);

        HEAP_USAGE_TRACKER_MOCK_STATS =
                new SearchBackPressureStatsCollector.ResourceUsageTrackerStats(0, 0, 0, 0, false);
        CPU_USAGE_TRACKER_MOCK_STATS =
                new SearchBackPressureStatsCollector.ResourceUsageTrackerStats(0, 0, 0, 0, false);
        ELAPSED_TIME_TRACKER_MOCK_STATS =
                new SearchBackPressureStatsCollector.ResourceUsageTrackerStats(0, 0, 0, 0, false);
    }

    /*
     * testSearchBackPressureStats_collectMetrics() test saveMetricValues() for SearchBackPressureStatsCollector
     */
    @Test
    public void testSearchBackPressureStats_saveMetricValues() {
        Mockito.when(
                        controller.isCollectorEnabled(
                                configOverrides, "SearchBackPressureStatsCollector"))
                .thenReturn(true);
        searchBackPressureStatsCollector.saveMetricValues("search_back_pressure", startTimeInMills);
        List<Event> metrics = new ArrayList<>();
        PerformanceAnalyzerMetrics.metricQueue.drainTo(metrics);

        // Valid case testing
        assertEquals(1, metrics.size());
        assertEquals("search_back_pressure", metrics.get(0).value);

        // Exception case testing
        try {
            searchBackPressureStatsCollector.saveMetricValues(
                    "search_back_pressure", startTimeInMills, "dummy");
            assertTrue("Negative scenario test: Should have been a RuntimeException", true);
        } catch (RuntimeException ex) {
            // - expecting exception...1 values passed; 0 expected
            // since keyPath does not match
        }
    }

    /*
     * testSearchBackPressureStats_collectMetrics() test collectoMetrics() for SearchBackPressureStatsCollector
     * Mock the behavior getSearchBackPressureStats() to return a mock SearchBackPressureStats Instance
     */
    @Test
    public void testSearchBackPressureStats_collectMetrics()
            throws NoSuchMethodException, IllegalAccessException, InvocationTargetException,
                    JsonProcessingException, NoSuchFieldException, ClassNotFoundException {
        String SEARCH_BACK_PRESSURE_MODE_FIELD_NAME = "searchbp_mode";
        SearchBackPressureStatsCollector spyCollector =
                Mockito.spy(searchBackPressureStatsCollector);

        Map<String, SearchBackPressureStatsCollector.ResourceUsageTrackerStats>
                resource_usage_mock_stats =
                        Map.ofEntries(
                                Map.entry("HEAP_USAGE_TRACKER", HEAP_USAGE_TRACKER_MOCK_STATS),
                                Map.entry("CPU_USAGE_TRACKER", CPU_USAGE_TRACKER_MOCK_STATS),
                                Map.entry("ELAPSED_TIME_TRACKER", ELAPSED_TIME_TRACKER_MOCK_STATS));

        Mockito.doReturn(
                        new SearchBackPressureStatsCollector.SearchBackPressureStats(
                                new SearchBackPressureStatsCollector.SearchShardTaskStats(
                                        0, 0, 0, resource_usage_mock_stats),
                                "MONITOR_ONLY",
                                new SearchBackPressureStatsCollector.SearchTaskStats(
                                        0, 0, 0, resource_usage_mock_stats)))
                .when(spyCollector)
                .getSearchBackPressureStats();

        Mockito.when(
                        controller.isCollectorEnabled(
                                configOverrides,
                                SearchBackPressureStatsCollector.class.getSimpleName()))
                .thenReturn(true);

        spyCollector.collectMetrics(startTimeInMills);
        List<Event> metrics = new ArrayList<>();
        PerformanceAnalyzerMetrics.metricQueue.drainTo(metrics);

        assertEquals(1, metrics.size());

        String[] lines = metrics.get(0).value.split(System.lineSeparator());
        Map<String, String> map = mapper.readValue(lines[1], Map.class);

        // Verify requried fields are all presented in the metrics
        String jsonStr = lines[1];
        for (String required_field : required_fields_for_searchBackPressureStats) {
            assertTrue(jsonStr.contains(required_field));
        }
    }
}
