/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer;

import org.opensearch.client.Client;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.settings.Settings;
import org.opensearch.core.indices.breaker.CircuitBreakerService;
import org.opensearch.env.Environment;
import org.opensearch.indices.IndicesService;
import org.opensearch.threadpool.ThreadPool;

public final class OpenSearchResources {
    public static final OpenSearchResources INSTANCE = new OpenSearchResources();

    private ThreadPool threadPool;
    private CircuitBreakerService circuitBreakerService;
    private ClusterService clusterService;
    private IndicesService indicesService;
    private Settings settings;
    private Environment environment;
    private java.nio.file.Path configPath;
    private String pluginFileLocation;
    private Client client;

    private OpenSearchResources() {
        threadPool = null;
        circuitBreakerService = null;
        clusterService = null;
        settings = null;
        indicesService = null;
        environment = null;
        configPath = null;
        pluginFileLocation = null;
    }

    public void setPluginFileLocation(String pluginFileLocation) {
        this.pluginFileLocation = pluginFileLocation;
    }

    public String getPluginFileLocation() {
        return pluginFileLocation;
    }

    public void setConfigPath(java.nio.file.Path configPath) {
        this.configPath = configPath;
    }

    public java.nio.file.Path getConfigPath() {
        return configPath;
    }

    public Environment getEnvironment() {
        return environment;
    }

    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    public ThreadPool getThreadPool() {
        return threadPool;
    }

    public void setThreadPool(ThreadPool threadPool) {
        this.threadPool = threadPool;
    }

    public CircuitBreakerService getCircuitBreakerService() {
        return circuitBreakerService;
    }

    public void setCircuitBreakerService(CircuitBreakerService circuitBreakerService) {
        this.circuitBreakerService = circuitBreakerService;
    }

    public ClusterService getClusterService() {
        return clusterService;
    }

    public void setClusterService(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public IndicesService getIndicesService() {
        return indicesService;
    }

    public void setIndicesService(IndicesService indicesService) {
        this.indicesService = indicesService;
    }

    public void setClient(final Client client) {
        this.client = client;
    }

    public Client getClient() {
        return client;
    }
}
