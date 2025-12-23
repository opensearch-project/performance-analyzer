/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import org.junit.Before;
import org.junit.Test;
import org.opensearch.telemetry.metrics.Histogram;
import org.opensearch.telemetry.metrics.MetricsRegistry;
import org.opensearch.telemetry.metrics.tags.Tags;

public class ShardMetricsCollectorTests {
    private ShardMetricsCollector shardMetricsCollector;
    private static MetricsRegistry metricsRegistry;
    private static Histogram cpuUtilizationHistogram;
    private static Histogram heapUsedHistogram;

    @Before
    public void init() {
        if (cpuUtilizationHistogram != null && heapUsedHistogram != null) {
            // Clear any previous mock interactions
            clearInvocations(cpuUtilizationHistogram, heapUsedHistogram);
        }

        metricsRegistry = mock(MetricsRegistry.class);
        cpuUtilizationHistogram = mock(Histogram.class);
        heapUsedHistogram = mock(Histogram.class);

        OpenSearchResources.INSTANCE.setMetricsRegistry(metricsRegistry);

        when(metricsRegistry.createHistogram(anyString(), anyString(), anyString()))
                .thenAnswer(
                        invocationOnMock -> {
                            String histogramName = (String) invocationOnMock.getArguments()[0];
                            if (histogramName.equals(ShardMetricsCollector.SHARD_CPU_UTILIZATION)) {
                                return cpuUtilizationHistogram;
                            } else if (histogramName.equals(
                                    ShardMetricsCollector.SHARD_HEAP_ALLOCATED)) {
                                return heapUsedHistogram;
                            }
                            return null;
                        });
    }

    @Test
    public void testRecordMetrics() {
        shardMetricsCollector = ShardMetricsCollector.INSTANCE;
        shardMetricsCollector.initialize();
        Tags testTags = Tags.create().addTag("shard_id", "1").addTag("operation", "search");

        shardMetricsCollector.recordCpuUtilization(75.0, testTags);
        verify(cpuUtilizationHistogram, times(1)).record(75.0, testTags);

        shardMetricsCollector.recordHeapUsed(1024.0, testTags);
        verify(heapUsedHistogram, times(1)).record(1024.0, testTags);
    }

    @Test
    public void testNullHistogram() {
        // Reset collector and set null registry
        shardMetricsCollector = ShardMetricsCollector.INSTANCE;
        OpenSearchResources.INSTANCE.setMetricsRegistry(null);
        shardMetricsCollector.initialize();

        Tags testTags = Tags.create().addTag("shard_id", "1").addTag("operation", "search");

        // Verify no exceptions when recording with null histograms
        shardMetricsCollector.recordCpuUtilization(75.0, testTags);
        shardMetricsCollector.recordHeapUsed(1024.0, testTags);

        // Verify no interactions
        verifyZeroInteractions(cpuUtilizationHistogram, heapUsedHistogram);
    }
}
