package vfuzz.network.request;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import vfuzz.config.ConfigAccessor;
import vfuzz.core.ArgParse;
import vfuzz.network.strategy.requestmethod.*;
import vfuzz.network.strategy.requestmode.*;
import vfuzz.operations.RandomAgent;

import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;


public class StandardRequestFactory implements WebRequestFactory {

    private static RequestModeStrategy requestModeStrategy;
    private static RequestMethodStrategy requestMethodStrategy;

    private final boolean isUserAgentRandomizationEnabled;

    private HttpRequestBase prototypeRequest;

    private final String targetUrl;

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

    public StandardRequestFactory(String targetUrl) {
        this.targetUrl = targetUrl;
        buildPrototypeRequest();
        isUserAgentRandomizationEnabled = ConfigAccessor.getConfigValue("randomAgent", Boolean.class);

    }

    public void buildPrototypeRequest() {
        // initialize request and set HTTP method
        prototypeRequest = requestMethodStrategy.createPrototypeRequest();

        // set up Headers
        if (!ArgParse.getHeaders().isEmpty()) {
            for (String header : ArgParse.getHeaders()) {
                String[] parts = header.split(":", 2); // split at the first colon
                if (parts.length == 2) {
                    prototypeRequest.setHeader(parts[0].trim(), parts[1].trim());
                } else {
                    System.err.println("Invalid header format while building request: " + header);
                }
            }
        }

        // set up User-Agent
        if (ConfigAccessor.getConfigValue("userAgent", String.class) != null) {
            prototypeRequest.setHeader("User-Agent", ConfigAccessor.getConfigValue("userAgent", String.class));
        }

        // set up cookies
        if (ConfigAccessor.getConfigValue("cookies", String.class) != null) {
            prototypeRequest.setHeader("Cookie", ConfigAccessor.getConfigValue("cookies", String.class));
        }
    }

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
