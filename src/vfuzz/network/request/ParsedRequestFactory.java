package vfuzz.network.request;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import vfuzz.config.ConfigAccessor;
import vfuzz.operations.RandomAgent;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

public class ParsedRequestFactory implements WebRequestFactory {

    private ParsedHttpRequest prototypeRequest;


    public ParsedRequestFactory() {
        try {
            prototypeRequest = new ParsedHttpRequest().parseHttpRequestFromFile(ConfigAccessor.getConfigValue("requestFilePath", String.class));
        } catch (IOException e) {
            throw new RuntimeException("There was an error parsing the request from the file:\n" + e.getMessage());
        }
    }


    @Override
    public HttpRequestBase buildRequest(String payload) {
        ParsedHttpRequest rawCopy = new ParsedHttpRequest(prototypeRequest);
        return buildRequestFromFile(rawCopy, payload);
    }

    public HttpRequestBase buildRequestFromFile(ParsedHttpRequest parsedRequest, String payload) {
        try {
            String encodedPayload = URLEncoder.encode(payload, StandardCharsets.UTF_8); // urlencoding, some wordlists have weird payloads
            parsedRequest.replaceFuzzMarker(encodedPayload); // injecting the payload into the request // TODO: OPTIONAL: could avoid making deep copies of the parsedRequest in QueueConsumer if we found a way to parse for FUZZ AFTER extracting the data from the parsedRequest. This would likely involve making a method in this class right here or checking for FUZZ every time we read data from the request
            HttpRequestBase request = null;
            String requestUrl = parsedRequest.getUrl();
            // requestUrl = requestUrl.endsWith("/") ? requestUrl : requestUrl + "/"; // TODO: Take care of duplicate due to backslashes another way, this is a little janky

            // set request method
            switch (parsedRequest.getMethod().toUpperCase()) {
                case "GET" -> request = new HttpGet(requestUrl);
                case "HEAD" -> request = new HttpHead(requestUrl);
                case "POST" -> {
                    HttpPost postRequest = new HttpPost(requestUrl);
                    postRequest.setEntity(new StringEntity(parsedRequest.getBody())); // TODO: check if POST body is preserved, handle content-length dynamically based on payload length
                    request = postRequest;
                }
            }

            // set up headers
            for (Map.Entry<String, String>entry : parsedRequest.getHeaders().entrySet()) {
                Objects.requireNonNull(request).setHeader(entry.getKey(), entry.getValue());
            }

            if (ConfigAccessor.getConfigValue("randomAgent", Boolean.class)) {
                Objects.requireNonNull(request).setHeader("User-Agent", RandomAgent.get());
            }

            return request;

        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
