/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.action.search.SearchResponse;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.metrics.RTFMetrics;
import org.opensearch.performanceanalyzer.commons.util.Util;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;

/**
 * Collects per-request search metrics via the RTF telemetry pipeline.
 *
 * <p>Metrics emitted:
 *
 * <ul>
 *   <li>{@code search_doc_count} — histogram of documents returned per search response.
 * </ul>
 */
public class RTFSearchRequestMetricsCollector {

    private static final Logger LOG = LogManager.getLogger(RTFSearchRequestMetricsCollector.class);

    public static final String SEARCH_DOC_COUNT = "search_doc_count";

    private final PerformanceAnalyzerController controller;
    private Histogram searchDocCountHistogram;
    private boolean metricsInitialized;

    public RTFSearchRequestMetricsCollector(final PerformanceAnalyzerController controller) {
        this.controller = controller;
        this.metricsInitialized = false;
    }

    private void initializeMetricsIfNeeded(MetricsRegistry metricsRegistry) {
        if (!metricsInitialized && metricsRegistry != null) {
            searchDocCountHistogram =
                    metricsRegistry.createHistogram(
                            SEARCH_DOC_COUNT,
                            "Number of documents returned per search response",
                            RTFMetrics.MetricUnits.COUNT.toString());
            metricsInitialized = true;
        }
    }

    public boolean isEnabled() {
        return controller.isPerformanceAnalyzerEnabled()
                && (controller.getCollectorsRunModeValue() == Util.CollectorMode.DUAL.getValue()
                        || controller.getCollectorsRunModeValue()
                                == Util.CollectorMode.TELEMETRY.getValue());
    }

    /**
     * Called on every SearchResponse. Records the number of documents returned via the RTF
     * telemetry pipeline.
     */
    public void onSearchResponse(SearchResponse searchResponse) {
        if (!isEnabled()) {
            return;
        }
        MetricsRegistry metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        initializeMetricsIfNeeded(metricsRegistry);
        if (searchDocCountHistogram == null) {
            LOG.debug("search_doc_count histogram not yet initialized, skipping");
            return;
        }
        try {
            int docCount = searchResponse.getHits().getHits().length;
            searchDocCountHistogram.record(docCount, Tags.create());
        } catch (Exception e) {
            LOG.debug("Error emitting search_doc_count metric", e);
        }
    }
}
