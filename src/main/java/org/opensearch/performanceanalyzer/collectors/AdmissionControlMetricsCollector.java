/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode.ADMISSION_CONTROL_COLLECTOR_ERROR;
import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatMetrics.ADMISSION_CONTROL_COLLECTOR_EXECUTION_TIME;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.performanceanalyzer.commons.collectors.MetricStatus;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.collectors.RcaCollector;
import org.opensearch.performanceanalyzer.commons.collectors.StatsCollector;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.commons.stats.ServiceMetrics;
import org.opensearch.performanceanalyzer.commons.stats.metrics.StatMetrics;

/** AdmissionControlMetricsCollector collects `UsedQuota`, `TotalQuota`, RejectionCount */
public class AdmissionControlMetricsCollector extends PerformanceAnalyzerMetricsCollector
        implements MetricsProcessor, RcaCollector {

    private static final Logger LOG = LogManager.getLogger(AdmissionControlMetricsCollector.class);
    private static final int sTimeInterval = MetricsConfiguration.SAMPLING_INTERVAL;
    private static final int KEYS_PATH_LENGTH = 0;
    private StringBuilder value;

    // Global JVM Memory Pressure Controller
    private static final String GLOBAL_JVMMP = "Global_JVMMP";

    // Request Size Controller
    private static final String REQUEST_SIZE = "Request_Size";

    private static final String ADMISSION_CONTROLLER =
            "com.sonian.opensearch.http.jetty.throttling.AdmissionController";

    private static final String ADMISSION_CONTROL_SERVICE =
            "com.sonian.opensearch.http.jetty.throttling.JettyAdmissionControlService";

    private Class admissionControllerClass;
    private Class jettyAdmissionControllerServiceClass;
    private final boolean admissionControllerAvailable;

    public AdmissionControlMetricsCollector() {
        super(
                sTimeInterval,
                "AdmissionControlMetricsCollector",
                ADMISSION_CONTROL_COLLECTOR_EXECUTION_TIME,
                ADMISSION_CONTROL_COLLECTOR_ERROR);
        this.value = new StringBuilder();
        this.admissionControllerAvailable = canLoadAdmissionControllerClasses();
    }

    @Override
    @SuppressWarnings("unchecked")
    public void collectMetrics(long startTime) {
        if (!this.admissionControllerAvailable) {
            LOG.debug("AdmissionControl is not available for this domain");
            ServiceMetrics.COMMONS_STAT_METRICS_AGGREGATOR.updateStat(
                    StatMetrics.ADMISSION_CONTROL_COLLECTOR_NOT_AVAILABLE, 1);
            return;
        }

        try {

            Method getAdmissionController =
                    this.jettyAdmissionControllerServiceClass.getDeclaredMethod(
                            "getAdmissionController", String.class);

            Object globalJVMMP = getAdmissionController.invoke(null, GLOBAL_JVMMP);
            Object requestSize = getAdmissionController.invoke(null, REQUEST_SIZE);

            if (Objects.isNull(globalJVMMP) && Objects.isNull(requestSize)) {
                return;
            }

            value.setLength(0);

            Method getUsedQuota = this.admissionControllerClass.getDeclaredMethod("getUsedQuota");
            Method getTotalQuota = this.admissionControllerClass.getDeclaredMethod("getTotalQuota");
            Method getRejectionCount =
                    this.admissionControllerClass.getDeclaredMethod("getRejectionCount");

            if (!Objects.isNull(globalJVMMP)) {
                value.append(PerformanceAnalyzerMetrics.getJsonCurrentMilliSeconds())
                        .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                        .append(
                                new AdmissionControlMetrics(
                                                GLOBAL_JVMMP,
                                                (long) getUsedQuota.invoke(globalJVMMP),
                                                (long) getTotalQuota.invoke(globalJVMMP),
                                                (long) getRejectionCount.invoke(globalJVMMP))
                                        .serialize());
            }

            if (!Objects.isNull(requestSize)) {
                value.append(PerformanceAnalyzerMetrics.getJsonCurrentMilliSeconds())
                        .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                        .append(
                                new AdmissionControlMetrics(
                                                REQUEST_SIZE,
                                                (long) getUsedQuota.invoke(requestSize),
                                                (long) getTotalQuota.invoke(requestSize),
                                                (long) getRejectionCount.invoke(requestSize))
                                        .serialize());
            }

            saveMetricValues(value.toString(), startTime);
        } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException ex) {
            LOG.debug(
                    "[ {} ] Exception in collecting AdmissionControl Metrics: {}",
                    this::getCollectorName,
                    ex::getMessage);
            StatsCollector.instance().logException(ADMISSION_CONTROL_COLLECTOR_ERROR);
        }
    }

    @Override
    public String getMetricsPath(long startTime, String... keysPath) {
        if (keysPath.length != KEYS_PATH_LENGTH) {
            throw new RuntimeException("keys length should be " + KEYS_PATH_LENGTH);
        }
        return PerformanceAnalyzerMetrics.generatePath(
                startTime, PerformanceAnalyzerMetrics.sAdmissionControlMetricsPath);
    }

    static class AdmissionControlMetrics extends MetricStatus {

        private String controllerName;
        private long current;
        private long threshold;
        private long rejectionCount;

        public AdmissionControlMetrics() {
            super();
        }

        public AdmissionControlMetrics(
                String controllerName, long current, long threshold, long rejectionCount) {
            super();
            this.controllerName = controllerName;
            this.current = current;
            this.threshold = threshold;
            this.rejectionCount = rejectionCount;
        }

        @JsonProperty(AllMetrics.AdmissionControlDimension.Constants.CONTROLLER_NAME)
        public String getControllerName() {
            return controllerName;
        }

        @JsonProperty(AllMetrics.AdmissionControlValue.Constants.CURRENT_VALUE)
        public long getCurrent() {
            return current;
        }

        @JsonProperty(AllMetrics.AdmissionControlValue.Constants.THRESHOLD_VALUE)
        public long getThreshold() {
            return threshold;
        }

        @JsonProperty(AllMetrics.AdmissionControlValue.Constants.REJECTION_COUNT)
        public long getRejectionCount() {
            return rejectionCount;
        }
    }

    private boolean canLoadAdmissionControllerClasses() {
        try {
            ClassLoader admissionControlClassLoader = this.getClass().getClassLoader().getParent();
            this.admissionControllerClass =
                    Class.forName(ADMISSION_CONTROLLER, false, admissionControlClassLoader);
            this.jettyAdmissionControllerServiceClass =
                    Class.forName(ADMISSION_CONTROL_SERVICE, false, admissionControlClassLoader);
        } catch (Exception e) {
            LOG.debug("Failed to load AdmissionControllerService classes : {}", e::toString);
            StatsCollector.instance().logException(ADMISSION_CONTROL_COLLECTOR_ERROR);
            return false;
        }
        return true;
    }
}
