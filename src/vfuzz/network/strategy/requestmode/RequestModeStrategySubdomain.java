package vfuzz.network.strategy.requestmode;

import org.apache.http.client.methods.HttpRequestBase;
import java.net.URI;
import java.net.URISyntaxException;

/**
 * The {@code RequestModeStrategySubdomain} class is a concrete implementation of
 * {@link RequestModeStrategy}, designed to handle requests in the subdomain mode.
 *
 * <p>In subdomain mode, the strategy modifies the request by rebuilding the URL
 * to include the fuzzing payload as a subdomain, and it sets the "Host" header
 * accordingly for virtual host fuzzing. This allows the fuzzer to target subdomain-based
 * vulnerabilities by inserting the fuzzing payload dynamically into the subdomain part of the URL.
 */
public class RequestModeStrategySubdomain extends RequestModeStrategy {

    /**
     * Modifies the given HTTP request by injecting the fuzzing payload as a subdomain
     * into the base URL and setting the "Host" header to reflect the modified URL.
     *
     * <p>The method rebuilds the URL to insert the payload before the domain, then it sets
     * this new URL as the request URI. Additionally, it sets the "Host" header with a value
     * that reflects the virtual host configuration, which may be required in some fuzzing scenarios.
     *
     * @param request    The {@link HttpRequestBase} object representing the HTTP request to be modified.
     * @param requestUrl The base URL of the target.
     * @param payload    The fuzzing payload to be inserted into the subdomain part of the URL.
     * @throws URISyntaxException If the modified URL is invalid or malformed.
     */
    @Override
    public void modifyRequest(HttpRequestBase request, String requestUrl, String payload) throws URISyntaxException {
        requestUrl = requestUrl.endsWith("/") ? requestUrl : requestUrl + "/";

        // Rebuild the URL with the payload as the subdomain
        String rebuiltUrl = urlRebuilder(requestUrl, payload);

        // Build the virtual host URL for the Host header
        String vhostUrl = vhostRebuilder(requestUrl, payload);

        // Set the modified URI and Host header in the request
        request.setURI(new URI(rebuiltUrl));
        request.setHeader("Host", vhostUrl);
    }
}
