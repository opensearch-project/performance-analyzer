/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.config.setting.handler;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.MockitoAnnotations.initMocks;
import static org.opensearch.performanceanalyzer.config.ConfigOverridesTestHelper.ACTION1;
import static org.opensearch.performanceanalyzer.config.ConfigOverridesTestHelper.ACTION2;
import static org.opensearch.performanceanalyzer.config.ConfigOverridesTestHelper.COLLECTOR1;
import static org.opensearch.performanceanalyzer.config.ConfigOverridesTestHelper.COLLECTOR2;
import static org.opensearch.performanceanalyzer.config.ConfigOverridesTestHelper.COLLECTOR3;
import static org.opensearch.performanceanalyzer.config.ConfigOverridesTestHelper.COLLECTOR4;
import static org.opensearch.performanceanalyzer.config.ConfigOverridesTestHelper.DECIDER1;
import static org.opensearch.performanceanalyzer.config.ConfigOverridesTestHelper.DECIDER2;
import static org.opensearch.performanceanalyzer.config.ConfigOverridesTestHelper.DECIDER3;
import static org.opensearch.performanceanalyzer.config.ConfigOverridesTestHelper.DECIDER4;
import static org.opensearch.performanceanalyzer.config.ConfigOverridesTestHelper.RCA1;
import static org.opensearch.performanceanalyzer.config.ConfigOverridesTestHelper.RCA2;
import static org.opensearch.performanceanalyzer.config.ConfigOverridesTestHelper.RCA3;
import static org.opensearch.performanceanalyzer.config.ConfigOverridesTestHelper.RCA4;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.opensearch.common.settings.Setting;
import org.opensearch.performanceanalyzer.config.ConfigOverridesTestHelper;
import org.opensearch.performanceanalyzer.config.overrides.ConfigOverrides;
import org.opensearch.performanceanalyzer.config.overrides.ConfigOverridesWrapper;
import org.opensearch.performanceanalyzer.config.setting.ClusterSettingsManager;

public class ConfigOverridesClusterSettingHandlerTests {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String TEST_KEY = "test key";
    private static final ConfigOverrides EMPTY_OVERRIDES = new ConfigOverrides();
    private ConfigOverridesClusterSettingHandler testClusterSettingHandler;
    private ConfigOverridesWrapper testOverridesWrapper;
    private Setting<String> testSetting;
    private ConfigOverrides testOverrides;

    @Mock private ClusterSettingsManager mockClusterSettingsManager;

    @Captor private ArgumentCaptor<String> updatedClusterSettingCaptor;

    @Before
    public void setUp() {
        initMocks(this);
        this.testSetting = Setting.simpleString(TEST_KEY);
        this.testOverridesWrapper = new ConfigOverridesWrapper();
        this.testOverrides = ConfigOverridesTestHelper.buildValidConfigOverrides();
        testOverridesWrapper.setCurrentClusterConfigOverrides(EMPTY_OVERRIDES);

        this.testClusterSettingHandler =
                new ConfigOverridesClusterSettingHandler(
                        testOverridesWrapper, mockClusterSettingsManager, testSetting);
    }

    @Test
    public void onSettingUpdateSuccessTest() throws JsonProcessingException {
        String updatedSettingValue = ConfigOverridesTestHelper.getValidConfigOverridesJson();
        testClusterSettingHandler.onSettingUpdate(updatedSettingValue);

        assertEquals(
                updatedSettingValue,
                MAPPER.writeValueAsString(testOverridesWrapper.getCurrentClusterConfigOverrides()));
    }

    @Test
    public void onSettingUpdateFailureTest() throws IOException {
        String updatedSettingValue = "invalid json";
        ConfigOverridesWrapper failingOverridesWrapper = new ConfigOverridesWrapper();

        testClusterSettingHandler =
                new ConfigOverridesClusterSettingHandler(
                        failingOverridesWrapper, mockClusterSettingsManager, testSetting);

        testClusterSettingHandler.onSettingUpdate(updatedSettingValue);

        assertEquals(
                MAPPER.writeValueAsString(EMPTY_OVERRIDES),
                MAPPER.writeValueAsString(testOverridesWrapper.getCurrentClusterConfigOverrides()));
    }

    @Test
    public void onSettingUpdateEmptySettingsTest() throws IOException {
        ConfigOverridesWrapper failingOverridesWrapper = new ConfigOverridesWrapper();

        testClusterSettingHandler =
                new ConfigOverridesClusterSettingHandler(
                        failingOverridesWrapper, mockClusterSettingsManager, testSetting);

        testClusterSettingHandler.onSettingUpdate(null);

        assertEquals(
                MAPPER.writeValueAsString(EMPTY_OVERRIDES),
                MAPPER.writeValueAsString(testOverridesWrapper.getCurrentClusterConfigOverrides()));
    }

    @Test
    public void updateConfigOverridesMergeSuccessTest() throws IOException {
        testOverridesWrapper.setCurrentClusterConfigOverrides(testOverrides);

        ConfigOverrides expectedOverrides = new ConfigOverrides();
        ConfigOverrides additionalOverrides = new ConfigOverrides();
        // current enabled rcas: 3,4. current disabled rcas: 1,2
        additionalOverrides.getEnable().setRcas(Arrays.asList(RCA1, RCA1));

        expectedOverrides.getEnable().setRcas(Arrays.asList(RCA1, RCA3, RCA4));
        expectedOverrides.getDisable().setRcas(Collections.singletonList(RCA2));

        // current enabled deciders: 3,4. current disabled deciders: none
        additionalOverrides.getDisable().setDeciders(Arrays.asList(DECIDER3, DECIDER1));
        additionalOverrides.getEnable().setDeciders(Collections.singletonList(DECIDER2));

        additionalOverrides.getDisable().setCollectors(Arrays.asList(COLLECTOR3, COLLECTOR1));
        additionalOverrides.getEnable().setCollectors(Collections.singletonList(COLLECTOR2));

        expectedOverrides.getEnable().setDeciders(Arrays.asList(DECIDER2, DECIDER4));
        expectedOverrides.getDisable().setDeciders(Arrays.asList(DECIDER3, DECIDER1));

        expectedOverrides.getEnable().setCollectors(Arrays.asList(COLLECTOR2, COLLECTOR4));
        expectedOverrides.getDisable().setCollectors(Arrays.asList(COLLECTOR3, COLLECTOR1));

        // current enabled actions: none. current disabled actions: 1,2
        additionalOverrides.getEnable().setActions(Arrays.asList(ACTION1, ACTION2));

        expectedOverrides.getEnable().setActions(Arrays.asList(ACTION1, ACTION2));

        testClusterSettingHandler.updateConfigOverrides(additionalOverrides);
        verify(mockClusterSettingsManager)
                .updateSetting(eq(testSetting), updatedClusterSettingCaptor.capture());

        assertTrue(
                areEqual(
                        expectedOverrides,
                        MAPPER.readValue(
                                updatedClusterSettingCaptor.getValue(), ConfigOverrides.class)));
    }

    private boolean areEqual(final ConfigOverrides expected, final ConfigOverrides actual) {
        Collections.sort(expected.getEnable().getRcas());
        Collections.sort(actual.getEnable().getRcas());
        assertEquals(expected.getEnable().getRcas(), actual.getEnable().getRcas());

        Collections.sort(expected.getEnable().getActions());
        Collections.sort(actual.getEnable().getActions());
        assertEquals(expected.getEnable().getActions(), actual.getEnable().getActions());

        Collections.sort(expected.getEnable().getDeciders());
        Collections.sort(actual.getEnable().getDeciders());
        assertEquals(expected.getEnable().getDeciders(), actual.getEnable().getDeciders());

        Collections.sort(expected.getEnable().getCollectors());
        Collections.sort(actual.getEnable().getCollectors());
        assertEquals(expected.getEnable().getCollectors(), actual.getEnable().getCollectors());

        Collections.sort(expected.getDisable().getRcas());
        Collections.sort(actual.getDisable().getRcas());
        assertEquals(expected.getDisable().getRcas(), actual.getDisable().getRcas());

        Collections.sort(expected.getDisable().getActions());
        Collections.sort(actual.getDisable().getActions());
        assertEquals(expected.getDisable().getActions(), actual.getDisable().getActions());

        Collections.sort(expected.getDisable().getDeciders());
        Collections.sort(actual.getDisable().getDeciders());
        assertEquals(expected.getDisable().getDeciders(), actual.getDisable().getDeciders());

        Collections.sort(expected.getDisable().getCollectors());
        Collections.sort(actual.getDisable().getCollectors());
        assertEquals(expected.getDisable().getCollectors(), actual.getDisable().getCollectors());

        return true;
    }
}
