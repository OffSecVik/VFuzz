package vfuzz.network.strategy.requestmethod;

import org.apache.http.Header;
import org.apache.http.client.methods.HttpRequestBase;

/**
 * The {@code RequestMethodStrategy} class defines an abstract strategy
 * for handling HTTP request methods in a customizable manner.
 *
 * <p>This class is part of the Strategy Pattern implementation, allowing the fuzzer
 * to dynamically choose the request method strategy at runtime.
 * Subclasses of this strategy will implement specific HTTP methods
 * (e.g., GET, POST, etc.) by cloning and creating new prototype requests.
 */
public abstract class RequestMethodStrategy {

    /**
     * Creates a deep clone of the given HTTP request, including its headers.
     * The specific type of request method (e.g., GET, POST) is determined
     * by subclasses of this strategy.
     *
     * @param request The {@link HttpRequestBase} to be cloned.
     * @return A new {@link HttpRequestBase} that is a clone of the given request.
     */
    public abstract HttpRequestBase cloneRequest(HttpRequestBase request);

    /**
     * Clones all headers from the source request to the destination request.
     * This helper method ensures that all headers from the original HTTP request
     * are preserved when cloning.
     *
     * @param sourceRequest The original request from which headers are copied.
     * @param destRequest   The destination request to which headers are added.
     */
    protected void cloneHeaders(HttpRequestBase sourceRequest, HttpRequestBase destRequest) {
        for (Header header : sourceRequest.getAllHeaders()) {
            destRequest.addHeader(header);
        }
    }

    /**
     * Creates a prototype of the HTTP request that represents the specific
     * request method (e.g., GET, POST). Subclasses will implement this method
     * to define the behavior of their respective HTTP methods.
     *
     * @return A new {@link HttpRequestBase} representing the prototype request.
     */
    public abstract HttpRequestBase createPrototypeRequest();
}
