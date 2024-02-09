/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.opensearch.performanceanalyzer.commons.event_process.Event;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;

public class TestUtil {
    public static List<Event> readEvents() {
        List<Event> metrics = new ArrayList<>();
        PerformanceAnalyzerMetrics.metricQueue.drainTo(metrics);
        return metrics;
    }

    public static List<String> readMetricsInJsonString(int length) {
        List<Event> metrics = readEvents();
        assert metrics.size() == 1;
        String[] jsonStrs = metrics.get(0).value.split("\n");
        assert jsonStrs.length == length;
        return Arrays.asList(jsonStrs).subList(1, jsonStrs.length);
    }
}
