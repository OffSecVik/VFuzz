package vfuzz.network;

import org.apache.http.HttpResponse;
import org.apache.http.ProtocolException;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpRequestBase;
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
import vfuzz.core.QueueConsumer;
import vfuzz.logging.Metrics;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WebRequester {

    private static final RateLimiter rateLimiter = new RateLimiter(5000);

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
        client.execute(request, new FutureCallback<>() {
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
                            return;
                        }
                    }
                }
                responseFuture.completeExceptionally(ex);
            }
            @Override
            public void cancelled() {
                responseFuture.cancel(true);
            }
        });
        return responseFuture;
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
}