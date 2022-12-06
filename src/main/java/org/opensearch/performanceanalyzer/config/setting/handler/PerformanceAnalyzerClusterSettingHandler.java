/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.config.setting.handler;


import java.util.*;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.config.setting.ClusterSettingListener;
import org.opensearch.performanceanalyzer.config.setting.ClusterSettingsManager;
import org.opensearch.performanceanalyzer.config.setting.PerformanceAnalyzerClusterSettings;

public class PerformanceAnalyzerClusterSettingHandler implements ClusterSettingListener<Integer> {
    private static final int BIT_ONE = 1;
    private static final int CLUSTER_SETTING_DISABLED_VALUE = 0;
    private static final int ENABLED_VALUE = 1;
    private static final int MAX_ALLOWED_BIT_POS =
            Math.min(
                    PerformanceAnalyzerClusterSettings.PerformanceAnalyzerFeatureBits.values()
                            .length,
                    Integer.SIZE - 1);
    private static final int RCA_ENABLED_BIT_POS =
            PerformanceAnalyzerClusterSettings.PerformanceAnalyzerFeatureBits.RCA_BIT.ordinal();
    private static final int PA_ENABLED_BIT_POS =
            PerformanceAnalyzerClusterSettings.PerformanceAnalyzerFeatureBits.PA_BIT.ordinal();
    private static final int LOGGING_ENABLED_BIT_POS =
            PerformanceAnalyzerClusterSettings.PerformanceAnalyzerFeatureBits.LOGGING_BIT.ordinal();
    private static final int BATCH_METRICS_ENABLED_BIT_POS =
            PerformanceAnalyzerClusterSettings.PerformanceAnalyzerFeatureBits.BATCH_METRICS_BIT
                    .ordinal();
    private static final int THREAD_CONTENTION_MONITORING_ENABLED_BIT_POS =
            PerformanceAnalyzerClusterSettings.PerformanceAnalyzerFeatureBits
                    .THREAD_CONTENTION_MONITORING_BIT
                    .ordinal();

    static final String PA_ENABLED_KEY = "PerformanceAnalyzerEnabled";
    static final String RCA_ENABLED_KEY = "RcaEnabled";
    static final String LOGGING_ENABLED_KEY = "LoggingEnabled";
    static final String BATCH_METRICS_ENABLED_KEY = "BatchMetricsEnabled";
    static final String THREAD_CONTENTION_MONITORING_ENABLED_KEY =
            "ThreadContentionMonitoringEnabled";

    private final PerformanceAnalyzerController controller;
    private final ClusterSettingsManager clusterSettingsManager;

    private Integer currentClusterSetting;

    public PerformanceAnalyzerClusterSettingHandler(
            final PerformanceAnalyzerController controller,
            final ClusterSettingsManager clusterSettingsManager) {
        this.controller = controller;
        this.clusterSettingsManager = clusterSettingsManager;
        this.currentClusterSetting =
                initializeClusterSettingValue(
                        controller.isPerformanceAnalyzerEnabled(),
                        controller.isRcaEnabled(),
                        controller.isLoggingEnabled(),
                        controller.isBatchMetricsEnabled(),
                        controller.isThreadContentionMonitoringEnabled());
    }

    /**
     * Updates the Performance Analyzer setting across the cluster.
     *
     * @param state The desired state for performance analyzer.
     */
    public void updatePerformanceAnalyzerSetting(final boolean state) {
        final Integer settingIntValue = getPASettingValueFromState(state);
        clusterSettingsManager.updateSetting(
                PerformanceAnalyzerClusterSettings.COMPOSITE_PA_SETTING, settingIntValue);
    }

    /**
     * Updates the Logging setting across the cluster.
     *
     * @param state The desired state for logging.
     */
    public void updateLoggingSetting(final boolean state) {
        final Integer settingIntValue = getLoggingSettingValueFromState(state);
        clusterSettingsManager.updateSetting(
                PerformanceAnalyzerClusterSettings.COMPOSITE_PA_SETTING, settingIntValue);
    }

    /**
     * Updates the RCA setting across the cluster.
     *
     * @param state The desired state for RCA.
     */
    public void updateRcaSetting(final boolean state) {
        final Integer settingIntValue = getRcaSettingValueFromState(state);
        clusterSettingsManager.updateSetting(
                PerformanceAnalyzerClusterSettings.COMPOSITE_PA_SETTING, settingIntValue);
    }

    /**
     * Updates the Batch Metrics setting across the cluster.
     *
     * @param state The desired state for batch metrics.
     */
    public void updateBatchMetricsSetting(final boolean state) {
        final Integer settingIntValue = getBatchMetricsSettingValueFromState(state);
        clusterSettingsManager.updateSetting(
                PerformanceAnalyzerClusterSettings.COMPOSITE_PA_SETTING, settingIntValue);
    }

    /**
     * Updates the Thread Contention Monitoring setting across the cluster.
     *
     * @param state The desired state for Thread Contention Monitoring setting.
     */
    public void updateThreadContentionMonitoringSetting(final boolean state) {
        final Integer settingIntValue = getThreadContentionMonitoringSettingValueFromState(state);
        clusterSettingsManager.updateSetting(
                PerformanceAnalyzerClusterSettings.COMPOSITE_PA_SETTING, settingIntValue);
    }

    /**
     * Handler that gets called when there is a new value for the setting that this listener is
     * listening to.
     *
     * @param newSettingValue The value of the new setting.
     */
    @Override
    public void onSettingUpdate(final Integer newSettingValue) {
        currentClusterSetting = newSettingValue;
        if (newSettingValue != null) {
            controller.updatePerformanceAnalyzerState(getPAStateFromSetting(newSettingValue));
            controller.updateRcaState(getRcaStateFromSetting(newSettingValue));
            controller.updateLoggingState(getLoggingStateFromSetting(newSettingValue));
            controller.updateBatchMetricsState(getBatchMetricsStateFromSetting(newSettingValue));
            controller.updateThreadContentionMonitoringState(
                    getThreadContentionMonitoringStateFromSetting(newSettingValue));
        }
    }

    /**
     * Gets the current(last seen) cluster setting value.
     *
     * @return the current cluster setting value if exists. Initial cluster setting otherwise.
     */
    public Map<String, Boolean> getCurrentClusterSettingValue() {
        Map<String, Boolean> statusMap = new LinkedHashMap<String, Boolean>();
        statusMap.put(PA_ENABLED_KEY, getPAStateFromSetting(currentClusterSetting.intValue()));
        statusMap.put(RCA_ENABLED_KEY, getRcaStateFromSetting(currentClusterSetting.intValue()));
        statusMap.put(
                LOGGING_ENABLED_KEY, getLoggingStateFromSetting(currentClusterSetting.intValue()));
        statusMap.put(
                BATCH_METRICS_ENABLED_KEY,
                getBatchMetricsStateFromSetting(currentClusterSetting.intValue()));
        statusMap.put(
                THREAD_CONTENTION_MONITORING_ENABLED_KEY,
                getThreadContentionMonitoringStateFromSetting(currentClusterSetting.intValue()));

        return statusMap;
    }

    /**
     * Gets the cluster settings from the controller.
     *
     * @param paEnabled If performance analyzer is enabled/disabled.
     * @param rcaEnabled If rca is enabled/disabled.
     * @param loggingEnabled If logging is enabled/disabled.
     * @return the cluster setting value
     */
    private Integer initializeClusterSettingValue(
            final boolean paEnabled,
            final boolean rcaEnabled,
            final boolean loggingEnabled,
            final boolean batchMetricsEnabled,
            final boolean threadContentionMonitoringEnabled) {
        int clusterSetting = CLUSTER_SETTING_DISABLED_VALUE;

        clusterSetting = paEnabled ? setBit(clusterSetting, PA_ENABLED_BIT_POS) : clusterSetting;
        if (paEnabled) {
            clusterSetting =
                    rcaEnabled ? setBit(clusterSetting, RCA_ENABLED_BIT_POS) : clusterSetting;
            clusterSetting =
                    loggingEnabled
                            ? setBit(clusterSetting, LOGGING_ENABLED_BIT_POS)
                            : clusterSetting;
            clusterSetting =
                    batchMetricsEnabled
                            ? setBit(clusterSetting, BATCH_METRICS_ENABLED_BIT_POS)
                            : clusterSetting;
            clusterSetting =
                    threadContentionMonitoringEnabled
                            ? setBit(clusterSetting, THREAD_CONTENTION_MONITORING_ENABLED_BIT_POS)
                            : clusterSetting;
        }
        return clusterSetting;
    }

    /**
     * Extracts the boolean value for performance analyzer from the cluster setting.
     *
     * @param settingValue The composite setting value.
     * @return true if the PA_ENABLED bit is set, false otherwise.
     */
    private boolean getPAStateFromSetting(final int settingValue) {
        return ((settingValue >> PA_ENABLED_BIT_POS) & BIT_ONE) == ENABLED_VALUE;
    }

    /**
     * Converts the boolean PA state to composite cluster setting. If Performance Analyzer is being
     * turned off, it will also turn off RCA, logging, batch metrics and thread contention
     * monitoring.
     *
     * @param state the state of performance analyzer. Will enable performance analyzer if true,
     *     disables performance analyzer, RCA, logging, batch metrics and thread contention
     *     monitoring.
     * @return composite cluster setting as an integer.
     */
    private Integer getPASettingValueFromState(final boolean state) {
        int clusterSetting = currentClusterSetting;

        if (state) {
            return setBit(clusterSetting, PA_ENABLED_BIT_POS);
        } else {
            return resetBit(
                    resetBit(
                            resetBit(
                                    resetBit(
                                            resetBit(clusterSetting, PA_ENABLED_BIT_POS),
                                            RCA_ENABLED_BIT_POS),
                                    LOGGING_ENABLED_BIT_POS),
                            BATCH_METRICS_ENABLED_BIT_POS),
                    THREAD_CONTENTION_MONITORING_ENABLED_BIT_POS);
        }
    }

    /**
     * Extracts the boolean value for RCA state from the cluster setting.
     *
     * @param settingValue The composite setting value.
     * @return true if the RCA_ENABLED bit is set, false otherwise.
     */
    private boolean getRcaStateFromSetting(final int settingValue) {
        return ((settingValue >> RCA_ENABLED_BIT_POS) & BIT_ONE) == ENABLED_VALUE;
    }

    /**
     * Extracts the boolean value for logging state from the cluster setting.
     *
     * @param settingValue The composite setting value.
     * @return true if the LOGGING bit is set, false otherwise.
     */
    private boolean getLoggingStateFromSetting(final int settingValue) {
        return ((settingValue >> LOGGING_ENABLED_BIT_POS) & BIT_ONE) == ENABLED_VALUE;
    }

    /**
     * Extracts the boolean value for batch metrics state from the cluster setting.
     *
     * @param settingValue The composite setting value.
     * @return true if the BATCH_METRICS bit is set, false otherwise.
     */
    private boolean getBatchMetricsStateFromSetting(final int settingValue) {
        return ((settingValue >> BATCH_METRICS_ENABLED_BIT_POS) & BIT_ONE) == ENABLED_VALUE;
    }

    /**
     * Extracts the boolean value for thread contention monitoring state from the cluster setting.
     *
     * @param settingValue The composite setting value.
     * @return true if the THREAD_CONTENTION_MONITORING bit is set, false otherwise.
     */
    private boolean getThreadContentionMonitoringStateFromSetting(final int settingValue) {
        return ((settingValue >> THREAD_CONTENTION_MONITORING_ENABLED_BIT_POS) & BIT_ONE)
                == ENABLED_VALUE;
    }

    /**
     * Converts the boolean RCA state to composite cluster setting. Enables RCA only if performance
     * analyzer is also set. Otherwise, results in a no-op.
     *
     * @param shouldEnable the state of rca. Will try to enable if true, disables RCA if false.
     * @return composite cluster setting as an integer.
     */
    private Integer getRcaSettingValueFromState(final boolean shouldEnable) {
        int clusterSetting = currentClusterSetting;

        if (shouldEnable) {
            return checkBit(currentClusterSetting, PA_ENABLED_BIT_POS)
                    ? setBit(clusterSetting, RCA_ENABLED_BIT_POS)
                    : clusterSetting;
        } else {
            return resetBit(clusterSetting, RCA_ENABLED_BIT_POS);
        }
    }

    /**
     * Converts the boolean logging state to composite cluster setting. Enables logging only if
     * performance analyzer is also set. Otherwise, results in a no-op.
     *
     * @param shouldEnable the state of logging. Will try to enable if true, disables logging if
     *     false.
     * @return composite cluster setting as an integer.
     */
    private Integer getLoggingSettingValueFromState(final boolean shouldEnable) {
        int clusterSetting = currentClusterSetting;

        if (shouldEnable) {
            return checkBit(currentClusterSetting, PA_ENABLED_BIT_POS)
                    ? setBit(clusterSetting, LOGGING_ENABLED_BIT_POS)
                    : clusterSetting;
        } else {
            return resetBit(clusterSetting, LOGGING_ENABLED_BIT_POS);
        }
    }

    /**
     * Converts the boolean batch metrics state to composite cluster setting. Enables batch metrics
     * only if performance analyzer is also set. Otherwise, results in a no-op.
     *
     * @param shouldEnable the state of batch metrics. Will try to enable if true, disables batch
     *     metrics if false.
     * @return composite cluster setting as an integer.
     */
    private Integer getBatchMetricsSettingValueFromState(final boolean shouldEnable) {
        int clusterSetting = currentClusterSetting;

        if (shouldEnable) {
            return checkBit(currentClusterSetting, PA_ENABLED_BIT_POS)
                    ? setBit(clusterSetting, BATCH_METRICS_ENABLED_BIT_POS)
                    : clusterSetting;
        } else {
            return resetBit(clusterSetting, BATCH_METRICS_ENABLED_BIT_POS);
        }
    }

    /**
     * Converts the boolean thread contention monitoring state to composite cluster setting. Enables
     * thread contention monitoring only if performance analyzer is also set. Otherwise, results in
     * a no-op.
     *
     * @param shouldEnable the state of thread contention monitoring. Will try to enable if true,
     *     disables thread contention monitoring if false.
     * @return composite cluster setting as an integer.
     */
    private Integer getThreadContentionMonitoringSettingValueFromState(final boolean shouldEnable) {
        int clusterSetting = currentClusterSetting;

        if (shouldEnable) {
            return checkBit(currentClusterSetting, PA_ENABLED_BIT_POS)
                    ? setBit(clusterSetting, THREAD_CONTENTION_MONITORING_ENABLED_BIT_POS)
                    : clusterSetting;
        } else {
            return resetBit(clusterSetting, THREAD_CONTENTION_MONITORING_ENABLED_BIT_POS);
        }
    }

    /**
     * Sets the bit at the specified position.
     *
     * @param number The number in which a needs to be set.
     * @param bitPosition The position of the bit in the number
     * @return number with the bit set at the specified position.
     */
    private int setBit(int number, int bitPosition) {
        return bitPosition < MAX_ALLOWED_BIT_POS ? (number | (1 << bitPosition)) : number;
    }

    /**
     * Resets the bit at the specified position.
     *
     * @param number The number in which a needs to be reset.
     * @param bitPosition The position of the bit in the number
     * @return number with the bit reset at the specified position.
     */
    private int resetBit(int number, int bitPosition) {
        return bitPosition < MAX_ALLOWED_BIT_POS ? (number & ~(1 << bitPosition)) : number;
    }

    /**
     * Checks if the bit is set or not at the specified position.
     *
     * @param clusterSettingValue The number which needs to be checked.
     * @param bitPosition The position of the bit in the clusterSettingValue
     * @return true if the bit is set, false otherwise.
     */
    public static boolean checkBit(int clusterSettingValue, int bitPosition) {
        return ((bitPosition < MAX_ALLOWED_BIT_POS)
                && (clusterSettingValue & (1 << bitPosition)) > 0);
    }
}
