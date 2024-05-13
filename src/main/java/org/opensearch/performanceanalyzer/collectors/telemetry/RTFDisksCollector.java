/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.OSMetricsGeneratorFactory;
import org.opensearch.performanceanalyzer.commons.collectors.DisksCollector;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics_generator.DiskMetricsGenerator;
import org.opensearch.performanceanalyzer.commons.metrics_generator.OSMetricsGenerator;
import org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode;
import org.opensearch.performanceanalyzer.commons.stats.metrics.StatMetrics;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;

public class RTFDisksCollector extends PerformanceAnalyzerMetricsCollector {

    private Histogram DiskWaitTimeMetrics;
    private Histogram DiskServiceRateMetrics;
    private Histogram DiskUtilizationMetrics;

    private MetricsRegistry metricsRegistry;
    private static final Logger LOG = LogManager.getLogger(RTFDisksCollector.class);

    public RTFDisksCollector() {
        super(
                MetricsConfiguration.CONFIG_MAP.get(DisksCollector.class).samplingInterval,
                "RTFDisksCollector",
                StatMetrics.DISKS_COLLECTOR_EXECUTION_TIME,
                StatExceptionCode.DISK_METRICS_COLLECTOR_ERROR);
    }

    @Override
    public void collectMetrics(long startTime) {
        OSMetricsGenerator generator = OSMetricsGeneratorFactory.getInstance();
        if (generator == null) {
            LOG.error("could not get the instance of OSMetricsGeneratorFactory class");
            return;
        }

        metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        if (metricsRegistry == null) {
            LOG.error("could not get the instance of MetricsRegistry class");
            return;
        }

        LOG.info("Executing collect metrics for RTFDisksCollector");

        initialiseMetrics();
        DiskMetricsGenerator diskMetricsGenerator = generator.getDiskMetricsGenerator();
        diskMetricsGenerator.addSample();

        recordMetrics(diskMetricsGenerator);
    }

    private void recordMetrics(DiskMetricsGenerator diskMetricsGenerator) {
        for (String disk : diskMetricsGenerator.getAllDisks()) {
            Tags DiskNameTag =
                    Tags.create().addTag(AllMetrics.DiskDimension.Constants.NAME_VALUE, disk);
            double Disk_WaitTime = diskMetricsGenerator.getAwait(disk);
            double Disk_ServiceRate = diskMetricsGenerator.getServiceRate(disk);
            double Disk_Utilization = diskMetricsGenerator.getDiskUtilization(disk);
            DiskWaitTimeMetrics.record(Disk_WaitTime, DiskNameTag);
            DiskUtilizationMetrics.record(Disk_Utilization, DiskNameTag);
            DiskServiceRateMetrics.record(Disk_ServiceRate, DiskNameTag);
        }
    }

    private void initialiseMetrics() {
        DiskWaitTimeMetrics =
                metricsRegistry.createHistogram(
                        AllMetrics.DiskValue.Constants.WAIT_VALUE, "DiskWaitTimeMetrics", "1");
        DiskServiceRateMetrics =
                metricsRegistry.createHistogram(
                        AllMetrics.DiskValue.Constants.SRATE_VALUE, "DiskServiceRateMetrics", "1");
        DiskUtilizationMetrics =
                metricsRegistry.createHistogram(
                        AllMetrics.DiskValue.Constants.UTIL_VALUE, "DiskUtilizationMetrics", "1");
    }
}
