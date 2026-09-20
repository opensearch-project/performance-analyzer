/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.index.query.ScriptQueryBuilder;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.metrics.RTFMetrics;
import org.opensearch.performanceanalyzer.commons.util.Util;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.search.aggregations.AggregationBuilder;
import org.opensearch.search.aggregations.metrics.ScriptedMetricAggregationBuilder;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.search.sort.ScriptSortBuilder;
import org.opensearch.telemetry.metrics.Counter;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;

/**
 * Collects per-request search metrics via the RTF telemetry pipeline. Currently tracks whether a
 * search request contains a script (Painless, script_score, scripted_metric, etc.).
 *
 * <p>Metric emitted: {@code script_search_count} — incremented only when a script is detected.
 */
public class RTFSearchRequestMetricsCollector {

    private static final Logger LOG = LogManager.getLogger(RTFSearchRequestMetricsCollector.class);

    public static final String SCRIPT_SEARCH_COUNT = "script_search_count";

    private final PerformanceAnalyzerController controller;
    private Counter scriptSearchCounter;
    private boolean metricsInitialized;

    public RTFSearchRequestMetricsCollector(final PerformanceAnalyzerController controller) {
        this.controller = controller;
        this.metricsInitialized = false;
    }

    private void initializeMetricsIfNeeded(MetricsRegistry metricsRegistry) {
        if (!metricsInitialized && metricsRegistry != null) {
            scriptSearchCounter =
                    metricsRegistry.createCounter(
                            SCRIPT_SEARCH_COUNT,
                            "Count of search requests that contain a script",
                            RTFMetrics.MetricUnits.COUNT.toString());
            metricsInitialized = true;
        }
    }

    private boolean isEnabled() {
        return controller.isPerformanceAnalyzerEnabled()
                && (controller.getCollectorsRunModeValue() == Util.CollectorMode.DUAL.getValue()
                        || controller.getCollectorsRunModeValue()
                                == Util.CollectorMode.TELEMETRY.getValue());
    }

    /**
     * Called on every incoming SearchRequest. Detects script usage and emits the counter via the
     * RTF telemetry pipeline.
     */
    public void onSearchRequest(SearchRequest searchRequest) {
        if (!isEnabled()) {
            return;
        }
        MetricsRegistry metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        initializeMetricsIfNeeded(metricsRegistry);
        if (scriptSearchCounter == null) {
            LOG.debug("script_search_count counter not yet initialized, skipping");
            return;
        }
        try {
            if (detectScript(searchRequest)) {
                scriptSearchCounter.add(1, Tags.create());
            }
        } catch (Exception e) {
            LOG.debug("Error emitting script_search_count metric", e);
        }
    }

    /**
     * Detects common script patterns via direct type checks — no string serialization.
     *
     * <p>Detects:
     * - Top-level script query: {"query": {"script": {...}}}
     * - Script fields: {"script_fields": {...}}
     * - Script sort: {"sort": {"_script": {...}}}
     * - Scripted metric aggregation: {"aggs": {"x": {"scripted_metric": {...}}}}
     *
     * <p>Does NOT detect:
     * - Scripts nested inside bool/dis_max/nested queries
     * - Function score with script_score function
     * - Bucket script / bucket selector pipeline aggregations
     * - Scripts inside sub-aggregations
     */
    private boolean detectScript(SearchRequest searchRequest) {
        if (searchRequest == null || searchRequest.source() == null) {
            return false;
        }
        try {
            SearchSourceBuilder source = searchRequest.source();

            // Check top-level query type
            if (source.query() instanceof ScriptQueryBuilder) {
                return true;
            }

            // Check script fields
            if (source.scriptFields() != null && !source.scriptFields().isEmpty()) {
                return true;
            }

            // Check script sort
            if (source.sorts() != null) {
                for (var sort : source.sorts()) {
                    if (sort instanceof ScriptSortBuilder) {
                        return true;
                    }
                }
            }

            // Check top-level aggregations for scripted_metric
            if (source.aggregations() != null) {
                for (AggregationBuilder agg : source.aggregations().getAggregatorFactories()) {
                    if (agg instanceof ScriptedMetricAggregationBuilder) {
                        return true;
                    }
                }
            }

            return false;
        } catch (Exception e) {
            LOG.debug("Error detecting scripts in search request", e);
            return false;
        }
    }
}
