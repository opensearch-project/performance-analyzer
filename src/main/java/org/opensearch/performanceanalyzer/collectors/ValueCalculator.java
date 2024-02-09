/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import org.opensearch.action.admin.indices.stats.ShardStats;

@FunctionalInterface
interface ValueCalculator {
    long calculateValue(ShardStats shardStats);
}
