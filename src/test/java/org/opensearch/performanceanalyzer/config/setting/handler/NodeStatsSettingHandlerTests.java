/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.config.setting.handler;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.config.setting.ClusterSettingsManager;

public class NodeStatsSettingHandlerTests {
    private NodeStatsSettingHandler handler;

    @Mock private PerformanceAnalyzerController controller;
    @Mock private ClusterSettingsManager clusterSettingsManager;

    @Before
    public void init() {
        initMocks(this);
        handler = new NodeStatsSettingHandler(controller, clusterSettingsManager);
    }

    @Test
    public void testOnSettingUpdate() {
        Integer newSettingValue = null;
        handler.onSettingUpdate(newSettingValue);
        verify(controller, never()).updateNodeStatsShardsPerCollection(anyInt());

        newSettingValue = 1;
        handler.onSettingUpdate(newSettingValue);
        verify(controller).updateNodeStatsShardsPerCollection(newSettingValue);
    }
}
