package vfuzz;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.StringEntity;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;

public class WebRequestBuilder { // TODO: obsolete? reconsider implementation of a WebRequestFactory to increase performance?

    public HttpRequestBase request = null;

     public void buildBaseRequest() { // here we build the parts of the request that remain the same across all requests during the fuzzing engagement.

        // initialize request here and set HTTP Method
        switch (ArgParse.getRequestMethod()) {
            case GET -> request = new HttpGet();
            case HEAD -> request = new HttpHead();
            case POST -> {
                HttpPost postRequest = new HttpPost();
                try {
                    postRequest.setEntity(new StringEntity("my post data")); // TODO: Variable
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e); // TODO: handle this exception better
                }
                request = postRequest;
            }
        }

        // setting up Headers from ArgParse
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

        // set up User-Agent
        if (ArgParse.getUserAgent() != null) {
            request.setHeader("User-Agent", ArgParse.getUserAgent());
        }
    }

    public HttpRequestBase buildSpecificRequest(String payload) {
        // load the payload depending on Mode
        try {
            switch (ArgParse.getRequestMode()) {
                case STANDARD -> request.setURI(new URI(ArgParse.getUrl() + payload));
                case SUBDOMAIN -> {
                    String rebuiltUrl = urlRebuilder(ArgParse.getUrl(), payload);
                    request.setURI(new URI(rebuiltUrl));
                }
                case VHOST -> {
                    // String rebuiltUrl = urlRebuilder(requestUrl, payload);
                    String vhostUrl = vhostRebuilder(ArgParse.getUrl(), payload);
                    request.setURI(new URI(ArgParse.getUrl()));
                    request.setHeader("Host", vhostUrl);
                    // System.out.println(request.getHeaders("Host").toString());
                }
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e); // TODO: handle this more gracefully
        }

        return request;
    }


    // TODO: unify the urlRebuilder and vhostRebuilder
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
