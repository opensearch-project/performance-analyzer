/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors.telemetry;

import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode.THREADPOOL_METRICS_COLLECTOR_ERROR;
import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatMetrics.THREADPOOL_METRICS_COLLECTOR_EXECUTION_TIME;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.common.util.concurrent.SizeBlockingQueue;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.collectors.StatsCollector;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;
import org.opensearch.threadpool.ThreadPool;
import org.opensearch.threadpool.ThreadPoolStats;

public class RTFThreadPoolMetricsCollector extends PerformanceAnalyzerMetricsCollector {

    private static final Logger LOG = LogManager.getLogger(RTFThreadPoolMetricsCollector.class);
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(RTFThreadPoolMetricsCollector.class)
                    .samplingInterval;
    private final Map<String, ThreadPoolStatsRecord> statsRecordMap;
    private Histogram threadPoolQueueSizeMetrics;
    private Histogram threadPoolRejectedReqsMetrics;
    private Histogram threadPoolTotalThreadsMetrics;
    private Histogram threadPoolActiveThreadsMetrics;

    //  Skipping ThreadPoolQueueLatencyMetrics since they are always emitting -1 in the original
    // collector
    //  private Histogram ThreadPoolQueueLatencyMetrics;
    private Histogram ThreadPoolQueueCapacityMetrics;
    private MetricsRegistry metricsRegistry;
    private boolean metricsInitialised;
    private PerformanceAnalyzerController performanceAnalyzerController;
    private ConfigOverridesWrapper configOverridesWrapper;

    public RTFThreadPoolMetricsCollector(
            PerformanceAnalyzerController performanceAnalyzerController,
            ConfigOverridesWrapper configOverridesWrapper) {
        super(
                SAMPLING_TIME_INTERVAL,
                "RTFThreadPoolMetricsCollector",
                THREADPOOL_METRICS_COLLECTOR_EXECUTION_TIME,
                THREADPOOL_METRICS_COLLECTOR_ERROR);
        statsRecordMap = new HashMap<>();
        this.metricsInitialised = false;
        this.performanceAnalyzerController = performanceAnalyzerController;
        this.configOverridesWrapper = configOverridesWrapper;
    }

    @Override
    public void collectMetrics(long startTime) {
        if (!performanceAnalyzerController.telemetryCollectorsEnabled()) {
            LOG.info("All Telemetry collectors are disabled. Skipping collection.");
            return;
        }

        if (performanceAnalyzerController.isCollectorDisabled(
                configOverridesWrapper, getCollectorName())) {
            LOG.info(getCollectorName() + " is disabled. Skipping collection.");
            return;
        }

        if (OpenSearchResources.INSTANCE.getThreadPool() == null) {
            return;
        }

        metricsRegistry = OpenSearchResources.INSTANCE.getMetricsRegistry();
        if (metricsRegistry == null) {
            LOG.error("could not get the instance of MetricsRegistry class");
            return;
        }

        LOG.debug("Executing collect metrics for RTFThreadPoolMetricsCollector");

        initialiseMetricsIfNeeded();

        Iterator<ThreadPoolStats.Stats> statsIterator =
                OpenSearchResources.INSTANCE.getThreadPool().stats().iterator();

        while (statsIterator.hasNext()) {
            ThreadPoolStats.Stats stats = statsIterator.next();
            long rejectionDelta = 0;
            String threadPoolName = stats.getName();
            if (statsRecordMap.containsKey(threadPoolName)) {
                ThreadPoolStatsRecord lastRecord = statsRecordMap.get(threadPoolName);
                // if the timestamp in previous record is greater than 15s (3 * intervals),
                // then the scheduler might hang or freeze due to long GC etc. We simply drop
                // previous record here and set rejectionDelta to 0.
                if (startTime - lastRecord.getTimestamp() <= SAMPLING_TIME_INTERVAL * 3L) {
                    rejectionDelta = stats.getRejected() - lastRecord.getRejected();
                    // we might not run into this as rejection is a LongAdder which never decrement
                    // its count.
                    // regardless, let's set it to 0 to be safe.
                    if (rejectionDelta < 0) {
                        rejectionDelta = 0;
                    }
                }
            }
            statsRecordMap.put(
                    threadPoolName, new ThreadPoolStatsRecord(startTime, stats.getRejected()));
            final long finalRejectionDelta = rejectionDelta;
            final int capacity =
                    AccessController.doPrivileged(
                            (PrivilegedAction<Integer>)
                                    () -> {
                                        try {
                                            ThreadPool threadPool =
                                                    (ThreadPool)
                                                            FieldUtils.readField(
                                                                    OpenSearchResources.INSTANCE
                                                                            .getIndicesService(),
                                                                    "threadPool",
                                                                    true);
                                            ThreadPoolExecutor threadPoolExecutor =
                                                    (ThreadPoolExecutor)
                                                            threadPool.executor(threadPoolName);
                                            Object queue = threadPoolExecutor.getQueue();
                                            // TODO: we might want to read the capacity of
                                            // SifiResizableBlockingQueue in the future.
                                            // In order to do that we can create a new
                                            // PerformanceAnalyzerLibrary package and push
                                            // all the code which depends on core OpenSearch
                                            // specific
                                            // changes into that library.
                                            if (queue instanceof SizeBlockingQueue) {
                                                return ((SizeBlockingQueue) queue).capacity();
                                            }
                                        } catch (Exception e) {
                                            LOG.warn("Fail to read queue capacity via reflection");
                                            StatsCollector.instance()
                                                    .logException(
                                                            THREADPOOL_METRICS_COLLECTOR_ERROR);
                                        }
                                        return -1;
                                    });

            recordMetrics(stats, finalRejectionDelta, capacity);
        }
    }

    private void recordMetrics(
            ThreadPoolStats.Stats stats, long finalRejectionDelta, int capacity) {
        Tags threadPoolTypeTag = Tags.create().addTag("thread_pool_type", stats.getName());

        threadPoolQueueSizeMetrics.record(stats.getQueue(), threadPoolTypeTag);
        threadPoolRejectedReqsMetrics.record(finalRejectionDelta, threadPoolTypeTag);
        threadPoolActiveThreadsMetrics.record(stats.getActive(), threadPoolTypeTag);
        threadPoolTotalThreadsMetrics.record(stats.getThreads(), threadPoolTypeTag);

        if (capacity >= 0) {
            ThreadPoolQueueCapacityMetrics.record(capacity, threadPoolTypeTag);
        }
    }

    private void initialiseMetricsIfNeeded() {
        if (metricsInitialised == false) {
            threadPoolQueueSizeMetrics =
                    metricsRegistry.createHistogram(
                            AllMetrics.ThreadPoolValue.Constants.QUEUE_SIZE_VALUE,
                            "ThreadPool Queue Size Metrics",
                            "");

            threadPoolRejectedReqsMetrics =
                    metricsRegistry.createHistogram(
                            AllMetrics.ThreadPoolValue.Constants.REJECTED_VALUE,
                            "ThreadPool Rejected Reqs Metrics",
                            "");

            threadPoolTotalThreadsMetrics =
                    metricsRegistry.createHistogram(
                            AllMetrics.ThreadPoolValue.Constants.THREADS_COUNT_VALUE,
                            "ThreadPool Total Threads Metrics",
                            "");

            threadPoolActiveThreadsMetrics =
                    metricsRegistry.createHistogram(
                            AllMetrics.ThreadPoolValue.Constants.THREADS_ACTIVE_VALUE,
                            "ThreadPool Active Threads Metrics",
                            "");

            ThreadPoolQueueCapacityMetrics =
                    metricsRegistry.createHistogram(
                            AllMetrics.ThreadPoolValue.Constants.QUEUE_CAPACITY_VALUE,
                            "ThreadPool Queue Capacity Metrics",
                            "");
            metricsInitialised = true;
        }
    }

    private static class ThreadPoolStatsRecord {
        private final long timestamp;
        private final long rejected;

        ThreadPoolStatsRecord(long timestamp, long rejected) {
            this.timestamp = timestamp;
            this.rejected = rejected;
        }

        public long getTimestamp() {
            return timestamp;
        }

        public long getRejected() {
            return rejected;
        }
    }
}
