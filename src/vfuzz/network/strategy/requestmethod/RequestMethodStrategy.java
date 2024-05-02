package vfuzz.network.strategy.requestmethod;

import org.apache.http.Header;
import org.apache.http.client.methods.HttpRequestBase;

public abstract class RequestMethodStrategy {

    public abstract HttpRequestBase cloneRequest(HttpRequestBase request);

    protected void cloneHeaders(HttpRequestBase sourceRequest, HttpRequestBase destRequest) {
        for (Header header : sourceRequest.getAllHeaders()) {
            destRequest.addHeader(header);
        }
    }

    public abstract HttpRequestBase createPrototypeRequest();
}
