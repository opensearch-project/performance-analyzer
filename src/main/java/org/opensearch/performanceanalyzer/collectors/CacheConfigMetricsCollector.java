/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.CacheType.FIELD_DATA_CACHE;
import static org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.CacheType.SHARD_REQUEST_CACHE;
import static org.opensearch.performanceanalyzer.commons.stats.decisionmaker.DecisionMakerConsts.CACHE_MAX_WEIGHT;
import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode.CACHE_CONFIG_METRICS_COLLECTOR_ERROR;
import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatMetrics.CACHE_CONFIG_METRICS_COLLECTOR_EXECUTION_TIME;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.security.AccessController;
import java.security.PrivilegedAction;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.cache.Cache;
import org.opensearch.indices.IndicesService;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.collectors.MetricStatus;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.CacheConfigDimension;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.CacheConfigValue;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsProcessor;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;

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
    private static final Logger LOG = LogManager.getLogger(CacheConfigMetricsCollector.class);
    private static final int KEYS_PATH_LENGTH = 0;
    private StringBuilder value;

    public CacheConfigMetricsCollector() {
        super(
                SAMPLING_TIME_INTERVAL,
                "CacheConfigMetrics",
                CACHE_CONFIG_METRICS_COLLECTOR_EXECUTION_TIME,
                CACHE_CONFIG_METRICS_COLLECTOR_ERROR);
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
                                        Object openSearchOnHeapCache =
                                                FieldUtils.readField(reqCache, "cache", true);
                                        Cache requestCache =
                                                (Cache)
                                                        FieldUtils.readField(
                                                                openSearchOnHeapCache,
                                                                "cache",
                                                                true);
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
                                        LOG.error("Error while evaluating requestCacheMaxSize", e);
                                        return null;
                                    }
                                });

        if (shardRequestCacheMaxSizeStatus == null) {
            return;
        }

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
