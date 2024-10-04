package vfuzz.network.strategy.requestmethod;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;

/**
 * The {@code RequestMethodStrategyGET} class is a concrete implementation of
 * the {@link RequestMethodStrategy} that handles HTTP GET requests.
 *
 * <p>This class defines how GET requests are cloned and how a prototype request
 * for the GET method is created. It uses the {@link HttpGet} class to represent
 * the GET request in Apache's HTTP client.
 */
public class RequestMethodStrategyGET extends RequestMethodStrategy {

    /**
     * Clones an existing HTTP GET request by copying its URI and headers.
     *
     * @param request The original {@link HttpRequestBase} to be cloned.
     * @return A cloned {@link HttpGet} request with the same URI and headers as the original.
     */
    @Override
    public HttpRequestBase cloneRequest(HttpRequestBase request) {
        HttpRequestBase clonedRequest = new HttpGet(request.getURI());
        cloneHeaders(request, clonedRequest);
        return clonedRequest;
    }

    /**
     * Creates a prototype HTTP GET request.
     *
     * @return A new {@link HttpGet} object representing a GET request.
     */
    @Override
    public HttpRequestBase createPrototypeRequest() {
        return new HttpGet();
    }
}
