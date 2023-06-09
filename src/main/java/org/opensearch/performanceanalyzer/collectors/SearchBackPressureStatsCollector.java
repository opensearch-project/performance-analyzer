/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode.CLUSTER_APPLIER_SERVICE_STATS_COLLECTOR_ERROR;
import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatMetrics.CLUSTER_APPLIER_SERVICE_STATS_COLLECTOR_EXECUTION_TIME;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.lang.reflect.InvocationTargetException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.performanceanalyzer.commons.collectors.MetricStatus;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.node.NodeService;
import org.opensearch.search.backpressure.SearchBackpressureService;

public class SearchBackPressureStatsCollector extends PerformanceAnalyzerMetricsCollector
        implements MetricsProcessor {
    // SAMPLING Interval to collect search back pressure stats (Add the Collector to the Config Map)
    // Read only (what does the class return)
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(SearchBackPressureStatsCollector.class)
                    .samplingInterval;
    private static final int KEYS_PATH_LENGTH = 0;
    private static final Logger LOG = LogManager.getLogger(SearchBackPressureStatsCollector.class);
    private static final ObjectMapper mapper;
    // Specify the method name to query for SearchBackPressure Stats
    private static final String GET_SEARCH_BACK_PRESSURE_STATS_METHOD_NAME =
            "getSearchBackPressureStats";
    private static volatile SearchBackPressureStats prevSearchBackPressureStats =
            new SearchBackPressureStats();
    // Metrics to be collected as a String and written in a JSON String
    private final StringBuilder value;
    private final PerformanceAnalyzerController controller;
    private final ConfigOverridesWrapper configOverridesWrapper;

    static {
        mapper = new ObjectMapper();
        mapper.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public SearchBackPressureStatsCollector(
            PerformanceAnalyzerController controller,
            ConfigOverridesWrapper configOverridesWrapper) {
        super(
                SAMPLING_TIME_INTERVAL,
                SearchBackPressureStatsCollector.class.getSimpleName(),
                CLUSTER_APPLIER_SERVICE_STATS_COLLECTOR_EXECUTION_TIME,
                CLUSTER_APPLIER_SERVICE_STATS_COLLECTOR_ERROR);

        this.controller = controller;
        this.configOverridesWrapper = configOverridesWrapper;
        this.value = new StringBuilder();
        // Log that the collecotr is spinning up
        LOG.info("SearchBackPressureStatsCollector is started");
    }
    // Override the collectMetrics method to return the Metrics as a String
    @Override
    public void collectMetrics(long startTime) {
        //  if (!controller.isCollectorEnabled(configOverridesWrapper, getCollectorName())) {
        //     return;
        //  }
        LOG.info("CollectMetrics of SearchBackPressure is started");

        SearchBackPressureStats currentSearchBackPressureStats = null;
        try {
            if (getSearchBackPressureStats() == null) currentSearchBackPressureStats = null;
            else {
                currentSearchBackPressureStats =
                        mapper.readValue(
                                mapper.writeValueAsString(getSearchBackPressureStats()),
                                SearchBackPressureStats.class);
            }
        } catch (InvocationTargetException
                | IllegalAccessException
                | NoSuchMethodException
                | JsonProcessingException ex) {
            LOG.warn(
                    "No method found to get Search Back Pressure stats. "
                            + "Skipping SearchBackPressureStatsCollector");
            return;
        }

        SearchBackPressureMetrics searchBackPressureMetrics = new SearchBackPressureMetrics(2.0);
        LOG.info(
                "searchbackpressure test count "
                        + Double.toString(
                                searchBackPressureMetrics.getSearchBackPressureStatsTest()));

        value.setLength(0);
        if (currentSearchBackPressureStats == null)
            LOG.info("currentSearchBackPressureStats is null");
        else
            LOG.info(
                    "currentSearchBackPressureStats is"
                            + currentSearchBackPressureStats.toString());
        if (searchBackPressureMetrics == null) LOG.info("searchBackPressureMetrics is null");
        else LOG.info("searchBackPressureMetrics is" + searchBackPressureMetrics.toString());
        LOG.info("searchBackPressureMetrics.serialize(): " + searchBackPressureMetrics.serialize());

        // Append system current time (required for standardized metrics)
        value.append(PerformanceAnalyzerMetrics.getJsonCurrentMilliSeconds())
                .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor);
        value.append(searchBackPressureMetrics.serialize());

        // print out the value by LOG
        LOG.info("value is: " + value.toString());

        saveMetricValues(value.toString(), startTime);
        // update the previous stats
        // SearchBackPressureStatsCollector.prevSearchBackPressureStats =
        //         currentSearchBackPressureStats;
    }

    @VisibleForTesting
    public void resetPrevSearchBackPressureStats() {
        SearchBackPressureStatsCollector.prevSearchBackPressureStats =
                new SearchBackPressureStats();
    }

    @VisibleForTesting
    public Object getSearchBackPressureStats()
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException {
        string GET_STATS_METHOD_NAME = "nodeStats";
        Method method = ClusterApplierService.class.getMethod(GET_STATS_METHOD_NAME);


        // create an instance of nodeService
        // and use the nodeservice to  getSearchBackpressureService()
        return method.invoke(
            OpenSearchResources.INSTANCE.getClusterService().getClusterApplierService());
    }
    // compute the test count based on the current stats and previous stats (initially set to 0 for
    // testing)
    private double computeTestCount(final SearchBackPressureStats currentStats) {
        return 1.0;
    }

    @Override
    public String getMetricsPath(long startTime, String... keysPath) {
        if (keysPath.length != KEYS_PATH_LENGTH) {
            throw new RuntimeException("keys length should be " + KEYS_PATH_LENGTH);
        }
        return PerformanceAnalyzerMetrics.generatePath(startTime, "search_back_pressure");
    }

    public static class SearchBackPressureStats {
        public SearchBackPressureStats() {}
    }

    public static class SearchBackPressureMetrics extends MetricStatus {
        private double searchBackPressureStatsTest;
        // private double clusterStateAppliedFailedCount;
        // private double clusterStateAppliedTimeInMillis;
        public SearchBackPressureMetrics(double searchBackPressureStats_Test) {
            this.searchBackPressureStatsTest = searchBackPressureStats_Test;
        }
        // JSON Property maps to the field in ?metrics=DESIRED_PROPERTY_NAME to be searched by user
        // @JsonProperty(
        //         AllMetrics.ClusterApplierServiceStatsValue.Constants
        //                 .CLUSTER_APPLIER_SERVICE_LATENCY)
        // public double getClusterApplierServiceLatency() {
        //     return clusterStateAppliedTimeInMillis;
        // }
        @JsonProperty("SearchBackPressureStats_Test")
        public double getSearchBackPressureStatsTest() {
            return searchBackPressureStatsTest;
        }
    }
}
