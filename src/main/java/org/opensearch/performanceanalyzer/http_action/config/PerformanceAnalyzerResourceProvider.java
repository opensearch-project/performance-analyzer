/*
 * Copyright OpenSearch Contributors
 * SPDX-License-Identifier: Apache-2.0
 */

package org.opensearch.performanceanalyzer.http_action.config;


import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableSet;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.AccessControlException;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.opensearch.client.node.NodeClient;
import org.opensearch.common.inject.Inject;
import org.opensearch.common.settings.Settings;
import org.opensearch.performanceanalyzer.config.PluginSettings;
import org.opensearch.rest.BaseRestHandler;
import org.opensearch.rest.BytesRestResponse;
import org.opensearch.rest.RestController;
import org.opensearch.rest.RestRequest;
import org.opensearch.rest.RestRequest.Method;
import org.opensearch.rest.RestResponse;
import org.opensearch.rest.RestStatus;

public class PerformanceAnalyzerResourceProvider extends BaseRestHandler {
    private static final Logger LOG =
            LogManager.getLogger(PerformanceAnalyzerResourceProvider.class);

    private static final int HTTP_CLIENT_CONNECTION_TIMEOUT_MILLIS = 200;
    private static final String AGENT_PATH = RestConfig.PA_BASE_URI + "/_agent/";
    private static final String LEGACY_AGENT_PATH = RestConfig.LEGACY_PA_BASE_URI + "/_agent/";
    private static final String DEFAULT_PORT_NUMBER = "9600";

    private String portNumber;
    private final boolean isHttpsEnabled;
    private static Set<String> SUPPORTED_REDIRECTIONS =
            ImmutableSet.of("rca", "metrics", "batch", "actions");

    private static final List<ReplacedRoute> REPLACED_ROUTES =
            Collections.singletonList(
                    new ReplacedRoute(
                            Method.GET,
                            AGENT_PATH + "{redirectEndpoint}",
                            Method.GET,
                            LEGACY_AGENT_PATH + "{redirectEndpoint}"));

    @Inject
    public PerformanceAnalyzerResourceProvider(Settings settings, RestController controller) {
        PluginSettings pluginSettings = PluginSettings.instance();
        portNumber =
                pluginSettings.getSettingValue("webservice-listener-port", DEFAULT_PORT_NUMBER);
        isHttpsEnabled = pluginSettings.getHttpsEnabled();

        if (isHttpsEnabled) {
            // skip host name verification
            // Create a trust manager that does not validate certificate chains
            TrustManager[] trustAllCerts =
                    new TrustManager[] {
                        new X509TrustManager() {
                            public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                                return null;
                            }

                            public void checkClientTrusted(
                                    X509Certificate[] certs, String authType) {}

                            public void checkServerTrusted(
                                    X509Certificate[] certs, String authType) {}
                        }
                    };

            // Install the all-trusting trust manager
            try {
                SSLContext sc = SSLContext.getInstance("SSL");
                sc.init(null, trustAllCerts, new java.security.SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
            } catch (AccessControlException e) {
                LOG.warn(
                        "SecurityManager forbids setting default SSL Socket Factory...using default settings",
                        e);
            } catch (Exception e) {
                LOG.warn(
                        "Error encountered while initializing SSLContext...using default settings",
                        e);
            }

            // Create all-trusting host name verifier
            HostnameVerifier allHostsValid = (hostname, session) -> true;
            // Attempt to install the all-trusting host verifier
            try {
                HttpsURLConnection.setDefaultHostnameVerifier(allHostsValid);
            } catch (AccessControlException e) {
                LOG.warn(
                        "SecurityManager forbids setting default hostname verifier...using default settings",
                        e);
            } catch (Exception e) {
                LOG.warn(
                        "Error encountered while initializing hostname verifier...using default settings",
                        e);
            }
        }
    }

    public String getName() {
        return "PerformanceAnalyzer_ResourceProvider";
    }

    /** {@inheritDoc} */
    @Override
    public List<Route> routes() {
        return Collections.emptyList();
    }

    @Override
    public List<ReplacedRoute> replacedRoutes() {
        return REPLACED_ROUTES;
    }

    @Override
    protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client)
            throws IOException {
        StringBuilder response = new StringBuilder();
        String inputLine;
        int responseCode;

        URL url = getAgentUri(request);
        // 'url' is null if no correct mapping for input uri is found
        if (url == null) {
            return channel -> {
                RestResponse finalResponse = new BytesRestResponse(RestStatus.NOT_FOUND, "");
                channel.sendResponse(finalResponse);
            };
        } else {
            HttpURLConnection httpURLConnection =
                    isHttpsEnabled ? createHttpsURLConnection(url) : createHttpURLConnection(url);
            // Build Response in buffer
            responseCode = httpURLConnection.getResponseCode();
            InputStream inputStream =
                    (responseCode == HttpsURLConnection.HTTP_OK)
                            ? httpURLConnection.getInputStream()
                            : httpURLConnection.getErrorStream();

            try (BufferedReader in = new BufferedReader(new InputStreamReader(inputStream))) {
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                LOG.debug("Response received - {}", response);
            } catch (Exception ex) {
                LOG.error("Error receiving response for Request Uri {} - {}", request.uri(), ex);
                return channel -> {
                    channel.sendResponse(
                            new BytesRestResponse(
                                    RestStatus.INTERNAL_SERVER_ERROR,
                                    "Encountered error possibly with downstream APIs"));
                };
            }

            RestResponse finalResponse =
                    new BytesRestResponse(
                            RestStatus.fromCode(responseCode), String.valueOf(response));
            LOG.debug("finalResponse: {}", finalResponse);

            return channel -> {
                try {
                    Map<String, List<String>> map = httpURLConnection.getHeaderFields();
                    for (Map.Entry<String, List<String>> entry : map.entrySet()) {
                        finalResponse.addHeader(entry.getKey(), entry.getValue().toString());
                    }
                    // Send Response back to callee
                    channel.sendResponse(finalResponse);
                } catch (Exception ex) {
                    LOG.error("Error sending response", ex);
                    channel.sendResponse(
                            new BytesRestResponse(
                                    RestStatus.INTERNAL_SERVER_ERROR, "Something went wrong"));
                }
            };
        }
    }

    private HttpURLConnection createHttpsURLConnection(URL url) throws IOException {
        HttpsURLConnection httpsURLConnection = (HttpsURLConnection) url.openConnection();
        httpsURLConnection.setConnectTimeout(HTTP_CLIENT_CONNECTION_TIMEOUT_MILLIS);
        return httpsURLConnection;
    }

    private HttpURLConnection createHttpURLConnection(URL url) throws IOException {
        HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
        httpURLConnection.setConnectTimeout(HTTP_CLIENT_CONNECTION_TIMEOUT_MILLIS);
        return httpURLConnection;
    }

    @VisibleForTesting
    void setPortNumber(String portNumber) {
        this.portNumber = portNumber;
    }

    /**
     * Get Agent URI mapping
     *
     * @param request : RestRequest as input with valid URI
     * @return URI of target path
     * @throws IOException for invalid URL
     */
    public URL getAgentUri(RestRequest request) throws IOException {
        String redirectEndpoint = request.param("redirectEndpoint");
        String urlScheme = isHttpsEnabled ? "https://" : "http://";
        String redirectBasePath =
                urlScheme + "localhost:" + portNumber + RestConfig.PA_BASE_URI + "/";
        // Need to register all params in OpenSearch request else opensearch throws
        // illegal_argument_exception
        for (String key : request.params().keySet()) {
            request.param(key);
        }

        // Add Handler whenever add new redirectAgent path
        if (SUPPORTED_REDIRECTIONS.contains(redirectEndpoint)) {
            String uri = null;
            if (request.uri().contains(AGENT_PATH)) {
                uri = redirectBasePath + request.uri().split(AGENT_PATH)[1];
            } else if (request.uri().contains(LEGACY_AGENT_PATH)) {
                uri = redirectBasePath + request.uri().split(LEGACY_AGENT_PATH)[1];
            } else {
                throw new IOException();
            }

            return new URL(uri);
        }
        return null;
    }
}
