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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.env.NodeEnvironment;
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
    // SAMPLING TIME INTERVAL to collect search back pressure stats
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

    public static final String HEAP_USAGE_TRACKER_FIELD_NAME = "HEAP_USAGE_TRACKER";
    public static final String CPU_USAGE_TRACKER_FIELD_NAME = "CPU_USAGE_TRACKER";
    public static final String ELAPSED_TIME_USAGE_TRACKER_FIELD_NAME = "ELAPSED_TIME_TRACKER";

    // Headline for search back pressure metrics
    public static final String PATH_TO_STORE_METRICS = "search_back_pressure";
    private String nodeId;

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
    }

    private void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    private String getNodeId() {
        return this.nodeId;
    }

    @Override
    public void collectMetrics(long startTime) {
        // if (!controller.isCollectorEnabled(configOverridesWrapper, getCollectorName())) {
        //     return;
        // }

        SearchBackPressureStats currentSearchBackPressureStats = null;
        try {
            String jsonString = mapper.writeValueAsString(getSearchBackPressureStats());
            LOG.info("JSON STRING: " + jsonString);
            currentSearchBackPressureStats =
                    mapper.readValue(jsonString, SearchBackPressureStats.class);

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
                new SearchBackPressureMetrics(
                        currentSearchBackPressureStats.getMode(),
                        getNodeId(),
                        currentSearchBackPressureStats
                                .getSearchShardTaskStats()
                                .getCancellationCount(),
                        currentSearchBackPressureStats
                                .getSearchShardTaskStats()
                                .getLimitReachedCount(),
                        currentSearchBackPressureStats
                                .getSearchShardTaskStats()
                                .getResourceUsageTrackerStats()
                                .get(HEAP_USAGE_TRACKER_FIELD_NAME)
                                .getCancellationCount(),
                        currentSearchBackPressureStats
                                .getSearchShardTaskStats()
                                .getResourceUsageTrackerStats()
                                .get(HEAP_USAGE_TRACKER_FIELD_NAME)
                                .getCurrentMax(),
                        currentSearchBackPressureStats
                                .getSearchShardTaskStats()
                                .getResourceUsageTrackerStats()
                                .get(HEAP_USAGE_TRACKER_FIELD_NAME)
                                .getRollingAvg(),
                        currentSearchBackPressureStats
                                .getSearchShardTaskStats()
                                .getResourceUsageTrackerStats()
                                .get(CPU_USAGE_TRACKER_FIELD_NAME)
                                .getCancellationCount(),
                        currentSearchBackPressureStats
                                .getSearchShardTaskStats()
                                .getResourceUsageTrackerStats()
                                .get(CPU_USAGE_TRACKER_FIELD_NAME)
                                .getCurrentMax(),
                        currentSearchBackPressureStats
                                .getSearchShardTaskStats()
                                .getResourceUsageTrackerStats()
                                .get(CPU_USAGE_TRACKER_FIELD_NAME)
                                .getCurrentAvg(),
                        currentSearchBackPressureStats
                                .getSearchShardTaskStats()
                                .getResourceUsageTrackerStats()
                                .get(ELAPSED_TIME_USAGE_TRACKER_FIELD_NAME)
                                .getCancellationCount(),
                        currentSearchBackPressureStats
                                .getSearchShardTaskStats()
                                .getResourceUsageTrackerStats()
                                .get(ELAPSED_TIME_USAGE_TRACKER_FIELD_NAME)
                                .getCurrentMax(),
                        currentSearchBackPressureStats
                                .getSearchShardTaskStats()
                                .getResourceUsageTrackerStats()
                                .get(ELAPSED_TIME_USAGE_TRACKER_FIELD_NAME)
                                .getCurrentAvg(),
                        currentSearchBackPressureStats.getSearchTaskStats().getCancellationCount(),
                        currentSearchBackPressureStats.getSearchTaskStats().getLimitReachedCount(),
                        currentSearchBackPressureStats
                                .getSearchTaskStats()
                                .getResourceUsageTrackerStats()
                                .get(HEAP_USAGE_TRACKER_FIELD_NAME)
                                .getCancellationCount(),
                        currentSearchBackPressureStats
                                .getSearchTaskStats()
                                .getResourceUsageTrackerStats()
                                .get(HEAP_USAGE_TRACKER_FIELD_NAME)
                                .getCurrentMax(),
                        currentSearchBackPressureStats
                                .getSearchTaskStats()
                                .getResourceUsageTrackerStats()
                                .get(HEAP_USAGE_TRACKER_FIELD_NAME)
                                .getRollingAvg(),
                        currentSearchBackPressureStats
                                .getSearchTaskStats()
                                .getResourceUsageTrackerStats()
                                .get(CPU_USAGE_TRACKER_FIELD_NAME)
                                .getCancellationCount(),
                        currentSearchBackPressureStats
                                .getSearchTaskStats()
                                .getResourceUsageTrackerStats()
                                .get(CPU_USAGE_TRACKER_FIELD_NAME)
                                .getCurrentMax(),
                        currentSearchBackPressureStats
                                .getSearchTaskStats()
                                .getResourceUsageTrackerStats()
                                .get(CPU_USAGE_TRACKER_FIELD_NAME)
                                .getCurrentAvg(),
                        currentSearchBackPressureStats
                                .getSearchTaskStats()
                                .getResourceUsageTrackerStats()
                                .get(ELAPSED_TIME_USAGE_TRACKER_FIELD_NAME)
                                .getCancellationCount(),
                        currentSearchBackPressureStats
                                .getSearchTaskStats()
                                .getResourceUsageTrackerStats()
                                .get(ELAPSED_TIME_USAGE_TRACKER_FIELD_NAME)
                                .getCurrentMax(),
                        currentSearchBackPressureStats
                                .getSearchTaskStats()
                                .getResourceUsageTrackerStats()
                                .get(ELAPSED_TIME_USAGE_TRACKER_FIELD_NAME)
                                .getCurrentAvg());

        value.setLength(0);
        // Append system current time and line seperator
        value.append(PerformanceAnalyzerMetrics.getCurrentTimeMetric())
                .append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor);
        value.append(searchBackPressureMetrics.serialize());
        saveMetricValues(value.toString(), startTime);
    }

    Field getField(String className, String fieldName)
            throws NoSuchFieldException, ClassNotFoundException {

        Class<?> BootStrapClass = Class.forName(className);
        Field bootStrapField = BootStrapClass.getDeclaredField(fieldName);

        // set the field to be accessible
        bootStrapField.setAccessible(true);
        return bootStrapField;
    }

    @VisibleForTesting
    public Object getSearchBackPressureStats()
            throws InvocationTargetException, IllegalAccessException, NoSuchMethodException,
                    NoSuchFieldException, NoSuchFieldError, ClassNotFoundException {

        // Get the static instance of Bootstrap
        Object bootStrapSInstance =
                getField(BOOTSTRAP_CLASS_NAME, BOOTSTRAP_INSTANCE_FIELD_NAME).get(null);

        // Get the Node instance from the Bootstrap instance
        Node node =
                (Node)
                        getField(BOOTSTRAP_CLASS_NAME, BOOTSTRAP_NODE_FIELD_NAME)
                                .get(bootStrapSInstance);

        NodeEnvironment nodeEnvironment = node.getNodeEnvironment();
        setNodeId(nodeEnvironment.nodeId());

        // Get the NodeService instance from the Node instance
        NodeService nodeService =
                (NodeService) getField(NODE_CLASS_NAME, NODE_SERVICE_FIELD_NAME).get(node);

        String GET_STATS_METHOD_NAME = "nodeStats";
        Method method = SearchBackpressureService.class.getMethod(GET_STATS_METHOD_NAME);

        return method.invoke(nodeService.getSearchBackpressureService());
    }

    @Override
    public String getMetricsPath(long startTime, String... keysPath) {
        if (keysPath.length != KEYS_PATH_LENGTH) {
            throw new RuntimeException("keys length should be " + KEYS_PATH_LENGTH);
        }
        return PerformanceAnalyzerMetrics.generatePath(startTime, PATH_TO_STORE_METRICS);
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

        public void setMode(String mode) {
            this.mode = mode;
        }

        public SearchShardTaskStats getSearchShardTaskStats() {
            return searchShardTaskStats;
        }

        public void setSearchShardTaskStats(SearchShardTaskStats searchShardTaskStats) {
            this.searchShardTaskStats = searchShardTaskStats;
        }

        public SearchTaskStats getSearchTaskStats() {
            return searchTaskStats;
        }

        public void setSearchTaskStats(SearchTaskStats searchTaskStats) {
            this.searchTaskStats = searchTaskStats;
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

        // Getters and Setters
        public long getCancellationCount() {
            return cancellationCount;
        }

        public void setCancellationCount(long cancellationCount) {
            this.cancellationCount = cancellationCount;
        }

        public long getLimitReachedCount() {
            return limitReachedCount;
        }

        public void setLimitReachedCount(long limitReachedCount) {
            this.limitReachedCount = limitReachedCount;
        }

        public Map<String, ResourceUsageTrackerStats> getResourceUsageTrackerStats() {
            return resourceUsageTrackerStats;
        }

        public void setResourceUsageTrackerStats(
                Map<String, ResourceUsageTrackerStats> resourceUsageTrackerStats) {
            this.resourceUsageTrackerStats = resourceUsageTrackerStats;
        }
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

        // Getters and Setters
        public long getCancellationCount() {
            return cancellationCount;
        }

        public void setCancellationCount(long cancellationCount) {
            this.cancellationCount = cancellationCount;
        }

        public long getLimitReachedCount() {
            return limitReachedCount;
        }

        public void setLimitReachedCount(long limitReachedCount) {
            this.limitReachedCount = limitReachedCount;
        }

        public Map<String, ResourceUsageTrackerStats> getResourceUsageTrackerStats() {
            return resourceUsageTrackerStats;
        }

        public void setResourceUsageTrackerStats(
                Map<String, ResourceUsageTrackerStats> resourceUsageTrackerStats) {
            this.resourceUsageTrackerStats = resourceUsageTrackerStats;
        }
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
        // Getters and Setters
        public long getCancellationCount() {
            return cancellationCount;
        }

        public void setCancellationCount(long cancellationCount) {
            this.cancellationCount = cancellationCount;
        }

        public long getCurrentMax() {
            return currentMax;
        }

        public void setCurrentMax(long currentMax) {
            this.currentMax = currentMax;
        }

        public long getCurrentAvg() {
            return currentAvg;
        }

        public void setCurrentAvg(long currentAvg) {
            this.currentAvg = currentAvg;
        }

        public long getRollingAvg() {
            return rollingAvg;
        }

        public void setRollingAvg(long rollingAvg) {
            this.rollingAvg = rollingAvg;
        }

        public boolean isFragment() {
            return fragment;
        }

        public void setFragment(boolean fragment) {
            this.fragment = fragment;
        }
    }

    // Flatten the data fields for easier access
    public static class SearchBackPressureMetrics extends MetricStatus {
        // private double searchBackPressureStatsTest;
        private String mode;
        private String nodeId;

        // SearchShardTaskStats related stats (General)
        private long searchbp_shard_stats_cancellationCount;
        private long searchbp_shard_stats_limitReachedCount;

        // SearchShardTaskStats related stats (resourceUsageTrackerStats)
        // HEAP_USAGE_TRACKER
        private long searchbp_shard_stats_resource_heap_usage_cancellationCount;
        private long searchbp_shard_stats_resource_heap_usage_currentMax;
        private long searchbp_shard_stats_resource_heap_usage_rollingAvg;

        // CPU_USAGE_TRACKER
        private long searchbp_shard_stats_resource_cpu_usage_cancellationCount;
        private long searchbp_shard_stats_resource_cpu_usage_currentMax;
        private long searchbp_shard_stats_resource_cpu_usage_currentAvg;

        // ELAPSED_TIME_TRACKER
        private long searchbp_shard_stats_resource_elaspedtime_usage_cancellationCount;
        private long searchbp_shard_stats_resource_elaspedtime_usage_currentMax;
        private long searchbp_shard_stats_resource_elaspedtime_usage_currentAvg;

        // SearchTaskStats related stats (General)
        private long searchbp_task_stats_cancellationCount;
        private long searchbp_task_stats_limitReachedCount;

        // SearchTaskStats related stats (resourceUsageTrackerStats)
        // HEAP_USAGE_TRACKER
        private long searchbp_task_stats_resource_heap_usage_cancellationCount;
        private long searchbp_task_stats_resource_heap_usage_currentMax;
        private long searchbp_task_stats_resource_heap_usage_rollingAvg;

        // CPU_USAGE_TRACKER
        private long searchbp_task_stats_resource_cpu_usage_cancellationCount;
        private long searchbp_task_stats_resource_cpu_usage_currentMax;
        private long searchbp_task_stats_resource_cpu_usage_currentAvg;

        // ELAPSED_TIME_TRACKER
        private long searchbp_task_stats_resource_elaspedtime_usage_cancellationCount;
        private long searchbp_task_stats_resource_elaspedtime_usage_currentMax;
        private long searchbp_task_stats_resource_elaspedtime_usage_currentAvg;

        public SearchBackPressureMetrics(
                String mode,
                String nodeId,
                long searchbp_shard_stats_cancellationCount,
                long searchbp_shard_stats_limitReachedCount,
                long searchbp_shard_stats_resource_heap_usage_cancellationCount,
                long searchbp_shard_stats_resource_heap_usage_currentMax,
                long searchbp_shard_stats_resource_heap_usage_rollingAvg,
                long searchbp_shard_stats_resource_cpu_usage_cancellationCount,
                long searchbp_shard_stats_resource_cpu_usage_currentMax,
                long searchbp_shard_stats_resource_cpu_usage_currentAvg,
                long searchbp_shard_stats_resource_elaspedtime_usage_cancellationCount,
                long searchbp_shard_stats_resource_elaspedtime_usage_currentMax,
                long searchbp_shard_stats_resource_elaspedtime_usage_currentAvg,
                long searchbp_task_stats_cancellationCount,
                long searchbp_task_stats_limitReachedCount,
                long searchbp_task_stats_resource_heap_usage_cancellationCount,
                long searchbp_task_stats_resource_heap_usage_currentMax,
                long searchbp_task_stats_resource_heap_usage_rollingAvg,
                long searchbp_task_stats_resource_cpu_usage_cancellationCount,
                long searchbp_task_stats_resource_cpu_usage_currentMax,
                long searchbp_task_stats_resource_cpu_usage_currentAvg,
                long searchbp_task_stats_resource_elaspedtime_usage_cancellationCount,
                long searchbp_task_stats_resource_elaspedtime_usage_currentMax,
                long searchbp_task_stats_resource_elaspedtime_usage_currentAvg) {
            this.mode = mode;
            this.nodeId = nodeId;
            this.searchbp_shard_stats_cancellationCount = searchbp_shard_stats_cancellationCount;
            this.searchbp_shard_stats_limitReachedCount = searchbp_shard_stats_limitReachedCount;
            this.searchbp_shard_stats_resource_heap_usage_cancellationCount =
                    searchbp_shard_stats_resource_heap_usage_cancellationCount;
            this.searchbp_shard_stats_resource_heap_usage_currentMax =
                    searchbp_shard_stats_resource_heap_usage_currentMax;
            this.searchbp_shard_stats_resource_heap_usage_rollingAvg =
                    searchbp_shard_stats_resource_heap_usage_rollingAvg;
            this.searchbp_shard_stats_resource_cpu_usage_cancellationCount =
                    searchbp_shard_stats_resource_cpu_usage_cancellationCount;
            this.searchbp_shard_stats_resource_cpu_usage_currentMax =
                    searchbp_shard_stats_resource_cpu_usage_currentMax;
            this.searchbp_shard_stats_resource_cpu_usage_currentAvg =
                    searchbp_shard_stats_resource_cpu_usage_currentAvg;
            this.searchbp_shard_stats_resource_elaspedtime_usage_cancellationCount =
                    searchbp_shard_stats_resource_elaspedtime_usage_cancellationCount;
            this.searchbp_shard_stats_resource_elaspedtime_usage_currentMax =
                    searchbp_shard_stats_resource_elaspedtime_usage_currentMax;
            this.searchbp_shard_stats_resource_elaspedtime_usage_currentAvg =
                    searchbp_shard_stats_resource_elaspedtime_usage_currentAvg;
            this.searchbp_task_stats_cancellationCount = searchbp_task_stats_cancellationCount;
            this.searchbp_task_stats_limitReachedCount = searchbp_task_stats_limitReachedCount;
            this.searchbp_task_stats_resource_heap_usage_cancellationCount =
                    searchbp_task_stats_resource_heap_usage_cancellationCount;
            this.searchbp_task_stats_resource_heap_usage_currentMax =
                    searchbp_task_stats_resource_heap_usage_currentMax;
            this.searchbp_task_stats_resource_heap_usage_rollingAvg =
                    searchbp_task_stats_resource_heap_usage_rollingAvg;
            this.searchbp_task_stats_resource_cpu_usage_cancellationCount =
                    searchbp_task_stats_resource_cpu_usage_cancellationCount;
            this.searchbp_task_stats_resource_cpu_usage_currentMax =
                    searchbp_task_stats_resource_cpu_usage_currentMax;
            this.searchbp_task_stats_resource_cpu_usage_currentAvg =
                    searchbp_task_stats_resource_cpu_usage_currentAvg;
            this.searchbp_task_stats_resource_elaspedtime_usage_cancellationCount =
                    searchbp_task_stats_resource_elaspedtime_usage_cancellationCount;
            this.searchbp_task_stats_resource_elaspedtime_usage_currentMax =
                    searchbp_task_stats_resource_elaspedtime_usage_currentMax;
            this.searchbp_task_stats_resource_elaspedtime_usage_currentAvg =
                    searchbp_task_stats_resource_elaspedtime_usage_currentAvg;
        }

        @JsonProperty("searchbp_mode")
        public String getSearchBackPressureStats_Mode() {
            return this.mode;
        }

        @JsonProperty("searchbp_nodeid")
        public String getSearchBackPressureStats_NodeId() {
            return this.nodeId;
        }

        @JsonProperty("searchbp_shard_stats_cancellationCount")
        public long getSearchbp_shard_stats_cancellationCount() {
            return searchbp_shard_stats_cancellationCount;
        }

        @JsonProperty("searchbp_shard_stats_limitReachedCount")
        public long getSearchbp_shard_stats_limitReachedCount() {
            return searchbp_shard_stats_limitReachedCount;
        }

        @JsonProperty("searchbp_shard_stats_resource_heap_usage_cancellationCount")
        public long getSearchbp_shard_stats_resource_heap_usage_cancellationCount() {
            return searchbp_shard_stats_resource_heap_usage_cancellationCount;
        }

        @JsonProperty("searchbp_shard_stats_resource_heap_usage_currentMax")
        public long getSearchbp_shard_stats_resource_heap_usage_currentMax() {
            return searchbp_shard_stats_resource_heap_usage_currentMax;
        }

        @JsonProperty("searchbp_shard_stats_resource_heap_usage_rollingAvg")
        public long getsearchbp_shard_stats_resource_heap_usage_rollingAvg() {
            return searchbp_shard_stats_resource_heap_usage_rollingAvg;
        }

        @JsonProperty("searchbp_shard_stats_resource_cpu_usage_cancellationCount")
        public long getSearchbp_shard_stats_resource_cpu_usage_cancellationCount() {
            return searchbp_shard_stats_resource_cpu_usage_cancellationCount;
        }

        @JsonProperty("searchbp_shard_stats_resource_cpu_usage_currentMax")
        public long getSearchbp_shard_stats_resource_cpu_usage_currentMax() {
            return searchbp_shard_stats_resource_cpu_usage_currentMax;
        }

        @JsonProperty("searchbp_shard_stats_resource_cpu_usage_currentAvg")
        public long getSearchbp_shard_stats_resource_cpu_usage_currentAvg() {
            return searchbp_shard_stats_resource_cpu_usage_currentAvg;
        }

        @JsonProperty("searchbp_shard_stats_resource_elaspedtime_usage_cancellationCount")
        public long getSearchbp_shard_stats_resource_elaspedtime_usage_cancellationCount() {
            return searchbp_shard_stats_resource_elaspedtime_usage_cancellationCount;
        }

        @JsonProperty("searchbp_shard_stats_resource_elaspedtime_usage_currentMax")
        public long getSearchbp_shard_stats_resource_elaspedtime_usage_currentMax() {
            return searchbp_shard_stats_resource_elaspedtime_usage_currentMax;
        }

        @JsonProperty("searchbp_shard_stats_resource_elaspedtime_usage_currentAvg")
        public long getSearchbp_shard_stats_resource_elaspedtime_usage_currentAvg() {
            return searchbp_shard_stats_resource_elaspedtime_usage_currentAvg;
        }

        @JsonProperty("searchbp_task_stats_cancellationCount")
        public long getSearchbp_task_stats_cancellationCount() {
            return searchbp_task_stats_cancellationCount;
        }

        @JsonProperty("searchbp_task_stats_limitReachedCount")
        public long getSearchbp_task_stats_limitReachedCount() {
            return searchbp_task_stats_limitReachedCount;
        }

        @JsonProperty("searchbp_task_stats_resource_heap_usage_cancellationCount")
        public long getSearchbp_task_stats_resource_heap_usage_cancellationCount() {
            return searchbp_task_stats_resource_heap_usage_cancellationCount;
        }

        @JsonProperty("searchbp_task_stats_resource_heap_usage_currentMax")
        public long getSearchbp_task_stats_resource_heap_usage_currentMax() {
            return searchbp_task_stats_resource_heap_usage_currentMax;
        }

        @JsonProperty("searchbp_task_stats_resource_heap_usage_rollingAvg")
        public long getsearchbp_task_stats_resource_heap_usage_rollingAvg() {
            return searchbp_task_stats_resource_heap_usage_rollingAvg;
        }

        @JsonProperty("searchbp_task_stats_resource_cpu_usage_cancellationCount")
        public long getSearchbp_task_stats_resource_cpu_usage_cancellationCount() {
            return searchbp_task_stats_resource_cpu_usage_cancellationCount;
        }

        @JsonProperty("searchbp_task_stats_resource_cpu_usage_currentMax")
        public long getSearchbp_task_stats_resource_cpu_usage_currentMax() {
            return searchbp_task_stats_resource_cpu_usage_currentMax;
        }

        @JsonProperty("searchbp_task_stats_resource_cpu_usage_currentAvg")
        public long getSearchbp_task_stats_resource_cpu_usage_currentAvg() {
            return searchbp_task_stats_resource_cpu_usage_currentAvg;
        }

        @JsonProperty("searchbp_task_stats_resource_elaspedtime_usage_cancellationCount")
        public long getSearchbp_task_stats_resource_elaspedtime_usage_cancellationCount() {
            return searchbp_task_stats_resource_elaspedtime_usage_cancellationCount;
        }

        @JsonProperty("searchbp_task_stats_resource_elaspedtime_usage_currentMax")
        public long getSearchbp_task_stats_resource_elaspedtime_usage_currentMax() {
            return searchbp_task_stats_resource_elaspedtime_usage_currentMax;
        }

        @JsonProperty("searchbp_task_stats_resource_elaspedtime_usage_currentAvg")
        public long getSearchbp_task_stats_resource_elaspedtime_usage_currentAvg() {
            return searchbp_task_stats_resource_elaspedtime_usage_currentAvg;
        }
    }
}
