/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import static org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.CacheType.FIELD_DATA_CACHE;
import static org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.CacheType.SHARD_REQUEST_CACHE;
import static org.opensearch.performanceanalyzer.commons.stats.decisionmaker.DecisionMakerConsts.CACHE_MAX_WEIGHT;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Closeable;
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
import org.opensearch.performanceanalyzer.commons.collectors.TelemetryCollector;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.RTFMetrics;
import org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode;
import org.opensearch.performanceanalyzer.commons.stats.metrics.StatMetrics;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;

public class RTFCacheConfigMetricsCollector extends PerformanceAnalyzerMetricsCollector
        implements TelemetryCollector {
    private static final Logger LOG = LogManager.getLogger(RTFCacheConfigMetricsCollector.class);
    private PerformanceAnalyzerController performanceAnalyzerController;
    private ConfigOverridesWrapper configOverridesWrapper;
    private Closeable fieldDataCacheGauge;
    private Closeable requestCacheGauge;
    private boolean metricsInitialised;

    public RTFCacheConfigMetricsCollector(
            PerformanceAnalyzerController performanceAnalyzerController,
            ConfigOverridesWrapper configOverridesWrapper) {
        super(
                MetricsConfiguration.CONFIG_MAP.get(RTFCacheConfigMetricsCollector.class)
                        .samplingInterval,
                "RTFCacheConfigMetricsCollector",
                StatMetrics.RTF_CACHE_CONFIG_METRICS_COLLECTOR_EXECUTION_TIME,
                StatExceptionCode.RTF_CACHE_CONFIG_METRICS_COLLECTOR_ERROR);
        this.performanceAnalyzerController = performanceAnalyzerController;
        this.configOverridesWrapper = configOverridesWrapper;
    }

    @Override
    public void collectMetrics(long l) {
        if (performanceAnalyzerController.isCollectorDisabled(
                configOverridesWrapper, getCollectorName())) {
            closeOpenGaugeObservablesIfAny();
            LOG.info("RTFCacheConfigMetricsCollector is disabled. Skipping collection.");
            return;
        }
        MetricsRegistry metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        if (metricsRegistry == null) {
            LOG.error("could not get the instance of MetricsRegistry class");
            return;
        }

        IndicesService indicesService = OpenSearchResources.INSTANCE.getIndicesService();
        if (indicesService == null) {
            LOG.error("could not get the instance of indicesService class");
            return;
        }
        LOG.debug("Executing collect metrics for RTFCacheConfigMetricsCollector");
        initialiseMetricsIfNeeded(metricsRegistry, indicesService);
    }

    private void initialiseMetricsIfNeeded(
            MetricsRegistry metricsRegistry, IndicesService indicesService) {
        if (!metricsInitialised) {
            fieldDataCacheGauge =
                    metricsRegistry.createGauge(
                            RTFMetrics.CacheConfigValue.Constants.CACHE_MAX_SIZE_VALUE,
                            "Cache Max Size metrics",
                            RTFMetrics.MetricUnits.BYTE.toString(),
                            () -> getFieldCacheMaxSizeStatus(indicesService),
                            Tags.create()
                                    .addTag(
                                            RTFMetrics.CacheConfigDimension.Constants.TYPE_VALUE,
                                            FIELD_DATA_CACHE.toString()));
            requestCacheGauge =
                    metricsRegistry.createGauge(
                            RTFMetrics.CacheConfigValue.Constants.CACHE_MAX_SIZE_VALUE,
                            "Cache Max Size metrics",
                            RTFMetrics.MetricUnits.BYTE.toString(),
                            () -> getRequestCacheMaxSizeStatus(indicesService),
                            Tags.create()
                                    .addTag(
                                            RTFMetrics.CacheConfigDimension.Constants.TYPE_VALUE,
                                            SHARD_REQUEST_CACHE.toString()));
            metricsInitialised = true;
        }
    }

    private void closeOpenGaugeObservablesIfAny() {
        if (fieldDataCacheGauge != null) {
            try {
                fieldDataCacheGauge.close();
            } catch (Exception e) {
                LOG.error("Unable to close the fieldDataCacheGauge observable");
            } finally {
                fieldDataCacheGauge = null;
            }
        }
        if (requestCacheGauge != null) {
            try {
                requestCacheGauge.close();
            } catch (Exception e) {
                LOG.error("Unable to close the fieldDataCacheGauge observable");
            } finally {
                requestCacheGauge = null;
            }
        }
        if (metricsInitialised) {
            metricsInitialised = false;
        }
    }

    private double getRequestCacheMaxSizeStatus(IndicesService indicesService) {
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
                                        LOG.debug(
                                                "Error occurred while fetching shardRequestCacheMaxSizeStatus: "
                                                        + e.getMessage());
                                        return null;
                                    }
                                });
        if (shardRequestCacheMaxSizeStatus != null
                && shardRequestCacheMaxSizeStatus.getCacheMaxSize() > 0) {
            return shardRequestCacheMaxSizeStatus.getCacheMaxSize();
        } else {
            return 0.0;
        }
    }

    private static double getFieldCacheMaxSizeStatus(IndicesService indicesService) {
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
                                        LOG.debug(
                                                "Error occurred while fetching fieldDataCacheMaxSizeStatus: "
                                                        + e.getMessage());
                                        return null;
                                    }
                                });
        if (fieldDataCacheMaxSizeStatus != null
                && fieldDataCacheMaxSizeStatus.getCacheMaxSize() > 0) {
            return fieldDataCacheMaxSizeStatus.getCacheMaxSize();
        } else {
            return 0.0;
        }
    }

    static class CacheMaxSizeStatus extends MetricStatus {

        private final String cacheType;

        @JsonInclude(JsonInclude.Include.NON_NULL)
        private final Long cacheMaxSize;

        CacheMaxSizeStatus(String cacheType, Long cacheMaxSize) {
            this.cacheType = cacheType;
            this.cacheMaxSize = cacheMaxSize;
        }

        @JsonProperty(AllMetrics.CacheConfigDimension.Constants.TYPE_VALUE)
        public String getCacheType() {
            return cacheType;
        }

        @JsonProperty(AllMetrics.CacheConfigValue.Constants.CACHE_MAX_SIZE_VALUE)
        public long getCacheMaxSize() {
            return cacheMaxSize;
        }
    }
}
