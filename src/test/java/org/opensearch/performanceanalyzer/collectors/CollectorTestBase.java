/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.MockitoAnnotations.initMocks;

import org.junit.Before;
import org.mockito.Mockito;
import org.opensearch.performanceanalyzer.commons.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;

public class CollectorTestBase {
    protected PerformanceAnalyzerController mockController;
    protected ConfigOverridesWrapper mockWrapper;

    @Before
    public void setUp() throws Exception {
        initMocks(this);
        System.setProperty("performanceanalyzer.metrics.log.enabled", "false");
        mockController = mock(PerformanceAnalyzerController.class);
        mockWrapper = mock(ConfigOverridesWrapper.class);
        Mockito.when(mockController.isCollectorDisabled(any(), anyString())).thenReturn(false);
    }
}
