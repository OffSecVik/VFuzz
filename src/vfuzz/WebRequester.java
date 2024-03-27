package vfuzz;

import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.impl.nio.client.HttpAsyncClients;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.impl.nio.reactor.IOReactorConfig;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class WebRequester {

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static int delay = 1000;

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
                .build();
        client.start();

    }


    public static CompletableFuture<HttpResponse> sendRequestWithRetry(String url, int maxRetries, long delay, TimeUnit unit) {
        CompletableFuture<HttpResponse> attempt = sendRequest(url);

        return attempt.handle((resp, th) -> {
            if (th == null) {
                // Success case, return the response
                return CompletableFuture.completedFuture(resp);
            } else if (maxRetries > 0) {
                // Retry case, schedule the retry with a delay
                CompletableFuture<HttpResponse> delayedRetry = new CompletableFuture<>();
                scheduler.schedule(() ->
                                sendRequestWithRetry(url, maxRetries - 1, delay, unit).whenComplete((retryResp, retryTh) -> {
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

    public static CompletableFuture<HttpResponse> sendRequest(String url) {
        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();


        HttpUriRequest request = new HttpGet(url); // keeping it simple for now

        client.execute(request, new FutureCallback<HttpResponse>() {
            @Override
            public void completed(HttpResponse response) {
                responseFuture.complete(response);
            }

            @Override
            public void failed(Exception ex) {
                responseFuture.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                responseFuture.cancel(true);
            }
        });

        return responseFuture;
    }
}
