/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.http_action.config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.MockitoAnnotations.initMocks;

import java.io.IOException;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.settings.Settings;
import org.opensearch.performanceanalyzer.config.PluginSettings;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestRequest;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({PluginSettings.class})
@SuppressStaticInitializationFor({"PluginSettings"})
public class PerformanceAnalyzerResourceProviderTests {
    @Mock RestController mockRestController;
    PerformanceAnalyzerResourceProvider performanceAnalyzerRp;

    @Before
    public void setup() {
        initMocks(this);
        initPerformanceAnalyzerResourceProvider(false);
    }

    private void initPerformanceAnalyzerResourceProvider(boolean isHttpsEnabled) {
        PluginSettings config = Mockito.mock(PluginSettings.class);
        Mockito.when(config.getHttpsEnabled()).thenReturn(isHttpsEnabled);

        PowerMockito.mockStatic(PluginSettings.class);
        PowerMockito.when(PluginSettings.instance()).thenReturn(config);

        performanceAnalyzerRp =
                new PerformanceAnalyzerResourceProvider(Settings.EMPTY, mockRestController);
        performanceAnalyzerRp.setPortNumber("9600");
    }

    private RestRequest generateRestRequest(String requestUri, String redirectEndpoint) {
        RestRequest request =
                new RestRequest(
                        null, new HashMap<>(), requestUri, Collections.emptyMap(), null, null) {
                    @Override
                    public Method method() {
                        return Method.GET;
                    }

                    @Override
                    public String uri() {
                        return requestUri;
                    }

                    @Override
                    public boolean hasContent() {
                        return false;
                    }

                    @Override
                    public BytesReference content() {
                        return null;
                    }
                };
        request.params().put("redirectEndpoint", redirectEndpoint);
        return request;
    }

    private void assertAgentUriWithMetricsRedirection(
            final String protocolScheme, String requestBasePath) throws IOException {
        initPerformanceAnalyzerResourceProvider(protocolScheme.equals("https://"));

        String requestURI =
                protocolScheme
                        + "localhost:9200"
                        + requestBasePath
                        + "/_agent/metrics"
                        + "?metrics=Latency,CPU_Utilization&agg=avg,max&dim=ShardID&nodes=all";
        String expectedResponseURI =
                protocolScheme
                        + "localhost:9600"
                        + RestConfig.PA_BASE_URI
                        + "/metrics"
                        + "?metrics=Latency,CPU_Utilization&agg=avg,max&dim=ShardID&nodes=all";

        RestRequest restRequest = generateRestRequest(requestURI, "metrics");
        URL actualResponseURI = performanceAnalyzerRp.getAgentUri(restRequest);
        assertEquals(new URL(expectedResponseURI), actualResponseURI);
    }

    private void assertAgentUriWithRcaRedirection(
            final String protocolScheme, String requestBasePath) throws IOException {
        initPerformanceAnalyzerResourceProvider(protocolScheme.equals("https://"));

        String requestUri =
                protocolScheme
                        + "localhost:9200"
                        + requestBasePath
                        + "/_agent/rca"
                        + "?rca=highShardCPU&startTime=2019-10-11";
        String expectedResponseUri =
                protocolScheme
                        + "localhost:9600"
                        + RestConfig.PA_BASE_URI
                        + "/rca"
                        + "?rca=highShardCPU&startTime=2019-10-11";

        RestRequest restRequest = generateRestRequest(requestUri, "rca");
        URL actualResponseURI = performanceAnalyzerRp.getAgentUri(restRequest);
        assertEquals(new URL(expectedResponseUri), actualResponseURI);
    }

    private void assertAgentUriWithBatchRedirection(
            final String protocolScheme, String requestBasePath) throws IOException {
        initPerformanceAnalyzerResourceProvider(protocolScheme.equals("https://"));

        String requestUri =
                protocolScheme
                        + "localhost:9200"
                        + requestBasePath
                        + "/_agent/batch"
                        + "?metrics=CPU_Utilization,IO_TotThroughput&starttime=1594412650000&endtime=1594412665000&samplingperiod=5";
        String expectedResponseUri =
                protocolScheme
                        + "localhost:9600"
                        + RestConfig.PA_BASE_URI
                        + "/batch"
                        + "?metrics=CPU_Utilization,IO_TotThroughput&starttime=1594412650000&endtime=1594412665000&samplingperiod=5";

        RestRequest restRequest = generateRestRequest(requestUri, "batch");
        URL actualResponseURI = performanceAnalyzerRp.getAgentUri(restRequest);
        assertEquals(new URL(expectedResponseUri), actualResponseURI);
    }

    private void assertAgentUriWithActionsRedirection(
            final String protocolScheme, String requestBasePath) throws IOException {
        initPerformanceAnalyzerResourceProvider(protocolScheme.equals("https://"));

        String requestUri = protocolScheme + "localhost:9200" + requestBasePath + "/_agent/actions";
        String expectedResponseUri =
                protocolScheme + "localhost:9600" + RestConfig.PA_BASE_URI + "/actions";

        RestRequest restRequest = generateRestRequest(requestUri, "actions");
        URL actualResponseURI = performanceAnalyzerRp.getAgentUri(restRequest);
        assertEquals(new URL(expectedResponseUri), actualResponseURI);
    }

    @Test
    public void testGetAgentUri_WithHttp_WithMetricRedirection() throws Exception {
        assertAgentUriWithMetricsRedirection("http://", RestConfig.PA_BASE_URI);
        assertAgentUriWithMetricsRedirection("http://", RestConfig.LEGACY_PA_BASE_URI);
    }

    @Test
    public void testGetAgentUri_WithHttps_WithMetricRedirection() throws Exception {
        assertAgentUriWithRcaRedirection("https://", RestConfig.PA_BASE_URI);
        assertAgentUriWithRcaRedirection("https://", RestConfig.LEGACY_PA_BASE_URI);
    }

    @Test
    public void testGetAgentUri_WithHttp_WithRcaRedirection() throws Exception {
        assertAgentUriWithRcaRedirection("http://", RestConfig.PA_BASE_URI);
        assertAgentUriWithRcaRedirection("http://", RestConfig.LEGACY_PA_BASE_URI);
    }

    @Test
    public void testGetAgentUri_WithHttps_WithRcaRedirection() throws Exception {
        assertAgentUriWithRcaRedirection("https://", RestConfig.PA_BASE_URI);
        assertAgentUriWithRcaRedirection("https://", RestConfig.LEGACY_PA_BASE_URI);
    }

    @Test
    public void testGetAgentUri_WithHttp_WithBatchRedirection() throws Exception {
        assertAgentUriWithBatchRedirection("http://", RestConfig.PA_BASE_URI);
        assertAgentUriWithBatchRedirection("http://", RestConfig.LEGACY_PA_BASE_URI);
    }

    @Test
    public void testGetAgentUri_WithHttps_WithBatchRedirection() throws Exception {
        assertAgentUriWithBatchRedirection("https://", RestConfig.PA_BASE_URI);
        assertAgentUriWithBatchRedirection("https://", RestConfig.LEGACY_PA_BASE_URI);
    }

    @Test
    public void testGetAgentUri_WithHttp_WithActionsRedirection() throws Exception {
        assertAgentUriWithActionsRedirection("http://", RestConfig.PA_BASE_URI);
        assertAgentUriWithActionsRedirection("http://", RestConfig.LEGACY_PA_BASE_URI);
    }

    @Test
    public void testGetAgentUri_WithHttps_WithActionsRedirection() throws Exception {
        assertAgentUriWithActionsRedirection("https://", RestConfig.PA_BASE_URI);
        assertAgentUriWithActionsRedirection("https://", RestConfig.LEGACY_PA_BASE_URI);
    }

    @Test
    public void testGetAgentUri_WithHttp_WithUnsupportedRedirection() throws Exception {
        String requestUri = "http://localhost:9200/_opendistro/_performanceanalyzer/_agent/invalid";
        RestRequest request = generateRestRequest(requestUri, "invalid");
        URL finalURI = performanceAnalyzerRp.getAgentUri(request);
        assertNull(finalURI);

        requestUri = "http://localhost:9200/_plugins/_performanceanalyzer/_agent/invalid";
        request = generateRestRequest(requestUri, "invalid");
        finalURI = performanceAnalyzerRp.getAgentUri(request);
        assertNull(finalURI);
    }
}
