/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.config.setting.handler;

import static org.junit.Assert.assertEquals;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.powermock.api.mockito.PowerMockito.when;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.config.setting.ClusterSettingsManager;

public class PerformanceAnalyzerClusterSettingHandlerTests {
    private static final Boolean DISABLED_STATE = Boolean.FALSE;
    private static final Boolean ENABLED_STATE = Boolean.TRUE;

    private PerformanceAnalyzerClusterSettingHandler clusterSettingHandler;

    @Mock private PerformanceAnalyzerController mockPerformanceAnalyzerController;
    @Mock private ClusterSettingsManager mockClusterSettingsManager;

    @Before
    public void setup() {
        initMocks(this);
    }

    @Test
    public void disabledClusterStateTest() {
        setControllerValues(DISABLED_STATE, DISABLED_STATE, DISABLED_STATE, DISABLED_STATE, DISABLED_STATE);
        clusterSettingHandler =
                new PerformanceAnalyzerClusterSettingHandler(
                        mockPerformanceAnalyzerController, mockClusterSettingsManager);
        assertEquals(0, clusterSettingHandler.getCurrentClusterSettingValue());
    }

    @Test
    public void enabledClusterStateTest() {
        setControllerValues(ENABLED_STATE, ENABLED_STATE, ENABLED_STATE, ENABLED_STATE, ENABLED_STATE);
        clusterSettingHandler =
                new PerformanceAnalyzerClusterSettingHandler(
                        mockPerformanceAnalyzerController, mockClusterSettingsManager);
        assertEquals(31, clusterSettingHandler.getCurrentClusterSettingValue());
    }

    @Test
    public void paDisabledClusterStateTest() {
        setControllerValues(DISABLED_STATE, ENABLED_STATE, ENABLED_STATE, ENABLED_STATE, ENABLED_STATE);
        clusterSettingHandler =
                new PerformanceAnalyzerClusterSettingHandler(
                        mockPerformanceAnalyzerController, mockClusterSettingsManager);
        assertEquals(0, clusterSettingHandler.getCurrentClusterSettingValue());
    }

    @Test
    public void updateClusterStateTest() {
        setControllerValues(ENABLED_STATE, ENABLED_STATE, DISABLED_STATE, DISABLED_STATE, DISABLED_STATE);
        clusterSettingHandler =
                new PerformanceAnalyzerClusterSettingHandler(
                        mockPerformanceAnalyzerController, mockClusterSettingsManager);
        assertEquals(3, clusterSettingHandler.getCurrentClusterSettingValue());
        clusterSettingHandler.onSettingUpdate(0);
        assertEquals(0, clusterSettingHandler.getCurrentClusterSettingValue());
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
}
