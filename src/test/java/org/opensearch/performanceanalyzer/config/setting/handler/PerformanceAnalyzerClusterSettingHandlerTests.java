/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.config.setting.handler;

import static org.junit.Assert.assertEquals;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.when;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.config.setting.ClusterSettingsManager;

public class PerformanceAnalyzerClusterSettingHandlerTests {
    private static final Boolean DISABLED_STATE = Boolean.FALSE;
    private static final Boolean ENABLED_STATE = Boolean.TRUE;

    private final Map<String, Boolean> ALL_ENABLED_CLUSTER =
            createStatusMap(
                    ENABLED_STATE, ENABLED_STATE, ENABLED_STATE, ENABLED_STATE, ENABLED_STATE);
    private final Map<String, Boolean> ALL_DISABLED_CLUSTER =
            createStatusMap(
                    DISABLED_STATE, DISABLED_STATE, DISABLED_STATE, DISABLED_STATE, DISABLED_STATE);
    private final Map<String, Boolean> MIXED_STATUS_CLUSTER =
            createStatusMap(
                    ENABLED_STATE, ENABLED_STATE, DISABLED_STATE, DISABLED_STATE, DISABLED_STATE);

    private PerformanceAnalyzerClusterSettingHandler clusterSettingHandler;

    @Mock private PerformanceAnalyzerController mockPerformanceAnalyzerController;
    @Mock private ClusterSettingsManager mockClusterSettingsManager;

    @Before
    public void setup() {
        initMocks(this);
    }

    @Test
    public void disabledClusterStateTest() {
        setControllerValues(
                DISABLED_STATE, DISABLED_STATE, DISABLED_STATE, DISABLED_STATE, DISABLED_STATE);
        clusterSettingHandler =
                new PerformanceAnalyzerClusterSettingHandler(
                        mockPerformanceAnalyzerController, mockClusterSettingsManager);
        assertEquals(0, clusterSettingHandler.getCurrentClusterSettingValue());
        assertEquals(
                ALL_DISABLED_CLUSTER, clusterSettingHandler.getCurrentClusterSettingValueVerbose());
    }

    @Test
    public void enabledClusterStateTest() {
        setControllerValues(
                ENABLED_STATE, ENABLED_STATE, ENABLED_STATE, ENABLED_STATE, ENABLED_STATE);
        clusterSettingHandler =
                new PerformanceAnalyzerClusterSettingHandler(
                        mockPerformanceAnalyzerController, mockClusterSettingsManager);
        assertEquals(31, clusterSettingHandler.getCurrentClusterSettingValue());
        assertEquals(
                ALL_ENABLED_CLUSTER, clusterSettingHandler.getCurrentClusterSettingValueVerbose());
    }

    @Test
    public void paDisabledClusterStateTest() {
        setControllerValues(
                DISABLED_STATE, ENABLED_STATE, ENABLED_STATE, ENABLED_STATE, ENABLED_STATE);
        clusterSettingHandler =
                new PerformanceAnalyzerClusterSettingHandler(
                        mockPerformanceAnalyzerController, mockClusterSettingsManager);
        assertEquals(0, clusterSettingHandler.getCurrentClusterSettingValue());
        assertEquals(
                ALL_DISABLED_CLUSTER, clusterSettingHandler.getCurrentClusterSettingValueVerbose());
    }

    @Test
    public void updateClusterStateTest() {
        setControllerValues(
                ENABLED_STATE, ENABLED_STATE, DISABLED_STATE, DISABLED_STATE, DISABLED_STATE);
        clusterSettingHandler =
                new PerformanceAnalyzerClusterSettingHandler(
                        mockPerformanceAnalyzerController, mockClusterSettingsManager);
        assertEquals(3, clusterSettingHandler.getCurrentClusterSettingValue());
        assertEquals(
                MIXED_STATUS_CLUSTER, clusterSettingHandler.getCurrentClusterSettingValueVerbose());
        clusterSettingHandler.onSettingUpdate(0);
        assertEquals(0, clusterSettingHandler.getCurrentClusterSettingValue());
        assertEquals(
                ALL_DISABLED_CLUSTER, clusterSettingHandler.getCurrentClusterSettingValueVerbose());
    }

    private void setControllerValues(
            final Boolean paEnabled,
            final Boolean rcaEnabled,
            final Boolean loggingEnabled,
            final Boolean batchMetricsEnabled,
            final Boolean threadContentionMonitoringEnabled) {
        when(mockPerformanceAnalyzerController.isPerformanceAnalyzerEnabled())
                .thenReturn(paEnabled);
        when(mockPerformanceAnalyzerController.isRcaEnabled()).thenReturn(rcaEnabled);
        when(mockPerformanceAnalyzerController.isLoggingEnabled()).thenReturn(loggingEnabled);
        when(mockPerformanceAnalyzerController.isBatchMetricsEnabled())
                .thenReturn(batchMetricsEnabled);
        when(mockPerformanceAnalyzerController.isThreadContentionMonitoringEnabled())
                .thenReturn(threadContentionMonitoringEnabled);
    }

    private Map<String, Boolean> createStatusMap(
            final Boolean paEnabled,
            final Boolean rcaEnabled,
            final Boolean loggingEnabled,
            final Boolean batchMetricsEnabled,
            final Boolean threadContentionMonitoringEnabled) {
        Map<String, Boolean> statusMap = new LinkedHashMap<String, Boolean>();
        statusMap.put(PerformanceAnalyzerClusterSettingHandler.PA_ENABLED_KEY, paEnabled);
        statusMap.put(PerformanceAnalyzerClusterSettingHandler.RCA_ENABLED_KEY, rcaEnabled);
        statusMap.put(PerformanceAnalyzerClusterSettingHandler.LOGGING_ENABLED_KEY, loggingEnabled);
        statusMap.put(
                PerformanceAnalyzerClusterSettingHandler.BATCH_METRICS_ENABLED_KEY,
                batchMetricsEnabled);
        statusMap.put(
                PerformanceAnalyzerClusterSettingHandler.THREAD_CONTENTION_MONITORING_ENABLED_KEY,
                threadContentionMonitoringEnabled);
        return statusMap;
    }
}
