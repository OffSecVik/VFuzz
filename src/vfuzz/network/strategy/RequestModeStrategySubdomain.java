package vfuzz.network.strategy;

import org.apache.http.client.methods.HttpRequestBase;

import java.net.URI;
import java.net.URISyntaxException;

public class RequestModeStrategySubdomain extends RequestModeStrategy {

    @Override
    public void modifyRequest(HttpRequestBase request, String requestUrl, String payload) throws URISyntaxException {
        requestUrl = requestUrl.endsWith("/") ? requestUrl : requestUrl + "/";
        String rebuiltUrl = urlRebuilder(requestUrl, payload);
        String vhostUrl = vhostRebuilder(requestUrl, payload);
        request.setURI(new URI(rebuiltUrl));
        request.setHeader("Host", vhostUrl);
    }
}
