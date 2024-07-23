/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.OSMetricsGeneratorFactory;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.collectors.TelemetryCollector;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.jvm.ThreadList;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.RTFMetrics;
import org.opensearch.performanceanalyzer.commons.metrics_generator.CPUPagingActivityGenerator;
import org.opensearch.performanceanalyzer.commons.metrics_generator.OSMetricsGenerator;
import org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode;
import org.opensearch.performanceanalyzer.commons.stats.metrics.StatMetrics;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;

public class RTFOSMetricsCollector extends PerformanceAnalyzerMetricsCollector
        implements TelemetryCollector {
    private MetricsRegistry metricsRegistry;
    private boolean metricsInitialised;
    private Histogram cpuUtilizationMetrics;
    private static final Logger LOG = LogManager.getLogger(RTFOSMetricsCollector.class);
    private PerformanceAnalyzerController performanceAnalyzerController;
    private ConfigOverridesWrapper configOverridesWrapper;
    private OSMetricsGenerator osMetricsGenerator;

    public RTFOSMetricsCollector(
            PerformanceAnalyzerController performanceAnalyzerController,
            ConfigOverridesWrapper configOverridesWrapper) {
        super(
                MetricsConfiguration.CONFIG_MAP.get(ThreadList.class).samplingInterval,
                "RTFOSMetricsCollector",
                StatMetrics.RTF_OS_METRICS_COLLECTOR_EXECUTION_TIME,
                StatExceptionCode.RTF_OS_METRICS_COLLECTOR_ERROR);
        this.metricsInitialised = false;
        this.performanceAnalyzerController = performanceAnalyzerController;
        this.configOverridesWrapper = configOverridesWrapper;
        osMetricsGenerator = OSMetricsGeneratorFactory.getInstance();
    }

    @Override
    public void collectMetrics(long l) {
        if (performanceAnalyzerController.isCollectorDisabled(
                configOverridesWrapper, getCollectorName())) {
            LOG.info("RTFOSMetricsCollector is disabled. Skipping collection.");
            return;
        }

        osMetricsGenerator = OSMetricsGeneratorFactory.getInstance();
        if (osMetricsGenerator == null) {
            LOG.error("could not get the instance of OSMetricsGeneratorFactory class");
            return;
        }

        metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        if (metricsRegistry == null) {
            LOG.error("could not get the instance of MetricsRegistry class");
            return;
        }

        LOG.debug("Executing collect metrics for RTFOSMetricsCollector");
        initialiseMetricsIfNeeded();
        CPUPagingActivityGenerator threadCPUPagingActivityGenerator =
                osMetricsGenerator.getPagingActivityGenerator();
        threadCPUPagingActivityGenerator.addSample();

        recordMetrics(threadCPUPagingActivityGenerator);
    }

    public void recordMetrics(CPUPagingActivityGenerator threadCPUPagingActivityGenerator) {
        for (String threadId : osMetricsGenerator.getAllThreadIds()) {
            cpuUtilizationMetrics.record(
                    threadCPUPagingActivityGenerator.getCPUUtilization(threadId),
                    Tags.create().addTag("thread_id", threadId));
        }
    }

    private void initialiseMetricsIfNeeded() {
        if (metricsInitialised == false) {
            cpuUtilizationMetrics =
                    metricsRegistry.createHistogram(
                            RTFMetrics.OSMetrics.CPU_UTILIZATION.toString(),
                            "CPUUtilizationMetrics",
                            RTFMetrics.MetricUnits.PERCENT.toString());
            metricsInitialised = true;
        }
    }
}
