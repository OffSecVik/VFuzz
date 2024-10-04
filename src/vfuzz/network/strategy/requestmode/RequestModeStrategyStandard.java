package vfuzz.network.strategy.requestmode;

import org.apache.http.client.methods.HttpRequestBase;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * The {@code RequestModeStrategyStandard} class is a concrete implementation of
 * {@link RequestModeStrategy} for handling requests in standard mode.
 *
 * <p>In standard mode, the strategy appends the provided payload to the base URL,
 * ensuring that the URL ends with a slash ("/") before adding the payload.
 * This mode is used for straightforward URL-based fuzzing without any special
 * modifications like subdomains or VHOST.
 */
public class RequestModeStrategyStandard extends RequestModeStrategy {

    /**
     * Modifies the given HTTP request by appending the fuzzing payload to the base URL.
     *
     * <p>If the base URL does not already end with a slash ("/"), this method appends
     * one before adding the payload. The resulting URL is then set in the request object.
     *
     * @param request    The {@link HttpRequestBase} object representing the HTTP request to be modified.
     * @param requestUrl The base URL of the target.
     * @param payload    The fuzzing payload to be appended to the URL.
     * @throws URISyntaxException If the modified URL is invalid or malformed.
     */
    @Override
    public void modifyRequest(HttpRequestBase request, String requestUrl, String payload) throws URISyntaxException {
        requestUrl = requestUrl.endsWith("/") ? requestUrl : requestUrl + "/";
        request.setURI(new URI(requestUrl + payload));
    }
}
