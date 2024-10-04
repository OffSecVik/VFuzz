package vfuzz.network.request;

import org.apache.http.client.methods.HttpRequestBase;

/**
 * The {@code WebRequestFactory} interface defines a contract for creating HTTP requests.
 *
 * <p>Implementations of this interface are responsible for building an HTTP request object
 * based on a given fuzzing payload. The created request is expected to be fully configured
 * and ready to be sent, including any headers, URL, and other configurations needed.
 *
 * <p>This interface allows for various strategies of constructing web requests, providing
 * flexibility in how different HTTP methods or request modes are applied.
 *
 * <p>Example usage of this interface:
 * <pre>
 *     WebRequestFactory requestFactory = new StandardRequestFactory("http://example.com");
 *     HttpRequestBase request = requestFactory.buildRequest("fuzzPayload");
 * </pre>
 */
public interface WebRequestFactory {

    /**
     * Builds an HTTP request using the provided fuzzing payload.
     *
     * <p>The payload is typically inserted into the URL or body of the request, depending on
     * the specific implementation. The returned request will be fully configured and
     * ready to be sent.
     *
     * @param payload The fuzzing payload to be included in the request.
     * @return A {@link HttpRequestBase} object representing the HTTP request.
     */
    HttpRequestBase buildRequest(String payload);

}
