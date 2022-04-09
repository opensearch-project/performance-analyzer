/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.config;


import java.io.File;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Scanner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.PerformanceAnalyzerApp;
import org.opensearch.performanceanalyzer.PerformanceAnalyzerPlugin;
import org.opensearch.performanceanalyzer.collectors.ScheduledMetricCollectorsExecutor;
import org.opensearch.performanceanalyzer.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.rca.framework.metrics.ExceptionsAndErrors;

public class PerformanceAnalyzerController {
    private static final String PERFORMANCE_ANALYZER_ENABLED_CONF =
            "performance_analyzer_enabled.conf";
    private static final String RCA_ENABLED_CONF = "rca_enabled.conf";
    private static final String LOGGING_ENABLED_CONF = "logging_enabled.conf";
    // This file should contain "true" or "false", indicating whether batch metrics is currently
    // enabled or not.
    private static final String BATCH_METRICS_ENABLED_CONF = "batch_metrics_enabled.conf";
    private static final String THREAD_CONTENTION_MONITORING_ENABLED_CONF = "thread_contention_monitoring_enabled.conf";
    private static final Logger LOG = LogManager.getLogger(PerformanceAnalyzerController.class);
    public static final int DEFAULT_NUM_OF_SHARDS_PER_COLLECTION = 0;

    private boolean paEnabled;
    private boolean rcaEnabled;
    private boolean loggingEnabled;
    private boolean batchMetricsEnabled;
    private boolean threadContentionMonitoringEnabled;
    private volatile int shardsPerCollection;
    private static final boolean paEnabledDefaultValue = false;
    private static final boolean rcaEnabledDefaultValue = true;
    private static final boolean loggingEnabledDefaultValue = false;
    private static final boolean batchMetricsEnabledDefaultValue = false;
    private static final boolean threadContentionMonitoringEnabledDefaultValue = false;
    private final ScheduledMetricCollectorsExecutor scheduledMetricCollectorsExecutor;

    public PerformanceAnalyzerController(
            final ScheduledMetricCollectorsExecutor scheduledMetricCollectorsExecutor) {
        this.scheduledMetricCollectorsExecutor = scheduledMetricCollectorsExecutor;
        initPerformanceAnalyzerStateFromConf();
        initRcaStateFromConf();
        initLoggingStateFromConf();
        initBatchMetricsStateFromConf();
        initThreadContentionMonitoringStateFromConf();
        shardsPerCollection = DEFAULT_NUM_OF_SHARDS_PER_COLLECTION;
    }

    /**
     * Returns the current state of performance analyzer.
     *
     * <p>This setting is indicative of both the engine and the plugin state. When enabled, both the
     * writer and the engine are active. The writer captures metrics and stores it while the engine
     * processes the stored metrics. When disabled, the writer stops capturing and no collector
     * performs actual work of collecting metrics even though the threads are still running. The
     * reader will not do any processing even though it is kept running.
     *
     * @return the state of performance analyzer.
     */
    public boolean isPerformanceAnalyzerEnabled() {
        return paEnabled;
    }

    /**
     * Returns the state of RCA framework. When enabled, the RCA scheduler will run at a periodicity
     * defined by the RCA scheduler. When disabled, the RCA scheduler is stopped.
     *
     * @return the state of RCA framework.
     */
    public boolean isRcaEnabled() {
        return rcaEnabled;
    }

    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    public boolean isBatchMetricsEnabled() {
        return batchMetricsEnabled;
    }

    public boolean isThreadContentionMonitoringEnabled() {
        return threadContentionMonitoringEnabled;
    }

    /**
     * Reads the shardsPerCollection parameter in NodeStatsMetric
     *
     * @return the count of Shards per Collection
     */
    public int getNodeStatsShardsPerCollection() {
        return shardsPerCollection;
    }

    /**
     * Updates the shardsPerCollection parameter in NodeStatsMetric
     *
     * @param value the desired integer value for Shards per Collection
     */
    public void updateNodeStatsShardsPerCollection(int value) {
        shardsPerCollection = value;
    }

    /**
     * Updates the state of performance analyzer(writer and engine).
     *
     * @param value The desired state of performance analyzer. False to disable, and true to enable.
     */
    public void updatePerformanceAnalyzerState(final boolean value) {
        this.paEnabled = value;
        if (scheduledMetricCollectorsExecutor != null) {
            scheduledMetricCollectorsExecutor.setEnabled(this.paEnabled);
        }
        saveStateToConf(this.paEnabled, PERFORMANCE_ANALYZER_ENABLED_CONF);
    }

    /**
     * Updates the state of RCA scheduler. TODO: Migrate the updating of RCA toggling to the engine.
     *
     * @param shouldEnable The desired state of rca. False to disable, true to enable if performance
     *     analyzer is also enabled.
     */
    public void updateRcaState(final boolean shouldEnable) {
        if (shouldEnable && !isPerformanceAnalyzerEnabled()) {
            return;
        }

        this.rcaEnabled = shouldEnable;
        saveStateToConf(this.rcaEnabled, RCA_ENABLED_CONF);
    }

    /**
     * Updates the state of performance analyzer logging.
     *
     * @param shouldEnable The desired state of performance analyzer logging. False to disable, and
     *     true to enable.
     */
    public void updateLoggingState(final boolean shouldEnable) {
        if (shouldEnable && !isPerformanceAnalyzerEnabled()) {
            return;
        }
        this.loggingEnabled = shouldEnable;
        if (scheduledMetricCollectorsExecutor != null) {
            PerformanceAnalyzerMetrics.setIsMetricsLogEnabled(this.loggingEnabled);
        }
        saveStateToConf(this.loggingEnabled, LOGGING_ENABLED_CONF);
    }

    /**
     * Updates the state of the batch metrics api.
     *
     * @param shouldEnable The desired state of the batch metrics api. False to disable, and true to
     *     enable.
     */
    public void updateBatchMetricsState(final boolean shouldEnable) {
        if (shouldEnable && !isPerformanceAnalyzerEnabled()) {
            return;
        }

        this.batchMetricsEnabled = shouldEnable;
        saveStateToConf(this.batchMetricsEnabled, BATCH_METRICS_ENABLED_CONF);
    }

    /**
     * Updates the state of the thread contention monitoring api.
     *
     * @param shouldEnable The desired state of the thread contention monitoring api. False to disable, and true to
     *     enable.
     */
    public void updateThreadContentionMonitoringState(final boolean shouldEnable) {
        if (shouldEnable && !isPerformanceAnalyzerEnabled()) {
            return;
        }
        this.threadContentionMonitoringEnabled = shouldEnable;
        if (scheduledMetricCollectorsExecutor != null) {
            scheduledMetricCollectorsExecutor.setThreadContentionMonitoringEnabled(threadContentionMonitoringEnabled);
        }
        saveStateToConf(this.threadContentionMonitoringEnabled, THREAD_CONTENTION_MONITORING_ENABLED_CONF);
    }

    private void initPerformanceAnalyzerStateFromConf() {
        Path filePath = Paths.get(getDataDirectory(), PERFORMANCE_ANALYZER_ENABLED_CONF);
        PerformanceAnalyzerPlugin.invokePrivileged(
                () -> {
                    boolean paEnabledFromConf;
                    try {
                        paEnabledFromConf = readBooleanFromFile(filePath);
                    } catch (Exception e) {
                        LOG.debug("Error reading Performance Analyzer state from Conf file", e);
                        if (e instanceof NoSuchFileException) {
                            saveStateToConf(
                                    paEnabledDefaultValue, PERFORMANCE_ANALYZER_ENABLED_CONF);
                        }
                        paEnabledFromConf = paEnabledDefaultValue;
                    }

                    updatePerformanceAnalyzerState(paEnabledFromConf);
                });
    }

    private void initRcaStateFromConf() {
        Path filePath = Paths.get(getDataDirectory(), RCA_ENABLED_CONF);
        PerformanceAnalyzerPlugin.invokePrivileged(
                () -> {
                    boolean rcaEnabledFromConf;
                    try {
                        rcaEnabledFromConf = readBooleanFromFile(filePath);
                    } catch (Exception e) {
                        LOG.debug("Error reading Performance Analyzer state from Conf file", e);
                        if (e instanceof NoSuchFileException) {
                            saveStateToConf(rcaEnabledDefaultValue, RCA_ENABLED_CONF);
                        }
                        rcaEnabledFromConf = rcaEnabledDefaultValue;
                    }

                    // For RCA framework to be enabled, it needs both PA and RCA to be enabled.
                    updateRcaState(paEnabled && rcaEnabledFromConf);
                });
    }

    private void initLoggingStateFromConf() {
        Path filePath = Paths.get(getDataDirectory(), LOGGING_ENABLED_CONF);
        PerformanceAnalyzerPlugin.invokePrivileged(
                () -> {
                    boolean loggingEnabledFromConf;
                    try {
                        loggingEnabledFromConf = readBooleanFromFile(filePath);
                    } catch (Exception e) {
                        LOG.debug("Error reading logging state from Conf file", e);
                        if (e instanceof NoSuchFileException) {
                            saveStateToConf(loggingEnabledDefaultValue, LOGGING_ENABLED_CONF);
                        }
                        loggingEnabledFromConf = loggingEnabledDefaultValue;
                    }

                    // For logging to be enabled, it needs both PA and Logging to be enabled.
                    updateLoggingState(paEnabled && loggingEnabledFromConf);
                });
    }

    private void initBatchMetricsStateFromConf() {
        Path filePath = Paths.get(getDataDirectory(), BATCH_METRICS_ENABLED_CONF);
        PerformanceAnalyzerPlugin.invokePrivileged(
                () -> {
                    boolean batchMetricsEnabledFromConf;
                    try {
                        batchMetricsEnabledFromConf = readBooleanFromFile(filePath);
                    } catch (NoSuchFileException e) {
                        LOG.debug("Error reading Performance Analyzer state from Conf file", e);
                        saveStateToConf(
                                batchMetricsEnabledDefaultValue, BATCH_METRICS_ENABLED_CONF);
                        batchMetricsEnabledFromConf = batchMetricsEnabledDefaultValue;
                    } catch (Exception e) {
                        LOG.debug("Error reading Performance Analyzer state from Conf file", e);
                        batchMetricsEnabledFromConf = batchMetricsEnabledDefaultValue;
                    }

                    // For batch metrics to be enabled, it needs both PA and Batch Metrics to be
                    // enabled.
                    updateBatchMetricsState(paEnabled && batchMetricsEnabledFromConf);
                });
    }

    private void initThreadContentionMonitoringStateFromConf() {
        Path filePath = Paths.get(getDataDirectory(), THREAD_CONTENTION_MONITORING_ENABLED_CONF);
        PerformanceAnalyzerPlugin.invokePrivileged(
                () -> {
                    boolean threadContentionMonitoringEnabledFromConf;
                    try {
                        threadContentionMonitoringEnabledFromConf = readBooleanFromFile(filePath);
                    } catch (NoSuchFileException e) {
                        LOG.debug("Error reading Performance Analyzer state from Conf file", e);
                        saveStateToConf(
                                threadContentionMonitoringEnabledDefaultValue, THREAD_CONTENTION_MONITORING_ENABLED_CONF);
                        threadContentionMonitoringEnabledFromConf = threadContentionMonitoringEnabledDefaultValue;
                    } catch (Exception e) {
                        LOG.debug("Error reading thread contention monitoring state from Conf file", e);
                        threadContentionMonitoringEnabledFromConf = threadContentionMonitoringEnabledDefaultValue;
                    }

                    // For thread contention monitoring to be enabled, both PA and thread contention monitoring
                    // need to enabled.
                    updateThreadContentionMonitoringState(paEnabled && threadContentionMonitoringEnabledFromConf);
                });
    }

    private boolean readBooleanFromFile(final Path filePath) throws Exception {
        try (Scanner sc = new Scanner(filePath)) {
            String nextLine = sc.nextLine();
            return Boolean.parseBoolean(nextLine);
        }
    }

    private String getDataDirectory() {
        return new org.opensearch.env.Environment(
                        OpenSearchResources.INSTANCE.getSettings(),
                        OpenSearchResources.INSTANCE.getConfigPath())
                .dataFiles()[0] // $OPENSEARCH_HOME/var/opensearch/data
                .toFile()
                .getPath();
    }

    private void saveStateToConf(boolean featureEnabled, String fileName) {
        PerformanceAnalyzerPlugin.invokePrivileged(
                () -> {
                    try {
                        Path destDir = Paths.get(getDataDirectory());
                        if (!Files.exists(destDir)) {
                            PerformanceAnalyzerApp.ERRORS_AND_EXCEPTIONS_AGGREGATOR.updateStat(
                                    ExceptionsAndErrors.CONFIG_DIR_NOT_FOUND, "", 1);
                            Files.createDirectory(destDir);
                        }
                        Files.write(
                                Paths.get(getDataDirectory() + File.separator + fileName),
                                String.valueOf(featureEnabled).getBytes());
                    } catch (Exception ex) {
                        LOG.error(ex.toString(), ex);
                    }
                });
    }

    public boolean isCollectorEnabled(
            ConfigOverridesWrapper configOverridesWrapper, String collectorName) {
        if (configOverridesWrapper == null) {
            return false;
        }
        List<String> enabledCollectorsList =
                configOverridesWrapper
                        .getCurrentClusterConfigOverrides()
                        .getEnable()
                        .getCollectors();
        return enabledCollectorsList.contains(collectorName) ? true : false;
    }

    public boolean isCollectorDisabled(
            ConfigOverridesWrapper configOverridesWrapper, String collectorName) {
        if (configOverridesWrapper == null) {
            return true;
        }

        List<String> disabledCollectorsList =
                configOverridesWrapper
                        .getCurrentClusterConfigOverrides()
                        .getDisable()
                        .getCollectors();

        return disabledCollectorsList.contains(collectorName);
    }
}
