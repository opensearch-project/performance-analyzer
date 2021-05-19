/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 *
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

/*
 * Copyright 2020-2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package org.opensearch.performanceanalyzer.config.setting.handler;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.opensearch.performanceanalyzer.config.PerformanceAnalyzerController;
import org.opensearch.performanceanalyzer.config.setting.ClusterSettingsManager;

public class NodeStatsSettingHandlerTests {
    private NodeStatsSettingHandler handler;

    @Mock private PerformanceAnalyzerController controller;
    @Mock private ClusterSettingsManager clusterSettingsManager;

    @Before
    public void init() {
        initMocks(this);
        handler = new NodeStatsSettingHandler(controller, clusterSettingsManager);
    }

    @Test
    public void testOnSettingUpdate() {
        Integer newSettingValue = null;
        handler.onSettingUpdate(newSettingValue);
        verify(controller, never()).updateNodeStatsShardsPerCollection(anyInt());

        newSettingValue = 1;
        handler.onSettingUpdate(newSettingValue);
        verify(controller).updateNodeStatsShardsPerCollection(newSettingValue);
    }
}
