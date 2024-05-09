package vfuzz.network.request;

import org.apache.http.client.methods.HttpRequestBase;

public interface WebRequestFactory {

    HttpRequestBase buildRequest(String payload);

}
