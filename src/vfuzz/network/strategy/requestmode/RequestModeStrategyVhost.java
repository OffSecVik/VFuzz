package vfuzz.network.strategy.requestmode;

import org.apache.http.client.methods.HttpRequestBase;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * The {@code RequestModeStrategyVhost} class is a concrete implementation of
 * {@link RequestModeStrategy}, designed to handle requests in VHOST mode.
 *
 * <p>In VHOST mode, the strategy modifies the request by setting the "Host"
 * header to a dynamically constructed virtual host URL based on the provided
 * fuzzing payload. This allows the fuzzer to target potential virtual host-based
 * vulnerabilities by modifying the "Host" header while keeping the request URL intact.
 */
public class RequestModeStrategyVhost extends RequestModeStrategy {

    /**
     * Modifies the given HTTP request by setting the "Host" header to a virtual
     * host URL based on the provided fuzzing payload. The base URL remains unchanged,
     * but the "Host" header is dynamically modified to reflect the virtual host.
     *
     * @param request    The {@link HttpRequestBase} object representing the HTTP request to be modified.
     * @param requestUrl The base URL of the target.
     * @param payload    The fuzzing payload to be used as the virtual host in the "Host" header.
     * @throws URISyntaxException If the modified URL or host header is malformed.
     */
    @Override
    public void modifyRequest(HttpRequestBase request, String requestUrl, String payload) throws URISyntaxException {

        // Ensure the base URL ends with a trailing slash
        requestUrl = requestUrl.endsWith("/") ? requestUrl : requestUrl + "/";

        // Set the request URI to the unchanged base URL
        request.setURI(new URI(requestUrl));

        // Rebuild the virtual host URL using the payload
        String vhostUrl = vhostRebuilder(requestUrl, payload);

        // Set the "Host" header to the virtual host URL
        request.setHeader("Host", vhostUrl);
    }
}
