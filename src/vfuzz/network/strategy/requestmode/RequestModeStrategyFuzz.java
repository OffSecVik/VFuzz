package vfuzz.network.strategy.requestmode;

import org.apache.http.client.methods.HttpRequestBase;
import vfuzz.config.ConfigAccessor;

import java.net.URI;
import java.net.URISyntaxException;

public class RequestModeStrategyFuzz extends RequestModeStrategy {

    private String fuzzMarker;

    public RequestModeStrategyFuzz() {
        fuzzMarker = ConfigAccessor.getConfigValue("fuzzMarker", String.class);
    }

    @Override
    public void modifyRequest(HttpRequestBase request, String requestUrl, String payload) throws URISyntaxException {
        request.setURI(new URI(requestUrl.replaceFirst(fuzzMarker, payload)));
    }
}
