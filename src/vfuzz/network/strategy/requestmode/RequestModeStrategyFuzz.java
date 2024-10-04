package vfuzz.network.strategy.requestmode;

import org.apache.http.client.methods.HttpRequestBase;
import vfuzz.config.ConfigAccessor;
import java.net.URI;
import java.net.URISyntaxException;

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

    private String fuzzMarker;

    /**
     * Constructs a new {@code RequestModeStrategyFuzz} by retrieving the
     * fuzz marker from the configuration. The fuzz marker is the placeholder
     * in the URL that will be replaced with fuzzing payloads.
     */
    public RequestModeStrategyFuzz() {
        fuzzMarker = ConfigAccessor.getConfigValue("fuzzMarker", String.class);
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
        request.setURI(new URI(requestUrl.replaceFirst(fuzzMarker, payload)));
    }
}
