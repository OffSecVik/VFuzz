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

    /**
     * Modifies the given HTTP request by replacing the fuzz marker in the
     * request URL with the fuzzing payload.
     *
     * <p>This method replaces the first occurrence of the fuzz marker in the
     * URL with the provided payload, constructing a new URL that is used for
     * the HTTP request.
     *
     * @param request    The {@link HttpRequestBase} object representing the HTTP request to be modified.
     * @param requestUrl The original URL containing the fuzz marker.
     * @param payload    The fuzzing payload that will replace the fuzz marker in the URL.
     * @throws URISyntaxException If the modified URL is invalid or malformed.
     */
    @Override
    public void modifyRequest(HttpRequestBase request, String requestUrl, String payload) throws URISyntaxException {
        if ("POST".equals(ConfigAccessor.getConfigValue("requestMethod", String.class))) {
            try {
                byte[] contentBytes = ((HttpPost) request).getEntity().getContent().readAllBytes();
                String content = new String(contentBytes, StandardCharsets.UTF_8).replaceFirst(fuzzMarker, payload);

                if (contentType != null) {
                    ((HttpPost) request).setEntity(new StringEntity(content, contentType));
                } else {
                    ((HttpPost) request).setEntity(new StringEntity(content));
                }

            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            request.setURI(new URI(requestUrl));
            return;
        }
        request.setURI(new URI(requestUrl.replaceFirst(fuzzMarker, payload)));
    }
}
