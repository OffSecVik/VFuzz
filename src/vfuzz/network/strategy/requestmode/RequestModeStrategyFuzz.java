package vfuzz.network.strategy.requestmode;

import org.apache.http.Header;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import vfuzz.config.ConfigAccessor;
import vfuzz.core.ArgParse;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;

/**
 * The {@code RequestModeStrategyFuzz} class is a concrete implementation of
 * {@link RequestModeStrategy} designed to handle requests in the "fuzzing" mode.
 *
 * <p>In fuzzing mode, this strategy modifies the request URL by replacing
 * a predefined marker (the "fuzz marker") with a fuzzing payload. This allows
 * the fuzzer to test different payloads dynamically at runtime by injecting
 * them into the URL where the marker is found.
 */
public class RequestModeStrategyFuzz extends RequestModeStrategy {

    private final String fuzzMarker;
    private final ContentType contentType;

    /**
     * Constructs a new {@code RequestModeStrategyFuzz} by retrieving the
     * fuzz marker from the configuration. The fuzz marker is the placeholder
     * in the URL that will be replaced with fuzzing payloads.
     */
    public RequestModeStrategyFuzz() {
        fuzzMarker = ConfigAccessor.getConfigValue("fuzzMarker", String.class);
        contentType = ArgParse.getContentType();
    }

    @Override
    public void modifyRequest(HttpRequestBase request, String requestUrl, List<String> payloads) throws URISyntaxException {

        if ("POST".equalsIgnoreCase(ConfigAccessor.getConfigValue("requestMethod", String.class))) {
            try {
                HttpPost postRequest = (HttpPost) request;
                byte[] contentBytes = postRequest.getEntity().getContent().readAllBytes();
                String content = new String(contentBytes, StandardCharsets.UTF_8);

                for (String payload : payloads) {
                    if (requestUrl.contains(fuzzMarker)) {
                        requestUrl = requestUrl.replaceFirst(fuzzMarker, Matcher.quoteReplacement(payload));
                        continue;
                    }

                    if (content.contains(fuzzMarker)) {
                        content = content.replaceFirst(fuzzMarker, Matcher.quoteReplacement(payload));
                    }
                }

                if (contentType != null) {
                    postRequest.setEntity(new StringEntity(content, contentType));
                } else {
                    postRequest.setEntity(new StringEntity(content));
                }

            } catch (Exception e) {
                throw new RuntimeException("Failed to modify POST request body", e);
            }

            request.setURI(new URI(requestUrl)); // leave URL untouched for POST
            return;
        }

        for (String payload : payloads) {
            requestUrl = requestUrl.replaceFirst(fuzzMarker, Matcher.quoteReplacement(payload));
        }
        request.setURI(new URI(requestUrl));
    }
}

