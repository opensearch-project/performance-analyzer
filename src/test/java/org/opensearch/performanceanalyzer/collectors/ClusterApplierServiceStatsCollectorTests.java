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
import java.util.List;
import java.util.Map;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.opensearch.cluster.service.ClusterApplierService;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.performanceanalyzer.CustomMetricsLocationTestBase;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.event_process.Event;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

@RunWith(PowerMockRunner.class)
@PrepareForTest({OpenSearchResources.class})
public class ClusterApplierServiceStatsCollectorTests extends CustomMetricsLocationTestBase {
    ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testClusterApplierServiceStats_saveMetricValues() {
        MetricsConfiguration.CONFIG_MAP.put(
                ClusterApplierServiceStatsCollector.class, MetricsConfiguration.cdefault);
        System.setProperty("performanceanalyzer.metrics.log.enabled", "False");
        long startTimeInMills = 1153721339;
        PerformanceAnalyzerController controller =
                Mockito.mock(PerformanceAnalyzerController.class);
        ConfigOverridesWrapper configOverrides = Mockito.mock(ConfigOverridesWrapper.class);
        ClusterApplierServiceStatsCollector clusterApplierServiceStatsCollector =
                new ClusterApplierServiceStatsCollector(controller, configOverrides);
        Mockito.when(
                        controller.isCollectorEnabled(
                                configOverrides, "ClusterApplierServiceStatsCollector"))
                .thenReturn(true);
        clusterApplierServiceStatsCollector.saveMetricValues(
                "cluster_applier_service", startTimeInMills);
        List<Event> metrics = new ArrayList<>();
        PerformanceAnalyzerMetrics.metricQueue.drainTo(metrics);

        assertEquals(1, metrics.size());
        assertEquals("cluster_applier_service", metrics.get(0).value);

        try {
            clusterApplierServiceStatsCollector.saveMetricValues(
                    "cluster_applier_service", startTimeInMills, "dummy");
            assertTrue("Negative scenario test: Should have been a RuntimeException", true);
        } catch (RuntimeException ex) {
            // - expecting exception...1 values passed; 0 expected
        }
    }

    @SuppressWarnings("unchecked")
    @Ignore
    @Test
    public void testClusterApplierServiceStats_collectMetrics()
            throws NoSuchMethodException,
                    IllegalAccessException,
                    InvocationTargetException,
                    JsonProcessingException {
        System.out.println("test 1");
        MetricsConfiguration.CONFIG_MAP.put(
                ClusterApplierServiceStatsCollector.class, MetricsConfiguration.cdefault);
        System.setProperty("performanceanalyzer.metrics.log.enabled", "False");
        long startTimeInMills = 1153721339;
        PerformanceAnalyzerController controller =
                Mockito.mock(PerformanceAnalyzerController.class);
        ConfigOverridesWrapper configOverrides = Mockito.mock(ConfigOverridesWrapper.class);
        ClusterApplierServiceStatsCollector clusterApplierServiceStatsCollector =
                new ClusterApplierServiceStatsCollector(controller, configOverrides);
        ClusterApplierServiceStatsCollector spyCollector =
                Mockito.spy(clusterApplierServiceStatsCollector);
        Mockito.doReturn(
                        new ClusterApplierServiceStatsCollector.ClusterApplierServiceStats(
                                23L, 15L, 2L, -1L))
                .when(spyCollector)
                .getClusterApplierServiceStats();
        Mockito.when(
                        controller.isCollectorEnabled(
                                configOverrides,
                                ClusterApplierServiceStatsCollector.class.getSimpleName()))
                .thenReturn(true);

        OpenSearchResources openSearchResources = Mockito.mock(OpenSearchResources.class);
        ClusterService clusterService = Mockito.mock(ClusterService.class);
        ClusterApplierService clusterApplierService = Mockito.mock(ClusterApplierService.class);
        Whitebox.setInternalState(OpenSearchResources.class, "INSTANCE", openSearchResources);
        Mockito.when(openSearchResources.getClusterService()).thenReturn(clusterService);
        Mockito.when(clusterService.getClusterApplierService()).thenReturn(clusterApplierService);

        spyCollector.collectMetrics(startTimeInMills);

        List<Event> metrics = new ArrayList<>();
        PerformanceAnalyzerMetrics.metricQueue.drainTo(metrics);

        assertEquals(1, metrics.size());
        String[] lines = metrics.get(0).value.split(System.lineSeparator());
        Map<String, String> map = mapper.readValue(lines[1], Map.class);
        assertEquals(
                0.6521739130434783,
                map.get(
                        AllMetrics.ClusterApplierServiceStatsValue.CLUSTER_APPLIER_SERVICE_LATENCY
                                .toString()));
        assertEquals(
                2.0,
                map.get(
                        AllMetrics.ClusterApplierServiceStatsValue.CLUSTER_APPLIER_SERVICE_FAILURE
                                .toString()));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testClusterApplierServiceStats_collectMetricsWithPreviousClusterApplierMetrics()
            throws NoSuchMethodException,
                    IllegalAccessException,
                    InvocationTargetException,
                    JsonProcessingException {
        System.out.println("test 2");
        MetricsConfiguration.CONFIG_MAP.put(
                ClusterApplierServiceStatsCollector.class, MetricsConfiguration.cdefault);
        System.setProperty("performanceanalyzer.metrics.log.enabled", "False");
        long startTimeInMills = 1153721339;
        PerformanceAnalyzerController controller =
                Mockito.mock(PerformanceAnalyzerController.class);
        ConfigOverridesWrapper configOverrides = Mockito.mock(ConfigOverridesWrapper.class);
        ClusterApplierServiceStatsCollector clusterApplierServiceStatsCollector =
                new ClusterApplierServiceStatsCollector(controller, configOverrides);
        ClusterApplierServiceStatsCollector spyCollector =
                Mockito.spy(clusterApplierServiceStatsCollector);
        Mockito.doReturn(
                        new ClusterApplierServiceStatsCollector.ClusterApplierServiceStats(
                                23L, 46L, 2L, -1L))
                .when(spyCollector)
                .getClusterApplierServiceStats();
        Mockito.when(
                        controller.isCollectorEnabled(
                                configOverrides,
                                ClusterApplierServiceStatsCollector.class.getSimpleName()))
                .thenReturn(true);

        OpenSearchResources openSearchResources = Mockito.mock(OpenSearchResources.class);
        ClusterService clusterService = Mockito.mock(ClusterService.class);
        ClusterApplierService clusterApplierService = Mockito.mock(ClusterApplierService.class);
        Whitebox.setInternalState(OpenSearchResources.class, "INSTANCE", openSearchResources);
        Mockito.when(openSearchResources.getClusterService()).thenReturn(clusterService);
        Mockito.when(clusterService.getClusterApplierService()).thenReturn(clusterApplierService);

        spyCollector.resetPrevClusterApplierServiceStats();
        spyCollector.collectMetrics(startTimeInMills);

        List<Event> metrics = new ArrayList<>();
        PerformanceAnalyzerMetrics.metricQueue.drainTo(metrics);

        assertEquals(1, metrics.size());
        String[] lines = metrics.get(0).value.split(System.lineSeparator());
        Map<String, String> map = mapper.readValue(lines[1], Map.class);
        assertEquals(
                2.0,
                map.get(
                        AllMetrics.ClusterApplierServiceStatsValue.CLUSTER_APPLIER_SERVICE_LATENCY
                                .toString()));
        assertEquals(
                2.0,
                map.get(
                        AllMetrics.ClusterApplierServiceStatsValue.CLUSTER_APPLIER_SERVICE_FAILURE
                                .toString()));

        Mockito.doReturn(
                        new ClusterApplierServiceStatsCollector.ClusterApplierServiceStats(
                                25L, 54L, 2L, -1L))
                .when(spyCollector)
                .getClusterApplierServiceStats();

        spyCollector.collectMetrics(startTimeInMills);

        metrics.clear();
        PerformanceAnalyzerMetrics.metricQueue.drainTo(metrics);

        assertEquals(1, metrics.size());
        String[] lines2 = metrics.get(0).value.split(System.lineSeparator());
        map = mapper.readValue(lines2[1], Map.class);
        assertEquals(
                4.0,
                map.get(
                        AllMetrics.ClusterApplierServiceStatsValue.CLUSTER_APPLIER_SERVICE_LATENCY
                                .toString()));
        assertEquals(
                0.0,
                map.get(
                        AllMetrics.ClusterApplierServiceStatsValue.CLUSTER_APPLIER_SERVICE_FAILURE
                                .toString()));
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testClusterApplierServiceStats_collectMetrics_ClassNotFoundException() {
        MetricsConfiguration.CONFIG_MAP.put(
                ClusterApplierServiceStatsCollector.class, MetricsConfiguration.cdefault);
        System.setProperty("performanceanalyzer.metrics.log.enabled", "False");
        long startTimeInMills = 1153721339;
        PerformanceAnalyzerController controller =
                Mockito.mock(PerformanceAnalyzerController.class);
        ConfigOverridesWrapper configOverrides = Mockito.mock(ConfigOverridesWrapper.class);
        ClusterApplierServiceStatsCollector clusterApplierServiceStatsCollector =
                new ClusterApplierServiceStatsCollector(controller, configOverrides);
        ClusterApplierServiceStatsCollector spyCollector =
                Mockito.spy(clusterApplierServiceStatsCollector);
        Mockito.when(
                        controller.isCollectorEnabled(
                                configOverrides,
                                ClusterApplierServiceStatsCollector.class.getSimpleName()))
                .thenReturn(true);

        OpenSearchResources openSearchResources = Mockito.mock(OpenSearchResources.class);
        ClusterService clusterService = Mockito.mock(ClusterService.class);
        ClusterApplierService clusterApplierService = Mockito.mock(ClusterApplierService.class);
        Whitebox.setInternalState(OpenSearchResources.class, "INSTANCE", openSearchResources);
        Mockito.when(openSearchResources.getClusterService()).thenReturn(clusterService);
        Mockito.when(clusterService.getClusterApplierService()).thenReturn(clusterApplierService);

        spyCollector.collectMetrics(startTimeInMills);

        List<Event> metrics = new ArrayList<>();
        PerformanceAnalyzerMetrics.metricQueue.drainTo(metrics);
        // No method found to get cluster state applier thread stats. Skipping
        // ClusterApplierServiceStatsCollector.
        assertEquals(0, metrics.size());
    }
}
