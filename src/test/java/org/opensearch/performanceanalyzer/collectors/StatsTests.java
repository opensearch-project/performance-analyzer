/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import org.opensearch.performanceanalyzer.commons.collectors.StatsCollector;
import org.opensearch.performanceanalyzer.commons.stats.metrics.StatExceptionCode;
import org.opensearch.performanceanalyzer.util.Utils;

public class StatsTests {
    static class AddStatsThread extends Thread {
        LinkedList<StatExceptionCode> exceptionCodeList;
        int startIndex;
        int totalToIterate;
        StatsCollector sc;

        AddStatsThread(
                LinkedList<StatExceptionCode> exceptionCodeList,
                int startIndex,
                int totalToIterate,
                StatsCollector sc) {
            this.exceptionCodeList = exceptionCodeList;
            this.startIndex = startIndex;
            this.totalToIterate = totalToIterate;
            this.sc = sc;
        }

        @Override
        public void run() {
            StatsTests.iterate(exceptionCodeList, startIndex, totalToIterate, sc);
        }
    }

    static Random RANDOM = new Random();
    private static final int MAX_COUNT = 500;
    private static final int CLUSTER_MANAGER_SERVICE_EVENTS_METRICS_ERROR =
            Math.abs(RANDOM.nextInt() % MAX_COUNT);
    private static final int REQUEST_REMOTE_ERRORS = Math.abs(RANDOM.nextInt() % MAX_COUNT);
    private static final int READER_PARSER_ERRORS = Math.abs(RANDOM.nextInt() % MAX_COUNT);
    private static final int READER_RESTART_PROCESSINGS = Math.abs(RANDOM.nextInt() % MAX_COUNT);
    private static final int TOTAL_ERRORS =
            CLUSTER_MANAGER_SERVICE_EVENTS_METRICS_ERROR
                    + REQUEST_REMOTE_ERRORS
                    + READER_PARSER_ERRORS
                    + READER_RESTART_PROCESSINGS;
    private static final AtomicInteger DEFAULT_VAL = new AtomicInteger(0);
    private static final int EXEC_COUNT = 20;

    @Test
    public void testStats() throws Exception {
        Utils.configureMetrics();
        System.setProperty("performanceanalyzer.metrics.log.enabled", "False");

        LinkedList<StatExceptionCode> exceptionCodeList = new LinkedList<>();

        for (int i = 0; i < CLUSTER_MANAGER_SERVICE_EVENTS_METRICS_ERROR; i++) {
            exceptionCodeList.add(
                    StatExceptionCode.CLUSTER_MANAGER_SERVICE_EVENTS_METRICS_COLLECTOR_ERROR);
        }

        for (int i = 0; i < REQUEST_REMOTE_ERRORS; i++) {
            exceptionCodeList.add(StatExceptionCode.REQUEST_REMOTE_ERROR);
        }

        for (int i = 0; i < READER_PARSER_ERRORS; i++) {
            exceptionCodeList.add(StatExceptionCode.READER_PARSER_ERROR);
        }

        for (int i = 0; i < READER_RESTART_PROCESSINGS; i++) {
            exceptionCodeList.add(StatExceptionCode.READER_RESTART_PROCESSING);
        }

        Collections.shuffle(exceptionCodeList);
        StatsCollector sc = StatsCollector.instance();
        int iterateSize = exceptionCodeList.size() / EXEC_COUNT;
        runInSerial(iterateSize, exceptionCodeList, sc);
        runInParallel(iterateSize, exceptionCodeList, sc);
    }

    private static void runInSerial(
            int iterateSize, LinkedList<StatExceptionCode> exceptionCodeList, StatsCollector sc) {
        int i = 0;
        sc.getCounters().clear();
        for (; i < EXEC_COUNT - 1; i++) {
            iterate(exceptionCodeList, i * iterateSize, iterateSize, sc);
        }
        iterate(exceptionCodeList, i * iterateSize, exceptionCodeList.size() - i * iterateSize, sc);
        assertExpected(sc);
    }

    private static void assertExpected(StatsCollector sc) {
        assertEquals(
                sc.getCounters()
                        .getOrDefault(
                                StatExceptionCode
                                        .CLUSTER_MANAGER_SERVICE_EVENTS_METRICS_COLLECTOR_ERROR
                                        .toString(),
                                DEFAULT_VAL)
                        .get(),
                CLUSTER_MANAGER_SERVICE_EVENTS_METRICS_ERROR);
        assertEquals(
                sc.getCounters()
                        .getOrDefault(
                                StatExceptionCode.REQUEST_REMOTE_ERROR.toString(), DEFAULT_VAL)
                        .get(),
                REQUEST_REMOTE_ERRORS);
        assertEquals(
                sc.getCounters()
                        .getOrDefault(StatExceptionCode.READER_PARSER_ERROR.toString(), DEFAULT_VAL)
                        .get(),
                READER_PARSER_ERRORS);
        assertEquals(
                sc.getCounters()
                        .getOrDefault(
                                StatExceptionCode.READER_RESTART_PROCESSING.toString(), DEFAULT_VAL)
                        .get(),
                READER_RESTART_PROCESSINGS);
        assertEquals(
                sc.getCounters()
                        .getOrDefault(StatExceptionCode.TOTAL_ERROR.toString(), DEFAULT_VAL)
                        .get(),
                TOTAL_ERRORS);
    }

    private static void runInParallel(
            int iterateSize, LinkedList<StatExceptionCode> exceptionCodeList, StatsCollector sc)
            throws Exception {
        sc.getCounters().clear();
        int i = 0;
        Thread[] threads = new Thread[EXEC_COUNT];
        for (; i < EXEC_COUNT - 1; i++) {
            threads[i] = new AddStatsThread(exceptionCodeList, i * iterateSize, iterateSize, sc);
        }
        threads[i] =
                new AddStatsThread(
                        exceptionCodeList,
                        i * iterateSize,
                        exceptionCodeList.size() - i * iterateSize,
                        sc);

        for (i = 0; i < EXEC_COUNT; i++) {
            threads[i].start();
        }

        for (i = 0; i < EXEC_COUNT; i++) {
            threads[i].join();
        }

        assertExpected(sc);
    }

    private static void iterate(
            LinkedList<StatExceptionCode> exceptionCodeList,
            int startIndex,
            int totalToIterate,
            StatsCollector sc) {
        int count = 0;
        ListIterator<StatExceptionCode> iterator = exceptionCodeList.listIterator(startIndex);

        while (count < totalToIterate && iterator.hasNext()) {
            StatExceptionCode exceptionCode = iterator.next();

            if (exceptionCode != null) {
                sc.logException(exceptionCode);
            }
            count++;
        }
    }
}
