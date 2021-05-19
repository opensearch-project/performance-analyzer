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
 * Copyright 2019-2021 Amazon.com, Inc. or its affiliates. All Rights Reserved.
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

package org.opensearch.performanceanalyzer.collectors;

import static org.junit.Assert.assertTrue;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import org.junit.Test;
import org.opensearch.performanceanalyzer.collectors.HeapMetricsCollector.HeapStatus;
import org.opensearch.performanceanalyzer.metrics.AllMetrics;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.CacheConfigDimension;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.CacheConfigValue;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.CircuitBreakerDimension;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.CircuitBreakerValue;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.DiskDimension;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.DiskValue;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.HeapDimension;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.HeapValue;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.IPDimension;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.IPValue;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.ShardStatsValue;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.TCPDimension;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.TCPValue;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.ThreadPoolDimension;
import org.opensearch.performanceanalyzer.metrics.AllMetrics.ThreadPoolValue;
import org.opensearch.performanceanalyzer.metrics.MetricDimension;
import org.opensearch.performanceanalyzer.metrics.MetricValue;

/**
 * Writer serialize a java bean to a /dev/shm/performanceanalyzer file using a collector's instance
 * field names or @jsonproperty annotation on getter method as json key names. Reader extracts
 * values using enum names in AllMetrics. The tests make sure the field names and enum names match.
 * If you see test errors here, it means somebody changes either the field name or enum names and
 * forget to sync them.
 */
public class JsonKeyTests {
    Function<Method, String> getMethodJsonProperty =
            f -> {
                if (!f.getName().startsWith("get")) {
                    return null;
                } else if (f.isAnnotationPresent(JsonProperty.class)) {
                    return f.getAnnotation(JsonProperty.class).value();
                } else {
                    return null;
                }
            };

    // For some fields we use abbreviation as the json key but use longer
    // words as the field names to save memory and disk space.
    Function<Field, String> fieldToString =
            f -> {
                if (f.isAnnotationPresent(JsonIgnore.class) || f.isSynthetic()) {
                    return null;
                } else {
                    return f.getName();
                }
            };

    @Test
    public void testJsonKeyNames() throws NoSuchFieldException, SecurityException {
        verifyMethodWithJsonKeyNames(
                CacheConfigMetricsCollector.CacheMaxSizeStatus.class,
                CacheConfigDimension.values(),
                CacheConfigValue.values(),
                getMethodJsonProperty);
        verifyMethodWithJsonKeyNames(
                CircuitBreakerCollector.CircuitBreakerStatus.class,
                CircuitBreakerDimension.values(),
                CircuitBreakerValue.values(),
                getMethodJsonProperty);
        verifyMethodWithJsonKeyNames(
                HeapStatus.class,
                HeapDimension.values(),
                HeapValue.values(),
                getMethodJsonProperty);
        verifyMethodWithJsonKeyNames(
                DiskMetrics.class,
                DiskDimension.values(),
                DiskValue.values(),
                getMethodJsonProperty);
        verifyMethodWithJsonKeyNames(
                TCPStatus.class, TCPDimension.values(), TCPValue.values(), getMethodJsonProperty);
        verifyMethodWithJsonKeyNames(
                NetInterfaceSummary.class,
                IPDimension.values(),
                IPValue.values(),
                getMethodJsonProperty);
        verifyMethodWithJsonKeyNames(
                ThreadPoolMetricsCollector.ThreadPoolStatus.class,
                ThreadPoolDimension.values(),
                ThreadPoolValue.values(),
                getMethodJsonProperty);
        verifyMethodWithJsonKeyNames(
                NodeStatsAllShardsMetricsCollector.NodeStatsMetricsAllShardsPerCollectionStatus
                        .class,
                new MetricDimension[] {},
                ShardStatsValue.values(),
                getMethodJsonProperty);
        verifyMethodWithJsonKeyNames(
                NodeStatsFixedShardsMetricsCollector.NodeStatsMetricsFixedShardsPerCollectionStatus
                        .class,
                new MetricDimension[] {},
                ShardStatsValue.values(),
                getMethodJsonProperty);
        verifyNodeDetailJsonKeyNames();
    }

    private void verifyMethodWithJsonKeyNames(
            Class<? extends MetricStatus> statusBean,
            MetricDimension[] dimensions,
            MetricValue[] metrics,
            Function<Method, String> toJsonKey) {
        Set<String> jsonKeySet = new HashSet<>();
        Method[] methods = statusBean.getDeclaredMethods();

        for (Method method : methods) {
            String annotationValue = toJsonKey.apply(method);
            if (annotationValue != null) {
                jsonKeySet.add(annotationValue);
            }
        }

        assertTrue(dimensions.length + metrics.length >= jsonKeySet.size());

        for (MetricDimension d : dimensions) {
            assertTrue(
                    String.format("We need %s", d.toString()), jsonKeySet.contains(d.toString()));
            jsonKeySet.remove(d.toString());
        }

        Set<String> s = new HashSet<>();
        for (MetricValue m : metrics) {
            s.add(m.toString());
        }
        for (String v : jsonKeySet) {
            assertTrue(String.format("We need %s", v), s.contains(v));
        }
    }

    private void verifyFieldWithJsonKeyNames(
            Class<? extends MetricStatus> statusBean,
            MetricDimension[] dimensions,
            MetricValue[] metrics,
            Function<Field, String> toJsonKey) {
        Set<String> jsonKeySet = new HashSet<>();
        Field[] fields = statusBean.getDeclaredFields();

        for (Field field : fields) {
            String annotationValue = toJsonKey.apply(field);
            if (annotationValue != null) {
                jsonKeySet.add(annotationValue);
            }
        }

        assertTrue(dimensions.length + metrics.length == jsonKeySet.size());

        for (MetricDimension d : dimensions) {
            assertTrue(
                    String.format("We need %s", d.toString()), jsonKeySet.contains(d.toString()));
        }

        for (MetricValue v : metrics) {
            assertTrue(
                    String.format("We need %s", v.toString()), jsonKeySet.contains(v.toString()));
        }
    }

    private void verifyNodeDetailJsonKeyNames() {
        Set<String> jsonKeySet = new HashSet<>();
        Set<String> nodeDetailColumnSet = new HashSet<>();
        Method[] methods = NodeDetailsCollector.NodeDetailsStatus.class.getDeclaredMethods();

        for (Method method : methods) {
            String annotationValue = getMethodJsonProperty.apply(method);
            if (annotationValue != null) {
                jsonKeySet.add(annotationValue);
            }
        }

        AllMetrics.NodeDetailColumns[] columns = AllMetrics.NodeDetailColumns.values();
        for (AllMetrics.NodeDetailColumns d : columns) {
            nodeDetailColumnSet.add(d.toString());
        }

        // The _cat/master fix might not be backport to all PA versions in brazil
        // So not all domains has the IS_MASTER_NODE field in NodeDetailsStatus
        // change this assert statement to support backward compatibility
        assertTrue(
                nodeDetailColumnSet.size() == jsonKeySet.size()
                        || nodeDetailColumnSet.size() - 1 == jsonKeySet.size());

        for (String key : jsonKeySet) {
            assertTrue(String.format("We need %s", key), nodeDetailColumnSet.contains(key));
        }
    }
}
