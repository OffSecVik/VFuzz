package vfuzz.network.strategy;

import org.apache.http.client.methods.HttpRequestBase;

import java.net.URI;
import java.net.URISyntaxException;

public class RequestModeStrategyVhost extends RequestModeStrategy {

    @Override
    public void modifyRequest(HttpRequestBase request, String requestUrl, String payload) throws URISyntaxException {
        requestUrl = requestUrl.endsWith("/") ? requestUrl : requestUrl + "/";
        request.setURI(new URI(requestUrl));
        String vhostUrl = vhostRebuilder(requestUrl, payload);
        request.setHeader("Host", vhostUrl);
        URI uri = new URI("TEST");
    }
}
