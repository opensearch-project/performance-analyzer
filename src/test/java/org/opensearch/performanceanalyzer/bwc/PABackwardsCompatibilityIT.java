/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.bwc;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.opensearch.common.settings.Settings;
import org.opensearch.performanceanalyzer.PerformanceAnalyzerIntegTestBase;
import org.opensearch.performanceanalyzer.http_action.config.RestConfig;
import org.opensearch.test.rest.OpenSearchRestTestCase;

public class PABackwardsCompatibilityIT extends PerformanceAnalyzerIntegTestBase {
    private static final ClusterType CLUSTER_TYPE =
            ClusterType.parse(System.getProperty("tests.rest.bwcsuite"));
    private static final String CLUSTER_NAME = System.getProperty("tests.clustername");

    @Override
    protected final boolean preserveReposUponCompletion() {
        return true;
    }

    @Override
    protected final Settings restClientSettings() {
        return Settings.builder()
                .put(super.restClientSettings())
                // increase the timeout here to 90 seconds to handle long waits for a green
                // cluster health. the waits for green need to be longer than a minute to
                // account for delayed shards
                .put(OpenSearchRestTestCase.CLIENT_SOCKET_TIMEOUT, "90s")
                .build();
    }

    public enum ClusterType {
        OLD,
        MIXED,
        UPGRADED;

        public static ClusterType parse(final String value) {
            switch (value) {
                case "old_cluster":
                    return OLD;
                case "mixed_cluster":
                    return MIXED;
                case "upgraded_cluster":
                    return UPGRADED;
                default:
                    throw new AssertionError("unknown cluster type: " + value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void testBackwardsCompatibility() throws Exception {
        String uri = getUri();
        Map<String, Map<String, Object>> responseMap =
                (Map<String, Map<String, Object>>) getAsMap(uri).get("nodes");
        for (Map<String, Object> response : responseMap.values()) {
            List<Map<String, Object>> plugins = (List<Map<String, Object>>) response.get("plugins");
            Set<Object> pluginNames =
                    plugins.stream().map(map -> map.get("name")).collect(Collectors.toSet());
            switch (CLUSTER_TYPE) {
                case OLD:
                    Assert.assertTrue(pluginNames.contains("opensearch-performance-analyzer"));
                    ensurePAStatus(RestConfig.PA_BASE_URI, true);
                    ensurePAStatus(RestConfig.PA_BASE_URI, false);
                    break;
                case MIXED:
                    Assert.assertTrue(pluginNames.contains("opensearch-performance-analyzer"));
                    ensurePAStatus(RestConfig.PA_BASE_URI, true);
                    break;
                case UPGRADED:
                    Assert.assertTrue(pluginNames.contains("opensearch-performance-analyzer"));
                    ensurePAStatus(RestConfig.PA_BASE_URI, true);
                    break;
            }
            break;
        }
    }

    private String getUri() {
        switch (CLUSTER_TYPE) {
            case OLD:
                return "_nodes/" + CLUSTER_NAME + "-0/plugins";
            case MIXED:
                String round = System.getProperty("tests.rest.bwcsuite_round");
                if (round.equals("second")) {
                    return "_nodes/" + CLUSTER_NAME + "-1/plugins";
                } else if (round.equals("third")) {
                    return "_nodes/" + CLUSTER_NAME + "-2/plugins";
                } else {
                    return "_nodes/" + CLUSTER_NAME + "-0/plugins";
                }
            case UPGRADED:
                return "_nodes/plugins";
            default:
                throw new AssertionError("unknown cluster type: " + CLUSTER_TYPE);
        }
    }
}
