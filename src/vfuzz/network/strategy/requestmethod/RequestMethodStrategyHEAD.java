package vfuzz.network.strategy.requestmethod;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpRequestBase;

/**
 * The {@code RequestMethodStrategyHEAD} class is a concrete implementation of
 * the {@link RequestMethodStrategy} that handles HTTP HEAD requests.
 *
 * <p>This class defines how HEAD requests are cloned and how a prototype request
 * for the HEAD method is created. It uses the {@link HttpHead} class to represent
 * the HEAD request in Apache's HTTP client.
 */
public class RequestMethodStrategyHEAD extends RequestMethodStrategy {

    /**
     * Clones an existing HTTP HEAD request by copying its URI and headers.
     *
     * @param request The original {@link HttpRequestBase} to be cloned.
     * @return A cloned {@link HttpHead} request with the same URI and headers as the original.
     */
    @Override
    public HttpRequestBase cloneRequest(HttpRequestBase request) {
        HttpRequestBase clonedRequest = new HttpHead(request.getURI());
        cloneHeaders(request, clonedRequest);
        return clonedRequest;
    }

    /**
     * Creates a prototype HTTP HEAD request.
     *
     * @return A new {@link HttpHead} object representing a HEAD request.
     */
    @Override
    public HttpRequestBase createPrototypeRequest() {
        return new HttpHead();
    }
}
