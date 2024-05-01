package vfuzz.network;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;
import vfuzz.config.ConfigAccessor;
import vfuzz.core.ArgParse;
import vfuzz.operations.RandomAgent;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;


public class WebRequestFactory {

    public HttpRequestBase buildRequest(String requestUrl, String payload) {
        try {
            String encodedPayload = URLEncoder.encode(payload, StandardCharsets.UTF_8);
            HttpRequestBase request = null;

            // for now every url gets a slash except if we're in FUZZ mode
            if (ConfigAccessor.getConfigValue("requestMode", RequestMode.class) != RequestMode.FUZZ) {
                requestUrl = requestUrl.endsWith("/") ? requestUrl : requestUrl + "/";
            }

            if (!payload.equals(encodedPayload)) {
                payload = encodedPayload;
                // System.out.println("Encoded \"" + payload + " to \"" + encodedPayload); // debug prints
            }

            // initialize request here and set HTTP Method
            switch (ConfigAccessor.getConfigValue("requestMethod", RequestMethod.class)) {
                case GET -> request = new HttpGet(requestUrl);
                case HEAD -> request = new HttpHead(requestUrl);
                case POST -> {
                    HttpPost postRequest = new HttpPost();
                    postRequest.setEntity(new StringEntity(ConfigAccessor.getConfigValue("postRequestData", String.class)));
                    request = postRequest;
                }
            }

            // load the payload depending on Mode
            switch (ConfigAccessor.getConfigValue("requestMode", RequestMode.class)) {
                case STANDARD -> request.setURI(new URI(requestUrl + payload));
                case SUBDOMAIN -> {

                    String rebuiltUrl = urlRebuilder(requestUrl, payload);
                    String vhostUrl = vhostRebuilder(requestUrl, payload);
                    request.setURI(new URI(rebuiltUrl));
                    request.setHeader("Host", vhostUrl);

                }
                case VHOST -> {
                    // String rebuiltUrl = urlRebuilder(requestUrl, payload);
                    request.setURI(new URI(requestUrl));
                    String vhostUrl = vhostRebuilder(requestUrl, payload);
                    request.setHeader("Host", vhostUrl);
                    // System.out.println(request.getHeaders("Host").toString());
                }
                case FUZZ -> request.setURI(new URI(requestUrl.replaceFirst(ConfigAccessor.getConfigValue("fuzzMarker", String.class), payload)));
            }

            // set up User-Agent
            if (ConfigAccessor.getConfigValue("userAgent", String.class) != null) {
                request.setHeader("User-Agent", ConfigAccessor.getConfigValue("userAgent", String.class));
            }

            // set up Headers
            if (!ArgParse.getHeaders().isEmpty()) {
                for (String header : ArgParse.getHeaders()) {
                    String[] parts = header.split(":", 2); // split at the first colon
                    if (parts.length == 2) {
                        String headerName = parts[0].trim();
                        String headerValue = parts[1].trim();
                        request.setHeader(headerName, headerValue);
                    } else {
                        System.err.println("Invalid header format while building request: " + header);
                    }
                }
            }

            if (ConfigAccessor.getConfigValue("cookies", String.class) != null) {
                request.setHeader("Cookie", ConfigAccessor.getConfigValue("cookies", String.class));
            }

            if (ConfigAccessor.getConfigValue("randomAgent", Boolean.class)) {
                request.setHeader("User-Agent", RandomAgent.get());
            }

            return request;

        } catch (IllegalArgumentException e) {
            System.err.println("Invalid URI: " + e.getMessage());
        } catch (UnsupportedEncodingException | URISyntaxException e) {
            throw new RuntimeException(e);
        }

        return null;
    }

    public HttpRequestBase buildRequestFromFile(ParsedHttpRequest parsedRequest, String payload) {
        try {
            String encodedPayload = URLEncoder.encode(payload, StandardCharsets.UTF_8); // urlencoding, some wordlists have weird payloads
            parsedRequest.replaceFuzzMarker(encodedPayload); // injecting the payload into the request // TODO: OPTIONAL: could avoid making deep copies of the parsedRequest in QueueConsumer if we found a way to parse for FUZZ AFTER extracting the data from the parsedRequest. This would likely involve making a method in this class right here or checking for FUZZ every time we read data from the request
            // String encodedPayload = URLEncoder.encode(payload, StandardCharsets.UTF_8);
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

    private static String urlRebuilder(String url, String payload) { // rebuilds URL for VHOST and SUBDOMAIN mode
        String httpPrefix = url.startsWith("https://") ? "https://" : "http://"; // selects which scheme the url starts with.
        String urlWithoutScheme = url.substring(httpPrefix.length()); // gets everything except the scheme
        String urlWithoutWww = urlWithoutScheme.startsWith("www") ? urlWithoutScheme.substring(4) : urlWithoutScheme; // cuts "www." if present in the url
        return httpPrefix + payload + "." + urlWithoutWww; // test this
    }

    private static String vhostRebuilder(String url, String payload) {
        String httpPrefix = url.startsWith("https://") ? "https://" : "http://"; // selects which scheme the url starts with.
        String urlWithoutScheme = url.substring(httpPrefix.length()); // gets everything except the scheme
        String urlWithoutWww = urlWithoutScheme.startsWith("www") ? urlWithoutScheme.substring(4) : urlWithoutScheme; // cuts "www." if present in the url
        return payload + "." + removeTrailingSlash(urlWithoutWww); // test this
    }

    private static String removeTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }
}
