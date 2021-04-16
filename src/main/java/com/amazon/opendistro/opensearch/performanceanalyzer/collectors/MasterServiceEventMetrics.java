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

package com.amazon.opendistro.opensearch.performanceanalyzer.collectors;


import com.amazon.opendistro.opensearch.performanceanalyzer.OpenSearchResources;
import com.amazon.opendistro.opensearch.performanceanalyzer.metrics.AllMetrics.MasterMetricDimensions;
import com.amazon.opendistro.opensearch.performanceanalyzer.metrics.AllMetrics.MasterMetricValues;
import com.amazon.opendistro.opensearch.performanceanalyzer.metrics.MetricsConfiguration;
import com.amazon.opendistro.opensearch.performanceanalyzer.metrics.MetricsProcessor;
import com.amazon.opendistro.opensearch.performanceanalyzer.metrics.PerformanceAnalyzerMetrics;
import com.amazon.opendistro.opensearch.performanceanalyzer.metrics.ThreadIDUtil;
import com.google.common.annotations.VisibleForTesting;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ThreadPoolExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.cluster.service.MasterService;
import org.opensearch.cluster.service.SourcePrioritizedRunnable;
import org.opensearch.common.util.concurrent.PrioritizedOpenSearchThreadPoolExecutor;

@SuppressWarnings("unchecked")
public class MasterServiceEventMetrics extends PerformanceAnalyzerMetricsCollector
        implements MetricsProcessor {
    public static final int SAMPLING_TIME_INTERVAL =
            MetricsConfiguration.CONFIG_MAP.get(MasterServiceEventMetrics.class).samplingInterval;
    private static final Logger LOG = LogManager.getLogger(MasterServiceEventMetrics.class);
    private static final String MASTER_NODE_NOT_UP_METRIC = "MasterNodeNotUp";
    private static final int KEYS_PATH_LENGTH = 3;
    private StringBuilder value;
    private static final int TPEXECUTOR_ADD_PENDING_PARAM_COUNT = 3;
    private Queue<Runnable> masterServiceCurrentQueue;
    private PrioritizedOpenSearchThreadPoolExecutor prioritizedOpenSearchThreadPoolExecutor;
    private HashSet<Object> masterServiceWorkers;
    private long currentThreadId;
    private Object currentWorker;

    @VisibleForTesting long lastTaskInsertionOrder;

    public MasterServiceEventMetrics() {
        super(SAMPLING_TIME_INTERVAL, "MasterServiceEventMetrics");
        masterServiceCurrentQueue = null;
        masterServiceWorkers = null;
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
                PerformanceAnalyzerMetrics.sMasterTaskPath,
                keysPath[1],
                keysPath[2]);
    }

    @Override
    public void collectMetrics(long startTime) {
        try {
            if (OpenSearchResources.INSTANCE.getClusterService() == null
                    || OpenSearchResources.INSTANCE.getClusterService().getMasterService()
                            == null) {
                return;
            }

            value.setLength(0);
            Queue<Runnable> current = getMasterServiceCurrentQueue();

            if (current == null || current.size() == 0) {
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
                            MasterMetricDimensions.MASTER_TASK_PRIORITY.toString(),
                            firstPending.priority.toString());
                    // - as it is sampling, we won't exactly know the start time of the current
                    // task, we will be
                    // - capturing start time as midpoint of previous time bucket
                    PerformanceAnalyzerMetrics.addMetricEntry(
                            value,
                            MasterMetricValues.START_TIME.toString(),
                            startTime - SAMPLING_TIME_INTERVAL / 2);
                    PerformanceAnalyzerMetrics.addMetricEntry(
                            value,
                            MasterMetricDimensions.MASTER_TASK_TYPE.toString(),
                            firstSpaceIndex == -1
                                    ? task.source()
                                    : task.source().substring(0, firstSpaceIndex));
                    PerformanceAnalyzerMetrics.addMetricEntry(
                            value,
                            MasterMetricDimensions.MASTER_TASK_METADATA.toString(),
                            firstSpaceIndex == -1 ? "" : task.source().substring(firstSpaceIndex));
                    PerformanceAnalyzerMetrics.addMetricEntry(
                            value,
                            MasterMetricDimensions.MASTER_TASK_QUEUE_TIME.toString(),
                            task.getAgeInMillis());

                    saveMetricValues(
                            value.toString(),
                            startTime,
                            String.valueOf(getMasterThreadId()),
                            String.valueOf(lastTaskInsertionOrder),
                            PerformanceAnalyzerMetrics.START_FILE_NAME);

                    value.setLength(0);
                }
            } else {
                generateFinishMetrics(startTime);
            }
            LOG.debug(() -> "Successfully collected Master Event Metrics.");
        } catch (Exception ex) {
            StatsCollector.instance().logException(StatExceptionCode.MASTER_METRICS_ERROR);
            LOG.debug(
                    "Exception in Collecting Master Metrics: {} for startTime {} with ExceptionCode: {}",
                    () -> ex.toString(),
                    () -> startTime,
                    () -> StatExceptionCode.MASTER_METRICS_ERROR.toString());
        }
    }

    @VisibleForTesting
    void generateFinishMetrics(long startTime) {
        if (lastTaskInsertionOrder != -1) {
            value.append(PerformanceAnalyzerMetrics.getCurrentTimeMetric());
            PerformanceAnalyzerMetrics.addMetricEntry(
                    value,
                    MasterMetricValues.FINISH_TIME.toString(),
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
    Field getMasterServiceTPExecutorField() throws Exception {
        Field threadPoolExecutorField = MasterService.class.getDeclaredField("threadPoolExecutor");
        threadPoolExecutorField.setAccessible(true);
        return threadPoolExecutorField;
    }

    // - Separated to have a unit test; and catch any code changes around this field
    Field getPrioritizedTPExecutorCurrentField() throws Exception {
        Field currentField =
                PrioritizedOpenSearchThreadPoolExecutor.class.getDeclaredField("current");
        currentField.setAccessible(true);
        return currentField;
    }

    // - Separated to have a unit test; and catch any code changes around this field
    Field getTPExecutorWorkersField() throws Exception {
        Field workersField = ThreadPoolExecutor.class.getDeclaredField("workers");
        workersField.setAccessible(true);
        return workersField;
    }

    // - Separated to have a unit test; and catch any code changes around this field
    Method getPrioritizedTPExecutorAddPendingMethod() throws Exception {
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

    Queue<Runnable> getMasterServiceCurrentQueue() throws Exception {
        if (masterServiceCurrentQueue == null) {
            if (OpenSearchResources.INSTANCE.getClusterService() != null) {
                MasterService masterService =
                        OpenSearchResources.INSTANCE.getClusterService().getMasterService();

                if (masterService != null) {
                    if (prioritizedOpenSearchThreadPoolExecutor == null) {
                        prioritizedOpenSearchThreadPoolExecutor =
                                (PrioritizedOpenSearchThreadPoolExecutor)
                                        getMasterServiceTPExecutorField().get(masterService);
                    }

                    if (prioritizedOpenSearchThreadPoolExecutor != null) {
                        masterServiceCurrentQueue =
                                (Queue<Runnable>)
                                        getPrioritizedTPExecutorCurrentField()
                                                .get(prioritizedOpenSearchThreadPoolExecutor);
                    } else {
                        StatsCollector.instance().logMetric(MASTER_NODE_NOT_UP_METRIC);
                    }
                }
            }
        }

        return masterServiceCurrentQueue;
    }

    HashSet<Object> getMasterServiceWorkers() throws Exception {
        if (masterServiceWorkers == null) {
            if (OpenSearchResources.INSTANCE.getClusterService() != null) {
                MasterService masterService =
                        OpenSearchResources.INSTANCE.getClusterService().getMasterService();

                if (masterService != null) {
                    if (prioritizedOpenSearchThreadPoolExecutor == null) {
                        prioritizedOpenSearchThreadPoolExecutor =
                                (PrioritizedOpenSearchThreadPoolExecutor)
                                        getMasterServiceTPExecutorField().get(masterService);
                    }

                    masterServiceWorkers =
                            (HashSet<Object>)
                                    getTPExecutorWorkersField()
                                            .get(prioritizedOpenSearchThreadPoolExecutor);
                }
            }
        }

        return masterServiceWorkers;
    }

    long getMasterThreadId() throws Exception {
        HashSet<Object> currentWorkers = getMasterServiceWorkers();

        if (currentWorkers.size() > 0) {
            if (currentWorkers.size() > 1) {
                LOG.error(
                        "Master threads are more than 1 (expected); current Master threads count: {}",
                        currentWorkers.size());
                currentThreadId = -1;
                currentWorker = null;
            } else {
                Object currentTopWorker = currentWorkers.iterator().next();
                if (currentWorker != currentTopWorker) {
                    currentWorker = currentTopWorker;
                    Thread masterThread = (Thread) getWorkerThreadField().get(currentWorker);
                    currentThreadId = ThreadIDUtil.INSTANCE.getNativeThreadId(masterThread.getId());
                }
            }
        } else {
            currentThreadId = -1;
            currentWorker = null;
        }

        return currentThreadId;
    }

    Field getWorkerThreadField() throws Exception {
        Class<?> tpExecutorWorkerClass =
                Class.forName("java.util.concurrent.ThreadPoolExecutor$Worker");
        Field workerField = tpExecutorWorkerClass.getDeclaredField("thread");
        workerField.setAccessible(true);
        return workerField;
    }
}
