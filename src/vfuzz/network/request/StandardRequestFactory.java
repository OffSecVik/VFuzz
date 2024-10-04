package vfuzz.network.request;

import org.apache.http.client.methods.HttpRequestBase;
import vfuzz.config.ConfigAccessor;
import vfuzz.core.ArgParse;
import vfuzz.network.strategy.requestmethod.*;
import vfuzz.network.strategy.requestmode.*;
import vfuzz.operations.RandomAgent;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * The {@code StandardRequestFactory} class is responsible for constructing
 * and configuring HTTP requests according to the configured request mode and method.
 *
 * <p>This class implements the {@link WebRequestFactory} interface and provides
 * a flexible way to create HTTP requests based on different strategies for both
 * HTTP request methods (e.g., GET, POST) and request modes (e.g., VHOST, SUBDOMAIN).
 * It supports dynamically setting request headers, user agents, and cookies from
 * configurations and can handle payloads that are injected into the URL for fuzzing purposes.
 */
public class StandardRequestFactory implements WebRequestFactory {

    private static RequestModeStrategy requestModeStrategy;
    private static RequestMethodStrategy requestMethodStrategy;

    private final boolean isUserAgentRandomizationEnabled;
    private final String targetUrl;

    private HttpRequestBase prototypeRequest;

    static {
        switch (ConfigAccessor.getConfigValue("requestMode", RequestMode.class)) {
            case STANDARD -> requestModeStrategy = new RequestModeStrategyStandard();
            case FUZZ -> requestModeStrategy = new RequestModeStrategyFuzz();
            case VHOST -> requestModeStrategy = new RequestModeStrategyVhost();
            case SUBDOMAIN -> requestModeStrategy = new RequestModeStrategySubdomain();
        }
        switch (ConfigAccessor.getConfigValue("requestMethod", RequestMethod.class)) {
            case GET -> requestMethodStrategy = new RequestMethodStrategyGET();
            case HEAD -> requestMethodStrategy = new RequestMethodStrategyHEAD();
            case POST -> requestMethodStrategy = new RequestMethodStrategyPOST();
        }
    }

    /**
     * Constructs a new {@code StandardRequestFactory} for a given target URL.
     *
     * <p>This constructor builds a prototype request that can be cloned and customized
     * for individual fuzzing payloads. It also checks the configuration for user-agent
     * randomization settings.
     *
     * @param targetUrl The base URL of the target that will be fuzzed.
     */
    public StandardRequestFactory(String targetUrl) {
        this.targetUrl = targetUrl;
        buildPrototypeRequest();
        isUserAgentRandomizationEnabled = ConfigAccessor.getConfigValue("randomAgent", Boolean.class);

    }

    /**
     * Builds a prototype HTTP request based on the configured request method.
     *
     * <p>This method initializes the request with the configured HTTP method (GET, POST, etc.)
     * and applies headers, user-agent, and cookies based on the configuration.
     */
    public void buildPrototypeRequest() {
        prototypeRequest = requestMethodStrategy.createPrototypeRequest();
        setUpHeaders();
        setUpUserAgent();
        setUpCookies();
    }

    /**
     * Sets up request headers from the configuration.
     */
    private void setUpHeaders() {
        if (!ArgParse.getHeaders().isEmpty()) {
            for (String header : ArgParse.getHeaders()) {
                String[] parts = header.split(":", 2);
                if (parts.length == 2) {
                    prototypeRequest.setHeader(parts[0].trim(), parts[1].trim());
                } else {
                    System.err.println("Invalid header format while building request: " + header);
                }
            }
        }
    }

    /**
     * Sets up the User-Agent header from the configuration if specified.
     */
    private void setUpUserAgent() {
        if (ConfigAccessor.getConfigValue("userAgent", String.class) != null) {
            prototypeRequest.setHeader("User-Agent", ConfigAccessor.getConfigValue("userAgent", String.class));
        }
    }

    /**
     * Sets up cookies from the configuration if specified.
     */
    private void setUpCookies() {
        if (ConfigAccessor.getConfigValue("cookies", String.class) != null) {
            prototypeRequest.setHeader("Cookie", ConfigAccessor.getConfigValue("cookies", String.class));
        }
    }

    /**
     * Builds a customized HTTP request by injecting a fuzzing payload into the URL.
     *
     * <p>This method encodes the payload, clones the prototype request, and modifies the
     * request according to the selected request mode (e.g., VHOST, SUBDOMAIN). Additionally,
     * it randomizes the User-Agent header if that feature is enabled in the configuration.
     *
     * @param payload The fuzzing payload to be injected into the URL.
     * @return A {@link HttpRequestBase} object representing the fully configured HTTP request.
     */
    @Override
    public HttpRequestBase buildRequest(String payload) {
        try {
            String encodedPayload = URLEncoder.encode(payload, StandardCharsets.UTF_8);

            if (!payload.equals(encodedPayload)) {
                payload = encodedPayload;
            }

            HttpRequestBase clonedRequest = requestMethodStrategy.cloneRequest(prototypeRequest);

            requestModeStrategy.modifyRequest(clonedRequest, targetUrl, payload);

            if (isUserAgentRandomizationEnabled) {
                clonedRequest.setHeader("User-Agent", RandomAgent.get());
            }

            return clonedRequest;

        } catch (IllegalArgumentException e) {
            System.err.println("Invalid URI: " + e.getMessage());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

        return null;
    }
}
