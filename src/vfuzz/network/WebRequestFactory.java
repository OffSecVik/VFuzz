package vfuzz.network;

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


public class WebRequestFactory {

    private static RequestModeStrategy requestModeStrategy;
    private static RequestMethodStrategy requestMethodStrategy;

    private final boolean isUserAgentRandomizationEnabled;

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

    public WebRequestFactory() {
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

    public HttpRequestBase buildRequest(String requestUrl, String payload) {
        try {
            String encodedPayload = URLEncoder.encode(payload, StandardCharsets.UTF_8);

            if (!payload.equals(encodedPayload)) {
                payload = encodedPayload;
            }

            HttpRequestBase clonedRequest = requestMethodStrategy.cloneRequest(prototypeRequest);

            requestModeStrategy.modifyRequest(clonedRequest, requestUrl, payload);

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

    public HttpRequestBase buildRequestFromFile(ParsedHttpRequest parsedRequest, String payload) {
        try {
            String encodedPayload = URLEncoder.encode(payload, StandardCharsets.UTF_8); // urlencoding, some wordlists have weird payloads
            parsedRequest.replaceFuzzMarker(encodedPayload); // injecting the payload into the request // TODO: OPTIONAL: could avoid making deep copies of the parsedRequest in QueueConsumer if we found a way to parse for FUZZ AFTER extracting the data from the parsedRequest. This would likely involve making a method in this class right here or checking for FUZZ every time we read data from the request
            // String encodedPayload = URLEncoder.encode(payload, StandardCharsets.UTF_8);
            HttpRequestBase request = null;
            String requestUrl = parsedRequest.getUrl();
            // requestUrl = requestUrl.endsWith("/") ? requestUrl : requestUrl + "/"; // TODO: Take care of duplicate due to backslashes another way, this is a little janky

            // set request method
            switch (parsedRequest.getMethod().toUpperCase()) {
                case "GET" -> request = new HttpGet(requestUrl);
                case "HEAD" -> request = new HttpHead(requestUrl);
                case "POST" -> {
                    HttpPost postRequest = new HttpPost(requestUrl);
                    postRequest.setEntity(new StringEntity(parsedRequest.getBody())); // TODO: check if POST body is preserved, handle content-length dynamically based on payload length
                    request = postRequest;
                }
            }

            // set up headers
            for (Map.Entry<String, String>entry : parsedRequest.getHeaders().entrySet()) {
                Objects.requireNonNull(request).setHeader(entry.getKey(), entry.getValue());
            }

            if (ConfigAccessor.getConfigValue("randomAgent", Boolean.class)) {
                Objects.requireNonNull(request).setHeader("User-Agent", RandomAgent.get());
            }

            return request;

        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
