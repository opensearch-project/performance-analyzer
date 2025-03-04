/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode.CLUSTER_MANAGER_NODE_NOT_UP;
import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode.CLUSTER_MANAGER_SERVICE_EVENTS_METRICS_COLLECTOR_ERROR;
import static org.opensearch.performanceanalyzer.commons.stats.metrics.StatMetrics.CLUSTER_MANAGER_SERVICE_EVENTS_METRICS_COLLECTOR_EXECUTION_TIME;

import com.google.common.annotations.VisibleForTesting;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ThreadPoolExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.cluster.service.ClusterManagerService;
import org.opensearch.cluster.service.SourcePrioritizedRunnable;
import org.opensearch.common.util.concurrent.PrioritizedOpenSearchThreadPoolExecutor;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.collectors.PerformanceAnalyzerMetricsCollector;
import org.opensearch.performanceanalyzer.commons.collectors.StatsCollector;
import org.opensearch.performanceanalyzer.commons.metrics.*;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.ClusterManagerMetricDimensions;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.ClusterManagerMetricValues;
import org.opensearch.performanceanalyzer.commons.util.ThreadIDUtil;

@SuppressWarnings("unchecked")
public class ClusterManagerServiceEventMetrics extends PerformanceAnalyzerMetricsCollector
        implements MetricsProcessor {
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(ClusterManagerServiceEventMetrics.class)
                    .samplingInterval;
    private static final Logger LOG = LogManager.getLogger(ClusterManagerServiceEventMetrics.class);
    private static final int KEYS_PATH_LENGTH = 3;
    private StringBuilder value;
    private static final int TPEXECUTOR_ADD_PENDING_PARAM_COUNT = 3;
    private Queue<Runnable> clusterManagerServiceCurrentQueue;
    private PrioritizedOpenSearchThreadPoolExecutor prioritizedOpenSearchThreadPoolExecutor;
    private HashSet<Object> clusterManagerServiceWorkers;
    private long currentThreadId;
    private Object currentWorker;

    @VisibleForTesting long lastTaskInsertionOrder;

    public ClusterManagerServiceEventMetrics() {
        super(
                SAMPLING_TIME_INTERVAL,
                "ClusterManagerServiceEventMetrics",
                CLUSTER_MANAGER_SERVICE_EVENTS_METRICS_COLLECTOR_EXECUTION_TIME,
                CLUSTER_MANAGER_SERVICE_EVENTS_METRICS_COLLECTOR_ERROR);
        clusterManagerServiceCurrentQueue = null;
        clusterManagerServiceWorkers = null;
        prioritizedOpenSearchThreadPoolExecutor = null;
        currentWorker = null;
        currentThreadId = -1;
        lastTaskInsertionOrder = -1;
        value = new StringBuilder();
    }

    @Override
    public String getMetricsPath(long startTime, String... keysPath) {
        // throw exception if keys.length is not equal to 3 (Keys should be threadID, taskID,
        // start/finish)
        if (keysPath.length != KEYS_PATH_LENGTH) {
            throw new RuntimeException("keys length should be " + KEYS_PATH_LENGTH);
        }
        return PerformanceAnalyzerMetrics.generatePath(
                startTime,
                PerformanceAnalyzerMetrics.sThreadsPath,
                keysPath[0],
                PerformanceAnalyzerMetrics.sClusterManagerTaskPath,
                keysPath[1],
                keysPath[2]);
    }

    @Override
    public void collectMetrics(long startTime) {
        try {
            if (Objects.isNull(OpenSearchResources.INSTANCE.getClusterService())
                    || Objects.isNull(
                            OpenSearchResources.INSTANCE
                                    .getClusterService()
                                    .getClusterManagerService())) {
                return;
            }

            value.setLength(0);
            Queue<Runnable> current = getClusterManagerServiceCurrentQueue();

            if (Objects.isNull(current) || current.size() == 0) {
                generateFinishMetrics(startTime);
                return;
            }

            List<PrioritizedOpenSearchThreadPoolExecutor.Pending> pending = new ArrayList<>();

            Object[] parameters = new Object[TPEXECUTOR_ADD_PENDING_PARAM_COUNT];
            parameters[0] = new ArrayList<>(current);
            parameters[1] = pending;
            parameters[2] = true;

            getPrioritizedTPExecutorAddPendingMethod()
                    .invoke(prioritizedOpenSearchThreadPoolExecutor, parameters);

            if (pending.size() != 0) {
                PrioritizedOpenSearchThreadPoolExecutor.Pending firstPending = pending.get(0);

                if (lastTaskInsertionOrder != firstPending.insertionOrder) {
                    generateFinishMetrics(startTime);
                    SourcePrioritizedRunnable task = (SourcePrioritizedRunnable) firstPending.task;
                    lastTaskInsertionOrder = firstPending.insertionOrder;
                    int firstSpaceIndex = task.source().indexOf(" ");
                    value.append(PerformanceAnalyzerMetrics.getCurrentTimeMetric());
                    PerformanceAnalyzerMetrics.addMetricEntry(
                            value,
                            ClusterManagerMetricDimensions.CLUSTER_MANAGER_TASK_PRIORITY.toString(),
                            firstPending.priority.toString());
                    // - as it is sampling, we won't exactly know the start time of the current
                    // task, we will be
                    // - capturing start time as midpoint of previous time bucket
                    PerformanceAnalyzerMetrics.addMetricEntry(
                            value,
                            ClusterManagerMetricValues.START_TIME.toString(),
                            startTime - SAMPLING_TIME_INTERVAL / 2);
                    PerformanceAnalyzerMetrics.addMetricEntry(
                            value,
                            ClusterManagerMetricDimensions.CLUSTER_MANAGER_TASK_TYPE.toString(),
                            firstSpaceIndex == -1
                                    ? task.source()
                                    : task.source().substring(0, firstSpaceIndex));
                    PerformanceAnalyzerMetrics.addMetricEntry(
                            value,
                            ClusterManagerMetricDimensions.CLUSTER_MANAGER_TASK_METADATA.toString(),
                            firstSpaceIndex == -1 ? "" : task.source().substring(firstSpaceIndex));
                    PerformanceAnalyzerMetrics.addMetricEntry(
                            value,
                            ClusterManagerMetricDimensions.CLUSTER_MANAGER_TASK_QUEUE_TIME
                                    .toString(),
                            task.getAgeInMillis());

                    saveMetricValues(
                            value.toString(),
                            startTime,
                            String.valueOf(getClusterManagerThreadId()),
                            String.valueOf(lastTaskInsertionOrder),
                            PerformanceAnalyzerMetrics.START_FILE_NAME);

                    value.setLength(0);
                }
            } else {
                generateFinishMetrics(startTime);
            }
        } catch (NoSuchFieldException
                | NoSuchMethodException
                | InvocationTargetException
                | IllegalAccessException
                | ClassNotFoundException e) {
            LOG.debug(
                    "[ {} ] Exception raised while getting Cluster Manager Service Event metrics: {} ",
                    this::getCollectorName,
                    e::getMessage);
            StatsCollector.instance()
                    .logException(CLUSTER_MANAGER_SERVICE_EVENTS_METRICS_COLLECTOR_ERROR);
        }
    }

    @VisibleForTesting
    void generateFinishMetrics(long startTime) {
        if (lastTaskInsertionOrder != -1) {
            value.append(PerformanceAnalyzerMetrics.getCurrentTimeMetric());
            PerformanceAnalyzerMetrics.addMetricEntry(
                    value,
                    ClusterManagerMetricValues.FINISH_TIME.toString(),
                    startTime - SAMPLING_TIME_INTERVAL / 2);
            saveMetricValues(
                    value.toString(),
                    startTime,
                    String.valueOf(currentThreadId),
                    String.valueOf(lastTaskInsertionOrder),
                    PerformanceAnalyzerMetrics.FINISH_FILE_NAME);
            value.setLength(0);
            lastTaskInsertionOrder = -1;
        }
    }

    // - Separated to have a unit test; and catch any code changes around this field
    Field getClusterManagerServiceTPExecutorField() throws NoSuchFieldException {
        // Currently ClusterManagerService extends MasterService, remove getSuperClass(),
        // once MasterService contents are moved and class is removed.
        Field threadPoolExecutorField =
                ClusterManagerService.class.getDeclaredField("threadPoolExecutor");
        threadPoolExecutorField.setAccessible(true);
        return threadPoolExecutorField;
    }

    // - Separated to have a unit test; and catch any code changes around this field
    Field getPrioritizedTPExecutorCurrentField() throws NoSuchFieldException {
        Field currentField =
                PrioritizedOpenSearchThreadPoolExecutor.class.getDeclaredField("current");
        currentField.setAccessible(true);
        return currentField;
    }

    // - Separated to have a unit test; and catch any code changes around this field
    Field getTPExecutorWorkersField() throws NoSuchFieldException {
        Field workersField = ThreadPoolExecutor.class.getDeclaredField("workers");
        workersField.setAccessible(true);
        return workersField;
    }

    // - Separated to have a unit test; and catch any code changes around this field
    Method getPrioritizedTPExecutorAddPendingMethod() throws NoSuchMethodException {
        Class<?>[] classArray = new Class<?>[TPEXECUTOR_ADD_PENDING_PARAM_COUNT];
        classArray[0] = List.class;
        classArray[1] = List.class;
        classArray[2] = boolean.class;
        Method addPendingMethod =
                PrioritizedOpenSearchThreadPoolExecutor.class.getDeclaredMethod(
                        "addPending", classArray);
        addPendingMethod.setAccessible(true);
        return addPendingMethod;
    }

    Queue<Runnable> getClusterManagerServiceCurrentQueue()
            throws NoSuchFieldException, IllegalAccessException {
        if (clusterManagerServiceCurrentQueue == null) {
            if (OpenSearchResources.INSTANCE.getClusterService() != null) {
                ClusterManagerService clusterManagerService =
                        OpenSearchResources.INSTANCE.getClusterService().getClusterManagerService();

                if (clusterManagerService != null) {
                    if (prioritizedOpenSearchThreadPoolExecutor == null) {
                        prioritizedOpenSearchThreadPoolExecutor =
                                (PrioritizedOpenSearchThreadPoolExecutor)
                                        getClusterManagerServiceTPExecutorField()
                                                .get(clusterManagerService);
                    }

                    if (prioritizedOpenSearchThreadPoolExecutor != null) {
                        clusterManagerServiceCurrentQueue =
                                (Queue<Runnable>)
                                        getPrioritizedTPExecutorCurrentField()
                                                .get(prioritizedOpenSearchThreadPoolExecutor);
                    } else {
                        StatsCollector.instance().logException(CLUSTER_MANAGER_NODE_NOT_UP);
                    }
                }
            }
        }

        return clusterManagerServiceCurrentQueue;
    }

    HashSet<Object> getClusterManagerServiceWorkers()
            throws NoSuchFieldException, IllegalAccessException {
        if (clusterManagerServiceWorkers == null) {
            if (OpenSearchResources.INSTANCE.getClusterService() != null) {
                ClusterManagerService clusterManagerService =
                        OpenSearchResources.INSTANCE.getClusterService().getClusterManagerService();

                if (clusterManagerService != null) {
                    if (prioritizedOpenSearchThreadPoolExecutor == null) {
                        prioritizedOpenSearchThreadPoolExecutor =
                                (PrioritizedOpenSearchThreadPoolExecutor)
                                        getClusterManagerServiceTPExecutorField()
                                                .get(clusterManagerService);
                    }

                    clusterManagerServiceWorkers =
                            (HashSet<Object>)
                                    getTPExecutorWorkersField()
                                            .get(prioritizedOpenSearchThreadPoolExecutor);
                }
            }
        }

        return clusterManagerServiceWorkers;
    }

    long getClusterManagerThreadId()
            throws NoSuchFieldException, IllegalAccessException, ClassNotFoundException {
        HashSet<Object> currentWorkers = getClusterManagerServiceWorkers();

        if (currentWorkers.size() > 0) {
            if (currentWorkers.size() > 1) {
                LOG.error(
                        "ClusterManager threads are more than 1 (expected); current ClusterManager threads count: {}",
                        currentWorkers.size());
                currentThreadId = -1;
                currentWorker = null;
            } else {
                Object currentTopWorker = currentWorkers.iterator().next();
                if (currentWorker != currentTopWorker) {
                    currentWorker = currentTopWorker;
                    Thread clusterManagerThread =
                            (Thread) getWorkerThreadField().get(currentWorker);
                    currentThreadId =
                            ThreadIDUtil.INSTANCE.getNativeThreadId(clusterManagerThread.getId());
                }
            }
        } else {
            currentThreadId = -1;
            currentWorker = null;
        }

        return currentThreadId;
    }

    Field getWorkerThreadField() throws ClassNotFoundException, NoSuchFieldException {
        Class<?> tpExecutorWorkerClass =
                Class.forName("java.util.concurrent.ThreadPoolExecutor$Worker");
        Field workerField = tpExecutorWorkerClass.getDeclaredField("thread");
        workerField.setAccessible(true);
        return workerField;
    }
}
