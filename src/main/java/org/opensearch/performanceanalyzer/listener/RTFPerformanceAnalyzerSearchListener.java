/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.listener;

import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode.OPENSEARCH_REQUEST_INTERCEPTOR_ERROR;
import static org.opensearch.performanceanalyzer.util.Utils.computeShareFactor;

import com.google.common.annotations.VisibleForTesting;
import java.util.HashMap;
import java.util.Map;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.search.BooleanClause;
import org.opensearch.core.action.NotifyOnceListener;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.index.query.QueryBuilderVisitor;
import org.opensearch.index.query.ScriptQueryBuilder;
import org.opensearch.index.query.functionscore.ScriptScoreQueryBuilder;
import org.opensearch.index.shard.SearchOperationListener;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.ShardMetricsCollector;
import org.opensearch.performanceanalyzer.commons.collectors.StatsCollector;
import org.opensearch.performanceanalyzer.commons.metrics.RTFMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.RTFMetrics.ShardOperationsValue;
import org.opensearch.performanceanalyzer.commons.util.Util;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.util.Utils;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.internal.SearchContext;
import org.opensearch.tasks.Task;
import org.opensearch.telemetry.metrics.Counter;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;

/**
 * {@link SearchOperationListener} to capture the resource utilization of a shard search operation.
 * This will be getting the resource tracking information from the {@link
 * org.opensearch.tasks.TaskResourceTrackingService}.
 */
public class RTFPerformanceAnalyzerSearchListener
        implements SearchOperationListener, SearchListener {

    private static final Logger LOG =
            LogManager.getLogger(RTFPerformanceAnalyzerSearchListener.class);
    private static final String SHARD_FETCH_PHASE = "shard_fetch";
    private static final String SHARD_QUERY_PHASE = "shard_query";
    public static final String QUERY_START_TIME = "query_start_time";
    public static final String FETCH_START_TIME = "fetch_start_time";
    public static final String QUERY_TASK_ID = "query_task_id";
    private final ThreadLocal<Map<String, Long>> threadLocal;
    private static final SearchListener NO_OP_SEARCH_LISTENER = new NoOpSearchListener();

    private final PerformanceAnalyzerController controller;
    private final Histogram cpuUtilizationHistogram;
    private final Histogram heapUsedHistogram;
    private final Histogram searchLatencyHistogram;
    private final Counter shardQueryWithScriptCounter;
    private final int numProcessors;

    public static final String SHARD_QUERY_WITH_SCRIPT_COUNT = "shard_query_with_script_count";

    public RTFPerformanceAnalyzerSearchListener(final PerformanceAnalyzerController controller) {
        this.controller = controller;
        this.cpuUtilizationHistogram =
                createCPUUtilizationHistogram(OpenSearchResources.INSTANCE.getMetricsRegistry());
        this.heapUsedHistogram =
                createHeapUsedHistogram(OpenSearchResources.INSTANCE.getMetricsRegistry());
        this.searchLatencyHistogram =
                createSearchLatencyHistogram(OpenSearchResources.INSTANCE.getMetricsRegistry());
        this.shardQueryWithScriptCounter =
                createShardQueryWithScriptCounter(
                        OpenSearchResources.INSTANCE.getMetricsRegistry());
        this.threadLocal = ThreadLocal.withInitial(HashMap::new);
        this.numProcessors = Runtime.getRuntime().availableProcessors();
    }

    private Histogram createCPUUtilizationHistogram(MetricsRegistry metricsRegistry) {
        if (metricsRegistry != null) {
            return metricsRegistry.createHistogram(
                    RTFMetrics.OSMetrics.CPU_UTILIZATION.toString(),
                    "CPU Utilization per shard for a search phase",
                    RTFMetrics.MetricUnits.RATE.toString());
        } else {
            LOG.debug("MetricsRegistry is null");
            return null;
        }
    }

    private Histogram createHeapUsedHistogram(MetricsRegistry metricsRegistry) {
        if (metricsRegistry != null) {
            return metricsRegistry.createHistogram(
                    RTFMetrics.OSMetrics.HEAP_ALLOCATED.toString(),
                    "Heap used per shard for a search phase",
                    RTFMetrics.MetricUnits.BYTE.toString());
        } else {
            LOG.debug("MetricsRegistry is null");
            return null;
        }
    }

    // This histogram will help to get the total latency for search request using getMax over an
    // interval.
    private Histogram createSearchLatencyHistogram(MetricsRegistry metricsRegistry) {
        if (metricsRegistry != null) {
            return metricsRegistry.createHistogram(
                    ShardOperationsValue.SHARD_SEARCH_LATENCY.toString(),
                    "Search latency per shard per phase",
                    RTFMetrics.MetricUnits.MILLISECOND.toString());
        } else {
            LOG.debug("MetricsRegistry is null");
            return null;
        }
    }

    private Counter createShardQueryWithScriptCounter(MetricsRegistry metricsRegistry) {
        if (metricsRegistry != null) {
            return metricsRegistry.createCounter(
                    SHARD_QUERY_WITH_SCRIPT_COUNT,
                    "Count of shard query phases where a script was used",
                    RTFMetrics.MetricUnits.COUNT.toString());
        } else {
            LOG.debug("MetricsRegistry is null");
            return null;
        }
    }

    @Override
    public String toString() {
        return RTFPerformanceAnalyzerSearchListener.class.getSimpleName();
    }

    @VisibleForTesting
    SearchListener getSearchListener() {
        return isSearchListenerEnabled() ? this : NO_OP_SEARCH_LISTENER;
    }

    private boolean isSearchListenerEnabled() {
        return OpenSearchResources.INSTANCE.getMetricsRegistry() != null
                && controller.isPerformanceAnalyzerEnabled()
                && (controller.getCollectorsRunModeValue() == Util.CollectorMode.DUAL.getValue()
                        || controller.getCollectorsRunModeValue()
                                == Util.CollectorMode.TELEMETRY.getValue());
    }

    @Override
    public void onPreQueryPhase(SearchContext searchContext) {
        try {
            getSearchListener().preQueryPhase(searchContext);
        } catch (Exception ex) {
            LOG.error(ex);
            StatsCollector.instance().logException(OPENSEARCH_REQUEST_INTERCEPTOR_ERROR);
        }
    }

    @Override
    public void onQueryPhase(SearchContext searchContext, long tookInNanos) {
        try {
            getSearchListener().queryPhase(searchContext, tookInNanos);
        } catch (Exception ex) {
            LOG.error(ex);
            StatsCollector.instance().logException(OPENSEARCH_REQUEST_INTERCEPTOR_ERROR);
        }
    }

    @Override
    public void onFailedQueryPhase(SearchContext searchContext) {
        try {
            getSearchListener().failedQueryPhase(searchContext);
        } catch (Exception ex) {
            LOG.error(ex);
            StatsCollector.instance().logException(OPENSEARCH_REQUEST_INTERCEPTOR_ERROR);
        }
    }

    @Override
    public void onPreFetchPhase(SearchContext searchContext) {
        try {
            getSearchListener().preFetchPhase(searchContext);
        } catch (Exception ex) {
            LOG.error(ex);
            StatsCollector.instance().logException(OPENSEARCH_REQUEST_INTERCEPTOR_ERROR);
        }
    }

    @Override
    public void onFetchPhase(SearchContext searchContext, long tookInNanos) {
        try {
            getSearchListener().fetchPhase(searchContext, tookInNanos);
        } catch (Exception ex) {
            LOG.error(ex);
            StatsCollector.instance().logException(OPENSEARCH_REQUEST_INTERCEPTOR_ERROR);
        }
    }

    @Override
    public void onFailedFetchPhase(SearchContext searchContext) {
        try {
            getSearchListener().failedFetchPhase(searchContext);
        } catch (Exception ex) {
            LOG.error(ex);
            StatsCollector.instance().logException(OPENSEARCH_REQUEST_INTERCEPTOR_ERROR);
        }
    }

    @Override
    public void preQueryPhase(SearchContext searchContext) {
        threadLocal.get().put(QUERY_START_TIME, System.nanoTime());
        threadLocal.get().put(QUERY_TASK_ID, searchContext.getTask().getId());
    }

    @Override
    public void queryPhase(SearchContext searchContext, long tookInNanos) {
        long queryStartTime = threadLocal.get().getOrDefault(QUERY_START_TIME, System.nanoTime());
        double queryTimeInMills = tookInNanos / 1_000_000.0;

        searchLatencyHistogram.record(
                queryTimeInMills, createTags(searchContext, SHARD_QUERY_PHASE, false));

        // Emit counter if query contains a script
        emitQueryWithScriptMetric(searchContext);

        addResourceTrackingCompletionListener(
                searchContext, queryStartTime, tookInNanos, SHARD_QUERY_PHASE, false);
    }

    @Override
    public void failedQueryPhase(SearchContext searchContext) {
        long queryStartTime = threadLocal.get().getOrDefault(QUERY_START_TIME, System.nanoTime());
        long queryTime = (System.nanoTime() - queryStartTime);
        addResourceTrackingCompletionListener(
                searchContext, queryStartTime, queryTime, SHARD_QUERY_PHASE, true);
    }

    @Override
    public void preFetchPhase(SearchContext searchContext) {
        threadLocal.get().put(FETCH_START_TIME, System.nanoTime());
    }

    @Override
    public void fetchPhase(SearchContext searchContext, long tookInNanos) {
        long fetchStartTime = threadLocal.get().getOrDefault(FETCH_START_TIME, System.nanoTime());
        double fetchTimeInMills = tookInNanos / 1_000_000.0;
        searchLatencyHistogram.record(
                fetchTimeInMills, createTags(searchContext, SHARD_FETCH_PHASE, false));

        addResourceTrackingCompletionListenerForFetchPhase(
                searchContext, fetchStartTime, tookInNanos, SHARD_FETCH_PHASE, false);
    }

    @Override
    public void failedFetchPhase(SearchContext searchContext) {
        long fetchStartTime = threadLocal.get().getOrDefault(FETCH_START_TIME, System.nanoTime());
        long fetchTime = (System.nanoTime() - fetchStartTime);
        addResourceTrackingCompletionListenerForFetchPhase(
                searchContext, fetchStartTime, fetchTime, SHARD_FETCH_PHASE, true);
    }

    /**
     * Emits the shard_query_with_script_count counter if the query contains a script. Uses
     * QueryBuilderVisitor to recursively walk the query tree and detect ScriptQueryBuilder or
     * ScriptScoreQueryBuilder. Also checks for script_fields via hasScriptFields().
     */
    private void emitQueryWithScriptMetric(SearchContext searchContext) {
        if (shardQueryWithScriptCounter == null) {
            return;
        }
        try {
            // Check script_fields
            if (searchContext.hasScriptFields()) {
                shardQueryWithScriptCounter.add(1, createTags(searchContext));
                return;
            }

            // Walk the query tree for script queries
            SearchSourceBuilder source = searchContext.request().source();
            if (source != null && source.query() != null) {
                ScriptQueryVisitor visitor = new ScriptQueryVisitor();
                source.query().visit(visitor);
                if (visitor.hasScript()) {
                    shardQueryWithScriptCounter.add(1, createTags(searchContext));
                }
            }
        } catch (Exception e) {
            LOG.debug("Error emitting shard_query_with_script_count metric", e);
        }
    }

    /**
     * Visitor that recursively walks the query tree to detect script-based queries. Detects
     * ScriptQueryBuilder and ScriptScoreQueryBuilder at any nesting level.
     */
    private static class ScriptQueryVisitor implements QueryBuilderVisitor {
        private boolean scriptDetected = false;

        @Override
        public void accept(QueryBuilder qb) {
            if (scriptDetected) {
                return;
            }
            if (qb instanceof ScriptQueryBuilder || qb instanceof ScriptScoreQueryBuilder) {
                scriptDetected = true;
            }
        }

        @Override
        public QueryBuilderVisitor getChildVisitor(BooleanClause.Occur occur) {
            if (scriptDetected) {
                return QueryBuilderVisitor.NO_OP_VISITOR;
            }
            return this;
        }

        public boolean hasScript() {
            return scriptDetected;
        }
    }

    private void addResourceTrackingCompletionListener(
            SearchContext searchContext,
            long startTime,
            long queryTime,
            String phase,
            boolean isFailed) {
        addCompletionListener(searchContext, startTime, queryTime, phase, isFailed);
    }

    private void addResourceTrackingCompletionListenerForFetchPhase(
            SearchContext searchContext,
            long fetchStartTime,
            long fetchTime,
            String phase,
            boolean isFailed) {
        long startTime = fetchStartTime;
        long queryTaskId = threadLocal.get().getOrDefault(QUERY_TASK_ID, -1l);
        /**
         * There are scenarios where both query and fetch phases run in the same task for an
         * optimization. Adding a special handling for that case to divide the CPU usage between
         * these 2 operations by their runTime.
         */
        if (queryTaskId == searchContext.getTask().getId()) {
            startTime = threadLocal.get().getOrDefault(QUERY_START_TIME, System.nanoTime());
        }
        addCompletionListener(searchContext, startTime, fetchTime, phase, isFailed);
    }

    private void addCompletionListener(
            SearchContext searchContext,
            long startTime,
            long phaseTookTime,
            String phase,
            boolean isFailed) {
        searchContext
                .getTask()
                .addResourceTrackingCompletionListener(
                        createListener(searchContext, startTime, phaseTookTime, phase, isFailed));
    }

    @VisibleForTesting
    NotifyOnceListener<Task> createListener(
            SearchContext searchContext,
            long startTime,
            long phaseTookTime,
            String phase,
            boolean isFailed) {
        return new NotifyOnceListener<Task>() {
            @Override
            protected void innerOnResponse(Task task) {
                LOG.debug("Updating the counter for task {}", task.getId());
                /**
                 * There are scenarios where cpuUsageTime consists of the total of CPU of multiple
                 * phases. In that case we are computing the cpuShareFactor by dividing the
                 * particular phaseTime and the total time till this calculation happen from the
                 * overall start time.
                 */
                long totalTime = System.nanoTime() - task.getStartTimeNanos();
                double shareFactor = computeShareFactor(phaseTookTime, totalTime);
                LOG.debug(
                        "Total task time {} ns. Total Operation Listener time {} ns. "
                                + "Phase took time {} ns. Share factor {} ",
                        totalTime,
                        System.nanoTime() - startTime,
                        phaseTookTime,
                        shareFactor);
                double cpuUtilization =
                        Utils.calculateCPUUtilization(
                                numProcessors,
                                totalTime,
                                task.getTotalResourceStats().getCpuTimeInNanos(),
                                shareFactor);
                cpuUtilizationHistogram.record(
                        cpuUtilization, createTags(searchContext, phase, isFailed));
                ShardMetricsCollector.INSTANCE.recordCpuUtilization(
                        cpuUtilization, createTags(searchContext));
                double heapUsed =
                        Math.max(0, task.getTotalResourceStats().getMemoryInBytes() * shareFactor);
                heapUsedHistogram.record(heapUsed, createTags(searchContext, phase, isFailed));
                ShardMetricsCollector.INSTANCE.recordHeapUsed(heapUsed, createTags(searchContext));
            }

            @Override
            protected void innerOnFailure(Exception e) {
                LOG.error("Error is executing the the listener", e);
            }
        };
    }

    private Tags createTags(SearchContext searchContext, String phase, boolean isFailed) {
        Tags tags =
                Tags.create()
                        .addTag(
                                RTFMetrics.CommonDimension.INDEX_NAME.toString(),
                                searchContext.request().shardId().getIndex().getName())
                        .addTag(
                                RTFMetrics.CommonDimension.INDEX_UUID.toString(),
                                searchContext.request().shardId().getIndex().getUUID())
                        .addTag(
                                RTFMetrics.CommonDimension.SHARD_ID.toString(),
                                searchContext.request().shardId().getId());

        // Only add phase tag if phase is not null
        if (phase != null && !phase.isEmpty()) {
            tags.addTag(RTFMetrics.CommonDimension.OPERATION.toString(), phase)
                    .addTag(RTFMetrics.CommonDimension.FAILED.toString(), isFailed);
        }
        return tags;
    }

    private Tags createTags(SearchContext searchContext) {
        return createTags(searchContext, null, false);
    }
}
