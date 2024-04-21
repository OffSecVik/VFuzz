package vfuzz.network;

import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import vfuzz.config.ConfigAccessor;
import vfuzz.core.ArgParse;
import vfuzz.core.QueueConsumer;
import vfuzz.logging.Metrics;
import vfuzz.operations.RandomAgent;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebRequester {

    private static final RateLimiter rateLimiter = new RateLimiter(40000);

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final CloseableHttpAsyncClient client;

    static {
        System.setProperty("networkaddress.cache.ttl", "60");
        System.setProperty("networkaddress.cache.negative.ttl", "10");

        IOReactorConfig ioReactorConfig = IOReactorConfig.custom()
                .setIoThreadCount(Runtime.getRuntime().availableProcessors())
                .setConnectTimeout(5000)
                .setSoTimeout(5000)
                .build();

        ConnectingIOReactor ioReactor;
        try {
            ioReactor = new DefaultConnectingIOReactor(ioReactorConfig);
        } catch (IOReactorException e) {
            throw new RuntimeException(e);
        }

        PoolingNHttpClientConnectionManager connManager = new PoolingNHttpClientConnectionManager(ioReactor);
        connManager.setMaxTotal(10000);
        connManager.setDefaultMaxPerRoute(200);
        ConnectionKeepAliveStrategy keepAliveStrategy = (response, context) -> {
            return 5 * 1000; // keep alive for 5 seconds
        };
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(5000)
                .setConnectionRequestTimeout(5000)
                .setSocketTimeout(5000)
                .build();

        client = HttpAsyncClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connManager)
                .setKeepAliveStrategy(keepAliveStrategy)
                .setRedirectStrategy(new CustomRedirectStrategy())
                .build();
        client.start();
    } // here we initialize the HttpAsyncClient and set its settings.

    public static CompletableFuture<HttpResponse> sendRequestWithRetry(HttpRequestBase request, int maxRetries, long delay, TimeUnit unit) { // TODO decide whether to pass the delay as argument or have it as a static variable / ConfigManager setting

        Metrics.incrementRequestsCount();
        if (maxRetries < QueueConsumer.MAX_RETRIES) { // TODO this is janky as fuck
            Metrics.incrementRetriesCount();
        }

        CompletableFuture<HttpResponse> attempt = sendRequest(request);
        rateLimiter.awaitToken();
        return attempt.handle((resp, th) -> {
            if (th == null) {

                // Success case, return the response
                Metrics.incrementSuccessfulRequestsCount();
                return CompletableFuture.completedFuture(resp);
            } else if (maxRetries > 0) {
                // Retry case, schedule the retry with a delay
                CompletableFuture<HttpResponse> delayedRetry = new CompletableFuture<>();
                scheduler.schedule(() ->
                                sendRequestWithRetry(request, maxRetries - 1, delay, unit).whenComplete((retryResp, retryTh) -> {
                                    if (retryTh == null) {
                                        delayedRetry.complete(retryResp);
                                    } else {
                                        delayedRetry.completeExceptionally(retryTh);
                                    }
                                }),
                        delay, unit
                );
                return delayedRetry;
            } else {
                // Failure case, no retries left
                CompletableFuture<HttpResponse> failed = new CompletableFuture<>();
                failed.completeExceptionally(th);
                return failed;
            }
        }).thenCompose(Function.identity());
    }

    public static CompletableFuture<HttpResponse> sendRequest(HttpRequestBase request) {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();

        client.execute(request, HttpClientContext.create(), new FutureCallback<>() {
            @Override
            public void completed(HttpResponse response) {
                responseFuture.complete(response);
            }

            @Override
            public void failed(Exception ex) {
                Throwable cause = ex.getCause();
                if (cause == null) {
                    cause = ex;
                }
                if (cause instanceof ProtocolException) {
                    if (cause.getMessage().contains("Unexpected response:")) {
                        // Extracting the Http StatusLine object from the exception message
                        Pattern pattern = Pattern.compile("(\\S+?)/(\\d)\\.(\\d) (\\d{3})");
                        Matcher matcher = pattern.matcher(cause.getMessage());
                        if (matcher.find()) {
                            HttpResponse response = getHttpResponse(matcher);
                            responseFuture.complete(response);
                        }
                    }
                }
                responseFuture.completeExceptionally(ex);
            }

            private static HttpResponse getHttpResponse(Matcher matcher) {
                String protocol = matcher.group(1);
                int protocolVersionMajor = Integer.parseInt(matcher.group(2));
                int protocolVersionMinor = Integer.parseInt(matcher.group(3));
                int statusCode = Integer.parseInt(matcher.group(4));
                ProtocolVersion protocolVersion = new ProtocolVersion(protocol, protocolVersionMajor, protocolVersionMinor);

                // Making the response object and returning it
                HttpResponse response = new BasicHttpResponse(protocolVersion, statusCode, null);
                response.setEntity(new StringEntity("", StandardCharsets.UTF_8));
                return response;
            }

            @Override
            public void cancelled() {
                responseFuture.cancel(true);
            }
        });
        return responseFuture;
    }

    public static HttpRequestBase buildRequest(String requestUrl, String payload) {
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

    public static HttpRequestBase buildRequestFromFile(ParsedHttpRequest parsedRequest, String payload) {
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

    private static String removeTrailingSlash(String url) {
        if (url != null && url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
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

}