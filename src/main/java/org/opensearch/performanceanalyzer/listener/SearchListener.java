/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.listener;

import org.opensearch.search.internal.SearchContext;

interface SearchListener {
    default void preQueryPhase(SearchContext searchContext) {}

    default void queryPhase(SearchContext searchContext, long tookInNanos) {}

    default void failedQueryPhase(SearchContext searchContext) {}

    default void preFetchPhase(SearchContext searchContext) {}

    default void fetchPhase(SearchContext searchContext, long tookInNanos) {}

    default void failedFetchPhase(SearchContext searchContext) {}
}
