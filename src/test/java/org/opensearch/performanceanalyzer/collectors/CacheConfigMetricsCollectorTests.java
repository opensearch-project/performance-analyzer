/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.paranamer.ParanamerModule;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.indices.IndicesService;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.event_process.Event;
import org.opensearch.performanceanalyzer.commons.metrics.AllMetrics.CacheType;
import org.opensearch.performanceanalyzer.commons.metrics.MetricsConfiguration;
import org.opensearch.performanceanalyzer.commons.metrics.PerformanceAnalyzerMetrics;
import org.opensearch.performanceanalyzer.config.PluginSettings;
import org.opensearch.performanceanalyzer.util.TestUtil;
import org.opensearch.test.OpenSearchSingleNodeTestCase;

public class CacheConfigMetricsCollectorTests extends OpenSearchSingleNodeTestCase {
    private static final String TEST_INDEX = "test";
    private CacheConfigMetricsCollector collector;
    private long startTimeInMills = 1153721339;

    @Before
    public void init() {
        IndicesService indicesService = getInstanceFromNode(IndicesService.class);
        OpenSearchResources.INSTANCE.setIndicesService(indicesService);
        MetricsConfiguration.CONFIG_MAP.put(
                CacheConfigMetricsCollector.class, MetricsConfiguration.cdefault);
        collector = new CacheConfigMetricsCollector();

        // clean metricQueue before running every test
        TestUtil.readEvents();
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void testGetMetricsPath() {
        String expectedPath =
                PluginSettings.instance().getMetricsLocation()
                        + PerformanceAnalyzerMetrics.getTimeInterval(startTimeInMills)
                        + "/"
                        + PerformanceAnalyzerMetrics.sCacheConfigPath;
        String actualPath = collector.getMetricsPath(startTimeInMills);
        assertEquals(expectedPath, actualPath);

        try {
            collector.getMetricsPath(startTimeInMills, "cacheConfigPath");
            fail("Negative scenario test: Should have been a RuntimeException");
        } catch (RuntimeException ex) {
            // - expecting exception...1 values passed; 0 expected
        }
    }

    @Test
    public void testCollectMetrics() throws IOException {
        createIndex(TEST_INDEX);
        collector.collectMetrics(startTimeInMills);

        List<CacheConfigMetricsCollector.CacheMaxSizeStatus> metrics = readMetrics();
        assertEquals(2, metrics.size());
        CacheConfigMetricsCollector.CacheMaxSizeStatus filedDataCache = metrics.get(0);
        CacheConfigMetricsCollector.CacheMaxSizeStatus shardRequestCache = metrics.get(1);
        assertEquals(CacheType.FIELD_DATA_CACHE.toString(), filedDataCache.getCacheType());
        assertEquals(CacheType.SHARD_REQUEST_CACHE.toString(), shardRequestCache.getCacheType());
    }

    private List<CacheConfigMetricsCollector.CacheMaxSizeStatus> readMetrics() throws IOException {
        List<Event> metrics = TestUtil.readEvents();
        assert metrics.size() == 1;
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new ParanamerModule());

        List<CacheConfigMetricsCollector.CacheMaxSizeStatus> list = new ArrayList<>();
        String[] jsonStrs = metrics.get(0).value.split("\n");
        assert jsonStrs.length == 3;
        for (int i = 1; i < 3; i++) {
            list.add(
                    objectMapper.readValue(
                            jsonStrs[i], CacheConfigMetricsCollector.CacheMaxSizeStatus.class));
        }
        return list;
    }
}
