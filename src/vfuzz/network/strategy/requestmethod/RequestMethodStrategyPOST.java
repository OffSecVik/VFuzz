package vfuzz.network.strategy.requestmethod;

import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import vfuzz.config.ConfigAccessor;

import java.io.UnsupportedEncodingException;

public class RequestMethodStrategyPOST extends RequestMethodStrategy {

    private final StringEntity postData;

    public RequestMethodStrategyPOST() {
        try {
            postData = new StringEntity(ConfigAccessor.getConfigValue("postRequestData", String.class));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Error cloning HTTP POST request entity", e);
        }
    }

    @Override
    public HttpRequestBase cloneRequest(HttpRequestBase request) {
        HttpPost clonedRequest = new HttpPost((request).getURI());
        cloneHeaders(request, clonedRequest);
        if (postData != null) {
            clonedRequest.setEntity(postData);
        }
        return clonedRequest;
    }

    @Override
    public HttpRequestBase createPrototypeRequest() {
        HttpPost prototypeRequest = new HttpPost();
        prototypeRequest.setEntity(postData);
        return prototypeRequest;
    }
}
