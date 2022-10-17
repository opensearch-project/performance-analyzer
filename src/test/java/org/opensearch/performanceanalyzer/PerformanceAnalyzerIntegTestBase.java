/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpStatus;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.impl.async.HttpAsyncClientBuilder;
import org.apache.hc.core5.http.message.BasicHeader;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.opensearch.client.RestClientBuilder;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.unit.TimeValue;
import org.opensearch.common.util.concurrent.ThreadContext;
import org.opensearch.performanceanalyzer.config.setting.PerformanceAnalyzerClusterSettings;
import org.opensearch.performanceanalyzer.config.setting.handler.PerformanceAnalyzerClusterSettingHandler;
import org.opensearch.performanceanalyzer.util.WaitFor;
import org.opensearch.test.rest.OpenSearchRestTestCase;

public abstract class PerformanceAnalyzerIntegTestBase extends OpenSearchRestTestCase {
    private static final Logger LOG = LogManager.getLogger(PerformanceAnalyzerIntegTestBase.class);
    private int paPort;
    protected static final ObjectMapper mapper = new ObjectMapper();
    // TODO this must be initialized at construction time to avoid NPEs, we should find a way for
    // subclasses to override this
    protected ITConfig config = new ITConfig();
    protected static RestClient paClient;
    protected static final String METHOD_GET = "GET";
    protected static final String METHOD_POST = "POST";

    // Don't wipe the cluster after test completion
    @Override
    protected boolean preserveClusterUponCompletion() {
        return true;
    }

    protected boolean isHttps() {
        return config.isHttps();
    }

    @Override
    protected String getProtocol() {
        if (isHttps()) {
            return "https";
        }
        return super.getProtocol();
    }

    protected RestClient buildBasicClient(Settings settings, HttpHost[] hosts) throws Exception {
        final RestClient[] restClientArr = new RestClient[1];
        try {
            WaitFor.waitFor(
                    () -> {
                        try {
                            restClientArr[0] = super.buildClient(settings, hosts);
                        } catch (Exception e) {
                            logger.debug(
                                    "Error building RestClient against hosts {}: {}", hosts, e);
                            return false;
                        }
                        return true;
                    },
                    1,
                    TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new IOException(e);
        }
        return restClientArr[0];
    }

    protected RestClient buildClient(Settings settings, HttpHost[] hosts) throws IOException {
        RestClientBuilder builder = RestClient.builder(hosts);
        if (isHttps()) {
            LOG.info("Setting up https client");
            configureHttpsClient(builder, settings);
        } else {
            configureClient(builder, settings);
        }
        builder.setStrictDeprecationMode(true);
        return builder.build();
    }

    public static Map<String, String> buildDefaultHeaders(Settings settings) {
        Settings headers = ThreadContext.DEFAULT_HEADERS_SETTING.get(settings);
        if (headers == null) {
            return Collections.emptyMap();
        } else {
            Map<String, String> defaultHeader = new HashMap<>();
            for (String key : headers.names()) {
                defaultHeader.put(key, headers.get(key));
            }
            return Collections.unmodifiableMap(defaultHeader);
        }
    }

    protected void configureHttpsClient(RestClientBuilder builder, Settings settings) {
        Map<String, String> headers = buildDefaultHeaders(settings);
        Header[] defaultHeaders = new Header[headers.size()];
        int i = 0;
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            defaultHeaders[i++] = new BasicHeader(entry.getKey(), entry.getValue());
        }
        builder.setDefaultHeaders(defaultHeaders);
        builder.setHttpClientConfigCallback(
                (HttpAsyncClientBuilder httpClientBuilder) -> {
                    CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
                    credentialsProvider.setCredentials(
                            AuthScope.ANY,
                            new UsernamePasswordCredentials(
                                    config.getUser(), config.getPassword()));
                    try {
                        return httpClientBuilder
                                .setDefaultCredentialsProvider(credentialsProvider)
                                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                                .setSSLContext(
                                        SSLContextBuilder.create()
                                                .loadTrustMaterial(
                                                        null,
                                                        (X509Certificate[] chain,
                                                                String authType) -> true)
                                                .build());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
        String socketTimeoutString = settings.get(CLIENT_SOCKET_TIMEOUT);
        if (socketTimeoutString == null) {
            socketTimeoutString = "60s";
            TimeValue socketTimeout =
                    TimeValue.parseTimeValue(socketTimeoutString, CLIENT_SOCKET_TIMEOUT);
            builder.setRequestConfigCallback(
                    (RequestConfig.Builder conf) ->
                            conf.setSocketTimeout(Math.toIntExact(socketTimeout.millis())));
            if (settings.hasValue(CLIENT_PATH_PREFIX)) {
                builder.setPathPrefix(settings.get(CLIENT_PATH_PREFIX));
            }
        }
    }

    protected List<HttpHost> getHosts(int port) {
        String cluster = config.getRestEndpoint();
        logger.info("Cluster is {}", cluster);
        if (cluster == null) {
            throw new RuntimeException(
                    "Must specify [tests.rest.cluster] system property with a comma delimited list of [host:port] "
                            + "to which to send REST requests");
        }
        return Collections.singletonList(
                new HttpHost(cluster.substring(0, cluster.lastIndexOf(":")), port, "http"));
    }

    @Before
    public void setupIT() throws Exception {
        paPort = config.getPaPort();
        List<HttpHost> hosts = getHosts(paPort);
        logger.info("initializing PerformanceAnalyzer client against {}", hosts);
        paClient = buildBasicClient(restClientSettings(), hosts.toArray(new HttpHost[0]));
    }

    private enum Component {
        PA,
        RCA
    }

    /**
     * modifyComponent enables/disables PA or RCA on the test cluster
     *
     * @param component Either PA or RCA
     * @return The cluster's {@link Response}
     */
    public Response modifyComponent(String base_uri, Component component, boolean enabled)
            throws Exception {
        String endpoint;
        switch (component) {
            case PA:
                endpoint = base_uri + "/cluster/config";
                break;
            case RCA:
                endpoint = base_uri + "/rca/cluster/config";
                break;
            default:
                throw new IllegalArgumentException(
                        "Unrecognized component value " + component.toString());
        }
        Request request = new Request("POST", endpoint);
        if (enabled) {
            request.setJsonEntity("{\"enabled\": true}");
        } else {
            request.setJsonEntity("{\"enabled\": false}");
        }
        return client().performRequest(request);
    }

    /**
     * ensurePaAndRcaEnabled makes a best effort to enable PA and RCA on the test OpenSearch cluster
     *
     * @throws Exception If the function is unable to enable PA and RCA
     */
    public void ensurePaAndRcaEnabled(String base_uri) throws Exception {
        // Attempt to enable PA and RCA on the cluster
        WaitFor.waitFor(
                () -> {
                    try {
                        Response paResp = modifyComponent(base_uri, Component.PA, true);
                        Response rcaResp = modifyComponent(base_uri, Component.RCA, true);
                        return paResp.getStatusLine().getStatusCode() == HttpStatus.SC_OK
                                && rcaResp.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
                    } catch (Exception e) {
                        return false;
                    }
                },
                1,
                TimeUnit.MINUTES);

        // Sanity check that PA and RCA are enabled on the cluster
        Response resp = client().performRequest(new Request("GET", base_uri + "/cluster/config"));
        Assert.assertEquals(resp.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
        Map<String, Object> respMap =
                mapper.readValue(
                        EntityUtils.toString(resp.getEntity(), "UTF-8"),
                        new TypeReference<Map<String, Object>>() {});
        Integer state = (Integer) respMap.get("currentPerformanceAnalyzerClusterState");
        Assert.assertTrue(
                "PA and RCA are not enabled on the target cluster!",
                PerformanceAnalyzerClusterSettingHandler.checkBit(
                                state,
                                PerformanceAnalyzerClusterSettings.PerformanceAnalyzerFeatureBits
                                        .PA_BIT
                                        .ordinal())
                        && PerformanceAnalyzerClusterSettingHandler.checkBit(
                                state,
                                PerformanceAnalyzerClusterSettings.PerformanceAnalyzerFeatureBits
                                        .RCA_BIT
                                        .ordinal()));
    }

    /**
     * ensurePAStatus makes a best effort to enable/disable PA the test OpenSearch cluster
     *
     * @throws Exception If the function is unable to enable PA
     */
    public void ensurePAStatus(String base_uri, boolean enabled) throws Exception {
        // Attempt to enable PA and RCA on the cluster
        WaitFor.waitFor(
                () -> {
                    try {
                        Response paResp = modifyComponent(base_uri, Component.PA, enabled);
                        return paResp.getStatusLine().getStatusCode() == HttpStatus.SC_OK;
                    } catch (Exception e) {
                        return false;
                    }
                },
                1,
                TimeUnit.MINUTES);
        // Wait for cluster to propagate PA state across nodes
        Thread.sleep(2_000);
        checkPAEnabled(base_uri, enabled);
    }

    /**
     * checkPAEnabled makes a best effort to check if PA is enabled on the test OpenSearch cluster
     *
     * @throws Exception If the cluster is unable to enable PA
     */
    public void checkPAEnabled(String base_uri, boolean enabled) throws Exception {
        // Sanity check that PA is enabled on the cluster
        Response resp = client().performRequest(new Request("GET", base_uri + "/cluster/config"));
        Assert.assertEquals(resp.getStatusLine().getStatusCode(), HttpStatus.SC_OK);
        Map<String, Object> respMap =
                mapper.readValue(
                        EntityUtils.toString(resp.getEntity(), "UTF-8"),
                        new TypeReference<Map<String, Object>>() {});
        Integer state = (Integer) respMap.get("currentPerformanceAnalyzerClusterState");
        Assert.assertEquals(
                enabled,
                PerformanceAnalyzerClusterSettingHandler.checkBit(
                        state,
                        PerformanceAnalyzerClusterSettings.PerformanceAnalyzerFeatureBits.PA_BIT
                                .ordinal()));
    }

    @After
    public void closePaClient() throws Exception {
        OpenSearchRestTestCase.closeClients();
        paClient.close();
        LOG.debug("AfterClass has run");
    }

    protected static class TestUtils {
        public static final String DATA = "data";
        public static final String RECORDS = "records";

        // Field related strings
        public static final String FIELDS = "fields";
        public static final String FIELD_NAME = "name";
        public static final String FIELD_TYPE = "type";
        public static final String DOUBLE_TYPE = "DOUBLE";

        // Metrics related strings
        public static final String M_DISKUTIL = "Disk_Utilization";
    }
}
