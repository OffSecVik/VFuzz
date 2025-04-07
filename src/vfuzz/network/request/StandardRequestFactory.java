package vfuzz.network.request;

import org.apache.http.client.methods.HttpRequestBase;
import vfuzz.config.ConfigAccessor;
import vfuzz.core.ArgParse;
import vfuzz.core.PayloadGenerator;
import vfuzz.except.RequestBuildingException;
import vfuzz.except.controlflow.PayloadGenerationFinishedException;
import vfuzz.network.strategy.requestmethod.*;
import vfuzz.network.strategy.requestmode.*;
import vfuzz.operations.RandomAgent;
import vfuzz.operations.Target;

import java.net.URI;
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
public class StandardRequestFactory extends WebRequestFactory {

    private static RequestModeStrategy requestModeStrategy;
    private static RequestMethodStrategy requestMethodStrategy;

    private final boolean isUserAgentRandomizationEnabled;
    private final String targetUrl;
    private final PayloadGenerator payloadGenerator;
    private String[] fileExtensions = null;
    private int fileExtensionIndex = 0;

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


    public StandardRequestFactory(Target target) {
        super();
        this.targetUrl = target.getUrl();
        this.payloadGenerator = new PayloadGenerator(target.getWordlistReaders());
        buildPrototypeRequest();
        isUserAgentRandomizationEnabled = ConfigAccessor.getConfigValue("randomAgent", Boolean.class);

        if (ConfigAccessor.getConfigValue("fileExtensions", String.class) != null) {
            fileExtensions = ConfigAccessor.getConfigValue("fileExtensions", String.class).split(",");
        }

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
                    if (parts[0].equals("Content-Type")) {
                        continue;
                    }
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

    private HttpRequestBase buildRequestWithFileExtensions() throws PayloadGenerationFinishedException {
        if (getPayloads().isEmpty()) {
            setPayloads(payloadGenerator.generatePayloads());
        }
        if (fileExtensionIndex == fileExtensions.length) {
            fileExtensionIndex = 0;
            setPayloads(payloadGenerator.generatePayloads());
        }
        String payload = getPayloads().get(0);

        currentPayloads.clear();
        currentPayloads.add(payload);

        String extension = fileExtensions[fileExtensionIndex];

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

            String uri = String.valueOf(clonedRequest.getURI());
            clonedRequest.setURI(URI.create(uri + extension));
            fileExtensionIndex++;
            return clonedRequest;

        } catch (Exception e) {
            throw new RequestBuildingException(e.getMessage(), e.getCause());
        }
    }

    private HttpRequestBase buildRequestWithoutFileExtensions() throws PayloadGenerationFinishedException {
        setPayloads(payloadGenerator.generatePayloads());
        String payload = getPayloads().get(0);

        currentPayloads.clear();
        currentPayloads.add(payload);
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

        } catch (Exception e) {
            throw new RequestBuildingException(e.getMessage(), e.getCause());
        }
    }

    /**
     * Builds a customized HTTP request by injecting a fuzzing payload into the URL.
     *
     * <p>This method encodes the payload, clones the prototype request, and modifies the
     * request according to the selected request mode (e.g., VHOST, SUBDOMAIN). Additionally,
     * it randomizes the User-Agent header if that feature is enabled in the configuration.
     *
     * @return A {@link HttpRequestBase} object representing the fully configured HTTP request.
     */
    @Override
    public HttpRequestBase buildRequest() throws RequestBuildingException, PayloadGenerationFinishedException {

        if (fileExtensions != null) {
            return buildRequestWithFileExtensions();
        } else {
            return buildRequestWithoutFileExtensions();
        }
    }
}
