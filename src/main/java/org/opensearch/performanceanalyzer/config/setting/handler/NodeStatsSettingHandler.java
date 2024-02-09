/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.config.setting.handler;

import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.config.setting.ClusterSettingListener;
import org.opensearch.performanceanalyzer.config.setting.ClusterSettingsManager;
import org.opensearch.performanceanalyzer.config.setting.PerformanceAnalyzerClusterSettings;

public class NodeStatsSettingHandler implements ClusterSettingListener<Integer> {
    private final PerformanceAnalyzerController controller;
    private final ClusterSettingsManager clusterSettingsManager;

    private Integer currentClusterSetting =
            PerformanceAnalyzerController.DEFAULT_NUM_OF_SHARDS_PER_COLLECTION;

    public NodeStatsSettingHandler(
            final PerformanceAnalyzerController controller,
            final ClusterSettingsManager clusterSettingsManager) {
        this.controller = controller;
        this.clusterSettingsManager = clusterSettingsManager;
    }

    /**
     * Updates the PA Node Stats setting across the cluster.
     *
     * @param value The desired num of shards value for node stats.
     */
    public void updateNodeStatsSetting(final int value) {
        clusterSettingsManager.updateSetting(
                PerformanceAnalyzerClusterSettings.PA_NODE_STATS_SETTING, value);
    }

    /**
     * Handler that gets called when there is a new value for the setting that this listener is
     * listening to.
     *
     * @param newSettingValue The value of the new setting.
     */
    @Override
    public void onSettingUpdate(final Integer newSettingValue) {
        if (newSettingValue != null) {
            currentClusterSetting = newSettingValue;
            controller.updateNodeStatsShardsPerCollection(newSettingValue);
        }
    }

    /**
     * Gets the current(last seen) cluster setting value.
     *
     * @return integer value for setting.
     */
    public int getNodeStatsSetting() {
        return currentClusterSetting;
    }
}
