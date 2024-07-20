/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.util;

import org.junit.Assert;
import org.junit.Test;

public class UtilsTests {

    @Test
    public void testCPUUtilization() {
        Assert.assertEquals(0.5, Utils.calculateCPUUtilization(2, 5, 5), 0.0);
        Assert.assertEquals(1.0, Utils.calculateCPUUtilization(1, 5, 5), 0.0);
        Assert.assertEquals(
                Double.valueOf(10 / 15.0), Utils.calculateCPUUtilization(3, 5, 10), 0.0);
    }

    @Test
    public void testCPUUtilizationZeroValue() {
        Assert.assertEquals(0.0, Utils.calculateCPUUtilization(2, 5, 0), 0.0);
        Assert.assertEquals(0.0, Utils.calculateCPUUtilization(2, 0, 5), 0.0);
        Assert.assertEquals(0.0, Utils.calculateCPUUtilization(0, 5, 5), 0.0);
    }
}
