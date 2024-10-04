package vfuzz.network.strategy.requestmethod;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import vfuzz.config.ConfigAccessor;

import java.io.UnsupportedEncodingException;

/**
 * The {@code RequestMethodStrategyPOST} class is a concrete implementation of
 * the {@link RequestMethodStrategy} that handles HTTP POST requests.
 *
 * <p>This class defines how POST requests are cloned and how a prototype request
 * for the POST method is created. It uses the {@link HttpPost} class to represent
 * the POST request in Apache's HTTP client. Additionally, it handles the
 * cloning and creation of POST data via {@link StringEntity}.
 */
public class RequestMethodStrategyPOST extends RequestMethodStrategy {

    private final StringEntity postData;

    /**
     * Constructs a new {@code RequestMethodStrategyPOST} by retrieving the POST
     * data from the configuration and creating a {@link StringEntity} to be
     * included in the request body.
     */
    public RequestMethodStrategyPOST() {
        try {
            postData = new StringEntity(ConfigAccessor.getConfigValue("postRequestData", String.class));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Error cloning HTTP POST request entity", e);
        }
    }

    /**
     * Clones an existing HTTP POST request by copying its URI, headers, and body data.
     *
     * @param request The original {@link HttpRequestBase} to be cloned.
     * @return A cloned {@link HttpPost} request with the same URI, headers, and POST data as the original.
     */
    @Override
    public HttpRequestBase cloneRequest(HttpRequestBase request) {
        HttpPost clonedRequest = new HttpPost((request).getURI());
        cloneHeaders(request, clonedRequest);
        if (postData != null) {
            clonedRequest.setEntity(postData);
        }
        return clonedRequest;
    }

    /**
     * Creates a prototype HTTP POST request, initializing it with POST data.
     *
     * @return A new {@link HttpPost} object representing a POST request with the configured POST data.
     */
    @Override
    public HttpRequestBase createPrototypeRequest() {
        HttpPost prototypeRequest = new HttpPost();
        prototypeRequest.setEntity(postData);
        return prototypeRequest;
    }
}
