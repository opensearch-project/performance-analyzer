/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

/*
 * Copyright 2019-2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.opensearch.performanceanalyzer.decisionmaker.DecisionMakerConsts.CACHE_MAX_WEIGHT;
import static org.opensearch.performanceanalyzer.metrics.AllMetrics.CacheType.FIELD_DATA_CACHE;
import static org.opensearch.performanceanalyzer.metrics.AllMetrics.CacheType.SHARD_REQUEST_CACHE;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.security.AccessController;
import java.security.PrivilegedAction;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.opensearch.common.cache.Cache;
import org.opensearch.indices.IndicesService;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.CacheConfigDimension;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.CacheConfigValue;
import org.opensearch.performanceanalyzer.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.metrics.PerformanceAnalyzerMetrics;

/*
 * Unlike Cache Hit, Miss, Eviction Count and Size, which is tracked on a per shard basis,
 * the Cache Max size is a node-level static setting and thus, we need a custom collector
 * (other than NodeStatsMetricsCollector which collects the per shard metrics) for this
 * metric.
 *
 * CacheConfigMetricsCollector collects the max size for the Field Data and Shard Request
 * Cache currently and can be extended for remaining cache types and any other node level
 * cache metric.
 *
 */
public class CacheConfigMetricsCollector extends PerformanceAnalyzerMetricsCollector
        implements MetricsProcessor {
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(CacheConfigMetricsCollector.class).samplingInterval;
    private static final int KEYS_PATH_LENGTH = 0;
    private StringBuilder value;

    public CacheConfigMetricsCollector() {
        super(SAMPLING_TIME_INTERVAL, "CacheConfigMetrics");
        value = new StringBuilder();
    }

    @Override
    public void collectMetrics(long startTime) {
        IndicesService indicesService = OpenSearchResources.INSTANCE.getIndicesService();
        if (indicesService == null) {
            return;
        }

        value.setLength(0);
        value.append(PerformanceAnalyzerMetrics.getJsonCurrentMilliSeconds());
        // This is for backward compatibility. Core OpenSearch may or may not emit maxWeight metric.
        // (depending on whether the patch has been applied or not). Thus, we need to use
        // reflection to check whether getMaxWeight() method exist in Cache.java
        //
        // Currently, we are collecting maxWeight metrics only for FieldData and Shard Request
        // Cache.
        CacheMaxSizeStatus fieldDataCacheMaxSizeStatus =
                AccessController.doPrivileged(
                        (PrivilegedAction<CacheMaxSizeStatus>)
                                () -> {
                                    try {
                                        Cache fieldDataCache =
                                                indicesService
                                                        .getIndicesFieldDataCache()
                                                        .getCache();
                                        long fieldDataMaxSize =
                                                (Long)
                                                        FieldUtils.readField(
                                                                fieldDataCache,
                                                                CACHE_MAX_WEIGHT,
                                                                true);
                                        return new CacheMaxSizeStatus(
                                                FIELD_DATA_CACHE.toString(), fieldDataMaxSize);
                                    } catch (Exception e) {
                                        return new CacheMaxSizeStatus(
                                                FIELD_DATA_CACHE.toString(), null);
                                    }
                                });
        value.append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                .append(fieldDataCacheMaxSizeStatus.serialize());
        CacheMaxSizeStatus shardRequestCacheMaxSizeStatus =
                AccessController.doPrivileged(
                        (PrivilegedAction<CacheMaxSizeStatus>)
                                () -> {
                                    try {
                                        Object reqCache =
                                                FieldUtils.readField(
                                                        indicesService,
                                                        "indicesRequestCache",
                                                        true);
                                        Cache requestCache =
                                                (Cache)
                                                        FieldUtils.readField(
                                                                reqCache, "cache", true);
                                        Long requestCacheMaxSize =
                                                (Long)
                                                        FieldUtils.readField(
                                                                requestCache,
                                                                CACHE_MAX_WEIGHT,
                                                                true);
                                        return new CacheMaxSizeStatus(
                                                SHARD_REQUEST_CACHE.toString(),
                                                requestCacheMaxSize);
                                    } catch (Exception e) {
                                        return new CacheMaxSizeStatus(
                                                SHARD_REQUEST_CACHE.toString(), null);
                                    }
                                });
        value.append(PerformanceAnalyzerMetrics.sMetricNewLineDelimitor)
                .append(shardRequestCacheMaxSizeStatus.serialize());
        saveMetricValues(value.toString(), startTime);
    }

    @Override
    public String getMetricsPath(long startTime, String... keysPath) {
        // throw exception if keys.length is not equal to 0
        if (keysPath.length != KEYS_PATH_LENGTH) {
            throw new RuntimeException("keys length should be " + KEYS_PATH_LENGTH);
        }

        return PerformanceAnalyzerMetrics.generatePath(
                startTime, PerformanceAnalyzerMetrics.sCacheConfigPath);
    }

    static class CacheMaxSizeStatus extends MetricStatus {

        private final String cacheType;

        @JsonInclude(Include.NON_NULL)
        private final Long cacheMaxSize;

        CacheMaxSizeStatus(String cacheType, Long cacheMaxSize) {
            this.cacheType = cacheType;
            this.cacheMaxSize = cacheMaxSize;
        }

        @JsonProperty(CacheConfigDimension.Constants.TYPE_VALUE)
        public String getCacheType() {
            return cacheType;
        }

        @JsonProperty(CacheConfigValue.Constants.CACHE_MAX_SIZE_VALUE)
        public long getCacheMaxSize() {
            return cacheMaxSize;
        }
    }
}
