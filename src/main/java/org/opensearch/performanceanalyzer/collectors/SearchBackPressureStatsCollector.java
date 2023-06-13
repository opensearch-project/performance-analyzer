/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode.CLUSTER_APPLIER_SERVICE_STATS_COLLECTOR_ERROR;
import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatMetrics.CLUSTER_APPLIER_SERVICE_STATS_COLLECTOR_EXECUTION_TIME;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.node.Node;
import org.opensearch.node.NodeService;
import org.opensearch.performanceanalyzer.commons.collectors.MetricStatus;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.search.backpressure.SearchBackpressureService;
import org.opensearch.search.backpressure.stats.SearchShardTaskStats;
import org.opensearch.search.backpressure.stats.SearchTaskStats;

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

    public static final String BOOTSTRAP_CLASS_NAME = "org.opensearch.bootstrap.Bootstrap";
    public static final String NODE_CLASS_NAME = "org.opensearch.node.Node";
    public static final String BOOTSTRAP_INSTANCE_FIELD_NAME = "INSTANCE";
    public static final String BOOTSTRAP_NODE_FIELD_NAME = "node";
    public static final String NODE_SERVICE_FIELD_NAME = "nodeService";

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
                LOG.info(
                        "7. Object from  getSearchBackPressureStats(): "
                                + ReflectionToStringBuilder.toString(getSearchBackPressureStats()));
                String jsonString = mapper.writeValueAsString(getSearchBackPressureStats());
                LOG.info("8. POJO STRING: " + jsonString);
                currentSearchBackPressureStats =
                        mapper.readValue(jsonString, SearchBackPressureStats.class);
                LOG.info(
                        "9. Deserialized SearchBackPressure stats (Mode): "
                                + currentSearchBackPressureStats.getMode()
                                + "| All Stats: "
                                + mapper.writeValueAsString(currentSearchBackPressureStats));
            }
        } catch (InvocationTargetException
                | IllegalAccessException
                | NoSuchMethodException
                | NoSuchFieldException
                | ClassNotFoundException
                | JsonProcessingException ex) {
            ex.printStackTrace();
            LOG.warn(
                    "No method found to get Search BackPressure Stats. "
                            + "Skipping SearchBackPressureStatsCollector. Error: "
                            + ex.getMessage());
            return;
        }

        SearchBackPressureMetrics searchBackPressureMetrics =
                new SearchBackPressureMetrics(currentSearchBackPressureStats.getMode());

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

    // getField to use reflection to get private fields (Node node) in BootStrap
    Field getField(String className, String fieldName)
            throws NoSuchFieldException, ClassNotFoundException {

        Class<?> BootStrapClass = Class.forName(className);
        Field bootStrapField = BootStrapClass.getDeclaredField(fieldName);

        // set the field to be accessible
        bootStrapField.setAccessible(true);
        return bootStrapField;
    }

    @VisibleForTesting
    public void resetPrevSearchBackPressureStats() {
        SearchBackPressureStatsCollector.prevSearchBackPressureStats =
                new SearchBackPressureStats();
    }

    @VisibleForTesting
    public Object getSearchBackPressureStats()
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException,
                    NoSuchFieldException, NoSuchFieldError, ClassNotFoundException {

        LOG.info("1.getSearchBackPressureStats() start");
        // Get the static instance of Bootstrap
        Object bootStrapSInstance =
                getField(BOOTSTRAP_CLASS_NAME, BOOTSTRAP_INSTANCE_FIELD_NAME).get(null);

        LOG.info("2.bootStrapSInstance created:" + bootStrapSInstance.toString());
        // Get the Node instance from the Bootstrap instance
        Node node =
                (Node)
                        getField(BOOTSTRAP_CLASS_NAME, BOOTSTRAP_NODE_FIELD_NAME)
                                .get(bootStrapSInstance);

        LOG.info("3.Node instance from BootStrap Instance created!");

        // Get the NodeService instance from the Node instance
        NodeService nodeService =
                (NodeService) getField(NODE_CLASS_NAME, NODE_SERVICE_FIELD_NAME).get(node);

        LOG.info("4.nodeService created!");

        String GET_STATS_METHOD_NAME = "nodeStats";
        Method method = SearchBackpressureService.class.getMethod(GET_STATS_METHOD_NAME);
        LOG.info("5.NodeService toString(): " + nodeService.toString());

        // create an instance of nodeService
        // and use the nodeservice to  getSearchBackpressureService()
        return method.invoke(nodeService.getSearchBackpressureService());
        // return null;
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
        private SearchShardTaskStats searchShardTaskStats;
        private String mode;
        private SearchTaskStats searchTaskStats;

        @VisibleForTesting
        @JsonCreator
        public SearchBackPressureStats(
                @JsonProperty("searchShardTaskStats") SearchShardTaskStats searchShardTaskStats,
                @JsonProperty("mode") String mode,
                @JsonProperty("searchTaskStats") SearchTaskStats searchTaskStats) {
            this.searchShardTaskStats = searchShardTaskStats;
            this.mode = mode;
            this.searchTaskStats = searchTaskStats;
        }

        public SearchBackPressureStats() {}

        // Getters and setters
        public String getMode() {
            return mode;
        }
    }

    public static class SearchShardTaskStats {
        private long cancellationCount;
        private long limitReachedCount;
        private Map<String, ResourceUsageTrackerStats> resourceUsageTrackerStats;

        @JsonCreator
        public SearchShardTaskStats(
                @JsonProperty("cancellationCount") long cancellationCount,
                @JsonProperty("limitReachedCount") long limitReachedCount,
                @JsonProperty("resourceUsageTrackerStats")
                        Map<String, ResourceUsageTrackerStats> resourceUsageTrackerStats) {
            this.cancellationCount = cancellationCount;
            this.limitReachedCount = limitReachedCount;
            this.resourceUsageTrackerStats = resourceUsageTrackerStats;
        }
        // Getters
    }

    public static class SearchTaskStats {
        private long cancellationCount;
        private long limitReachedCount;
        private Map<String, ResourceUsageTrackerStats> resourceUsageTrackerStats;

        @JsonCreator
        public SearchTaskStats(
                @JsonProperty("cancellationCount") long cancellationCount,
                @JsonProperty("limitReachedCount") long limitReachedCount,
                @JsonProperty("resourceUsageTrackerStats")
                        Map<String, ResourceUsageTrackerStats> resourceUsageTrackerStats) {
            this.cancellationCount = cancellationCount;
            this.limitReachedCount = limitReachedCount;
            this.resourceUsageTrackerStats = resourceUsageTrackerStats;
        }
        // Getters
    }

    public static class ResourceUsageTrackerStats {
        private long cancellationCount;
        private long currentMax;
        private long currentAvg;
        private long rollingAvg;
        private boolean fragment;

        @JsonCreator
        public ResourceUsageTrackerStats(
                @JsonProperty("cancellationCount") long cancellationCount,
                @JsonProperty("currentMax") long currentMax,
                @JsonProperty("currentAvg") long currentAvg,
                @JsonProperty("rollingAvg") long rollingAvg,
                @JsonProperty("fragment") boolean fragment) {
            this.cancellationCount = cancellationCount;
            this.currentMax = currentMax;
            this.currentAvg = currentAvg;
            this.rollingAvg = rollingAvg;
            this.fragment = fragment;
        }

        // Getters
    }

    public static class SearchBackPressureMetrics extends MetricStatus {
        // private double searchBackPressureStatsTest;
        private String mode;
        // private double clusterStateAppliedFailedCount;
        // private double clusterStateAppliedTimeInMillis;
        public SearchBackPressureMetrics(String mode) {
            this.mode = mode;
        }
        // JSON Property maps to the field in ?metrics=DESIRED_PROPERTY_NAME to be searched by user
        // @JsonProperty(
        //         AllMetrics.ClusterApplierServiceStatsValue.Constants
        //                 .CLUSTER_APPLIER_SERVICE_LATENCY)
        // public double getClusterApplierServiceLatency() {
        //     return clusterStateAppliedTimeInMillis;
        // }
        // @JsonProperty("SearchBackPressureStats_Test")
        // public double getSearchBackPressureStatsTest() {
        //     return searchBackPressureStatsTest;
        // }

        @JsonProperty("SearchBackPressureStats_Mode")
        public String getMode() {
            return mode;
        }
    }
}
