/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.MockitoAnnotations.initMocks;

import java.nio.file.Paths;
import java.util.Arrays;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.opensearch.common.settings.Settings;
import org.opensearch.performanceanalyzer.OpenSearchResources;
import org.opensearch.performanceanalyzer.commons.collectors.ScheduledMetricCollectorsExecutor;
import org.opensearch.performanceanalyzer.config.overrides.ConfigOverrides;
import org.opensearch.performanceanalyzer.config.overrides.ConfigOverridesWrapper;

public class PerformanceAnalyzerControllerTests {
    private static final int NUM_OF_SHARDS_PER_COLLECTION = 1;
    private Settings settings;
    private PerformanceAnalyzerController controller;

    @Before
    public void init() {
        initMocks(this);
        settings = Settings.builder().put("path.home", "./").build();
        OpenSearchResources.INSTANCE.setSettings(settings);
        OpenSearchResources.INSTANCE.setConfigPath(Paths.get("build/tmp/junit_metrics"));
        controller = new PerformanceAnalyzerController(new ScheduledMetricCollectorsExecutor());
    }

    @Test
    public void testGetNodeStatsShardsPerCollection() {
        Assert.assertEquals(
                PerformanceAnalyzerController.DEFAULT_NUM_OF_SHARDS_PER_COLLECTION,
                controller.getNodeStatsShardsPerCollection());

        controller.updateNodeStatsShardsPerCollection(NUM_OF_SHARDS_PER_COLLECTION);
        assertEquals(NUM_OF_SHARDS_PER_COLLECTION, controller.getNodeStatsShardsPerCollection());
    }

    @Test
    public void testCollectorIsDisabledIfPresentInDisabledConfigOverride() {
        ConfigOverridesWrapper configOverridesWrapper = new ConfigOverridesWrapper();
        ConfigOverrides configOverrides = new ConfigOverrides();

        String collectorName = RandomStringUtils.randomAlphabetic(10);

        ConfigOverrides.Overrides overrides = new ConfigOverrides.Overrides();
        overrides.setCollectors(Arrays.asList(collectorName));
        configOverrides.setDisable(overrides);
        configOverridesWrapper.setCurrentClusterConfigOverrides(configOverrides);

        assertTrue(controller.isCollectorDisabled(configOverridesWrapper, collectorName));
    }

    @Test
    public void testCollectorIsNotDisabledIfNotPresentInDisabledConfigOverride() {
        ConfigOverridesWrapper configOverridesWrapper = new ConfigOverridesWrapper();
        ConfigOverrides configOverrides = new ConfigOverrides();

        String collectorName = RandomStringUtils.randomAlphabetic(10);

        ConfigOverrides.Overrides overrides = new ConfigOverrides.Overrides();
        configOverrides.setDisable(overrides);
        configOverridesWrapper.setCurrentClusterConfigOverrides(configOverrides);

        assertFalse(controller.isCollectorDisabled(configOverridesWrapper, collectorName));
    }

    @Test
    public void testCollectorIsDisabledIfConfigOverrideIsNotProvided() {
        String collectorName = RandomStringUtils.randomAlphabetic(10);
        assertTrue(controller.isCollectorDisabled(null, collectorName));
    }
}
