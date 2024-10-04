package vfuzz.network.strategy.requestmode;

import org.apache.http.client.methods.HttpRequestBase;
import java.net.URISyntaxException;

/**
 * The {@code RequestModeStrategy} abstract class defines a strategy for modifying
 * HTTP requests based on different request modes, such as VHOST, SUBDOMAIN, and STANDARD modes.
 *
 * <p>This class is part of the Strategy Pattern implementation, where different
 * concrete strategies will modify HTTP requests in various ways, depending on the request mode.
 *
 * <p>The class also provides utility methods to assist with URL rebuilding for
 * VHOST and SUBDOMAIN modes.
 */
public abstract class RequestModeStrategy {

    /**
     * Modifies the given HTTP request by applying the specific request mode strategy.
     *
     * <p>This method must be implemented by subclasses to define how the request is modified
     * according to the selected request mode (e.g., VHOST, SUBDOMAIN).
     *
     * @param request The {@link HttpRequestBase} object representing the HTTP request to be modified.
     * @param url     The base URL of the target.
     * @param payload The payload to be injected into the URL.
     * @throws URISyntaxException if the URL is malformed or invalid.
     */
    public abstract void modifyRequest(HttpRequestBase request, String url, String payload) throws URISyntaxException;

    /**
     * Rebuilds the URL for VHOST and SUBDOMAIN modes by injecting the payload
     * into the URL. The payload is inserted before the domain.
     *
     * <p>For example, given a payload of "test" and a URL of "http://example.com",
     * this method will return "http://test.example.com".
     *
     * @param url     The base URL of the target.
     * @param payload The payload to be inserted into the URL.
     * @return The rebuilt URL with the payload injected.
     */
    static String urlRebuilder(String url, String payload) { // rebuilds URL for VHOST and SUBDOMAIN mode
        String httpPrefix = url.startsWith("https://") ? "https://" : "http://"; // selects which scheme the url starts with.
        String urlWithoutScheme = url.substring(httpPrefix.length()); // gets everything except the scheme
        String urlWithoutWww = urlWithoutScheme.startsWith("www") ? urlWithoutScheme.substring(4) : urlWithoutScheme; // cuts "www." if present in the url
        return httpPrefix + payload + "." + urlWithoutWww; // test this
    }

    /**
     * Rebuilds the URL specifically for VHOST mode by injecting the payload before the domain.
     *
     * <p>This is similar to {@code urlRebuilder}, but removes the trailing slash
     * from the domain if it exists.
     *
     * @param url     The base URL of the target.
     * @param payload The payload to be inserted into the URL.
     * @return The rebuilt URL with the payload injected.
     */
    static String vhostRebuilder(String url, String payload) {
        String httpPrefix = url.startsWith("https://") ? "https://" : "http://"; // selects which scheme the url starts with.
        String urlWithoutScheme = url.substring(httpPrefix.length()); // gets everything except the scheme
        String urlWithoutWww = urlWithoutScheme.startsWith("www") ? urlWithoutScheme.substring(4) : urlWithoutScheme; // cuts "www." if present in the url
        return payload + "." + removeTrailingSlash(urlWithoutWww); // test this
    }

    /**
     * Removes the trailing slash from the given URL, if present.
     *
     * <p>This utility method ensures that the URL does not have a trailing slash,
     * which could interfere with URL processing in VHOST or SUBDOMAIN modes.
     *
     * @param url The URL from which to remove the trailing slash.
     * @return The URL without the trailing slash.
     */
    private static String removeTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
