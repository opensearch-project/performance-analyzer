/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchRequest;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.metrics.RTFMetrics;
import org.opensearch.performanceanalyzer.commons.util.Util;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.search.builder.SearchSourceBuilder;
import org.opensearch.telemetry.metrics.Counter;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;

/**
 * Collects per-request search metrics via the RTF telemetry pipeline. Currently tracks whether a
 * search request contains a script (Painless, script_score, scripted_metric, etc.).
 *
 * <p>Metric emitted: {@code script_search_count} with tag {@code has_script=true|false}
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
                            "Count of search requests tagged by whether they contain a script",
                            RTFMetrics.MetricUnits.COUNT.toString());
            metricsInitialized = true;
            System.out.println("PA_DEBUG: script_search_count counter initialized");
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
                System.out.println("PA_DEBUG: script_search_count emitted");
            }
        } catch (Exception e) {
            LOG.debug("Error emitting script_search_count metric", e);
        }
    }

    /**
     * Detects if a SearchRequest contains any scripts (Painless or otherwise). Checks for:
     * script_score, script queries, scripted_metric aggregations, script fields, script-based
     * sorting.
     */
    private boolean detectScript(SearchRequest searchRequest) {
        if (searchRequest == null || searchRequest.source() == null) {
            return false;
        }
        try {
            SearchSourceBuilder source = searchRequest.source();
            String sourceString = source.toString();
            return sourceString.contains("\"script\"")
                    || sourceString.contains("script_score")
                    || sourceString.contains("script_fields")
                    || sourceString.contains("scripted_metric")
                    || sourceString.contains("painless")
                    || sourceString.contains("expression")
                    || sourceString.contains("mustache");
        } catch (Exception e) {
            LOG.debug("Error detecting scripts in search request", e);
            return false;
        }
    }
}
