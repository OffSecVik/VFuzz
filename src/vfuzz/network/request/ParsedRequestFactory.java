package vfuzz.network.request;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import vfuzz.config.ConfigAccessor;
import vfuzz.operations.RandomAgent;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

/**
 * The {@code ParsedRequestFactory} class is responsible for creating HTTP requests based on
 * a pre-defined HTTP request template parsed from a file. This factory allows for custom
 * requests to be dynamically generated using fuzzing payloads that are injected into the
 * parsed request.
 *
 * <p>It implements the {@link WebRequestFactory} interface, providing the ability to build
 * requests that replace the fuzz marker with a specified payload. This is particularly useful
 * for more complex or customized requests that may be used in fuzzing scenarios.
 */
public class ParsedRequestFactory implements WebRequestFactory {

    private final ParsedHttpRequest prototypeRequest;


    /**
     * Constructs a new {@code ParsedRequestFactory} by reading and parsing an HTTP request
     * template from a file.
     *
     * <p>The file path for the HTTP request is retrieved from the configuration, and the
     * {@link ParsedHttpRequest} object is initialized by parsing the content of the file.
     * If the file cannot be read, a {@link RuntimeException} is thrown.
     */
    public ParsedRequestFactory() {
        try {
            prototypeRequest = new ParsedHttpRequest().parseHttpRequestFromFile(ConfigAccessor.getConfigValue("requestFilePath", String.class));
        } catch (IOException e) {
            throw new RuntimeException("There was an error parsing the request from the file:\n" + e.getMessage());
        }
    }

    /**
     * Builds an HTTP request by injecting a fuzzing payload into a pre-parsed HTTP request
     * template. This method clones the prototype request, replaces the fuzz marker with the
     * provided payload, and then constructs a new {@link HttpRequestBase} object accordingly.
     *
     * @param payload The fuzzing payload to inject into the request.
     * @return A fully-constructed {@link HttpRequestBase} object with the fuzzing payload inserted.
     */
    @Override
    public HttpRequestBase buildRequest(String payload) {
        ParsedHttpRequest rawCopy = new ParsedHttpRequest(prototypeRequest);
        return buildRequestFromFile(rawCopy, payload);
    }

    /**
     * Builds an HTTP request from the parsed template by injecting a fuzzing payload into
     * the request and setting the appropriate headers and HTTP method (GET, POST, or HEAD).
     *
     * <p>The method handles URL encoding of the payload, dynamically sets the request method,
     * inserts the payload, and applies headers from the parsed request template. Additionally,
     * it sets a random user-agent if enabled in the configuration.
     *
     * @param parsedRequest The parsed request template used to construct the HTTP request.
     * @param payload       The fuzzing payload to inject into the request.
     * @return A {@link HttpRequestBase} object representing the complete HTTP request.
     */
    public HttpRequestBase buildRequestFromFile(ParsedHttpRequest parsedRequest, String payload) {
        try {
            String encodedPayload = URLEncoder.encode(payload, StandardCharsets.UTF_8); // urlencoding, some wordlists have weird payloads
            parsedRequest.replaceFuzzMarker(encodedPayload); // injecting the payload into the request // TODO: OPTIONAL: could avoid making deep copies of the parsedRequest in QueueConsumer if we found a way to parse for FUZZ AFTER extracting the data from the parsedRequest. This would likely involve making a method in this class right here or checking for FUZZ every time we read data from the request

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
