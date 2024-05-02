package vfuzz.network.strategy.requestmethod;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;

public class RequestMethodStrategyGET extends RequestMethodStrategy {

    @Override
    public HttpRequestBase cloneRequest(HttpRequestBase request) {
        HttpRequestBase clonedRequest = new HttpGet(request.getURI());
        cloneHeaders(request, clonedRequest);
        return clonedRequest;
    }

    @Override
    public HttpRequestBase createPrototypeRequest() {
        return new HttpGet();
    }
}
