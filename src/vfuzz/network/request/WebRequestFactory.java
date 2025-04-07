package vfuzz.network.request;

import org.apache.http.client.methods.HttpRequestBase;
import vfuzz.except.controlflow.PayloadGenerationFinishedException;
import vfuzz.except.controlflow.WordlistCompletedException;

import java.util.ArrayList;
import java.util.List;

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
public abstract class WebRequestFactory {

    List<String> currentPayloads = new ArrayList<>();


    public List<String> getPayloads() {
        return currentPayloads;
    }

    public WebRequestFactory() {

    }

    /**
     * Builds an HTTP request using the provided fuzzing payload.
     *
     * <p>The payload is typically inserted into the URL or body of the request, depending on
     * the specific implementation. The returned request will be fully configured and
     * ready to be sent.
     *
     * @return A {@link HttpRequestBase} object representing the HTTP request.
     */
    public abstract HttpRequestBase buildRequest() throws PayloadGenerationFinishedException;

    protected void setPayloads(List<String> payloads) {
        this.currentPayloads = payloads;
    }
}
