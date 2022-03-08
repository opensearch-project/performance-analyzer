/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.config.setting;

public interface ClusterSettingListener<T> {
    /**
     * Handler that gets called when there is a new value for the setting that this listener is
     * listening to.
     *
     * @param newSettingValue The value of the new setting.
     */
    void onSettingUpdate(T newSettingValue);
}
