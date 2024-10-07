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
import org.apache.http.nio.client.HttpAsyncClient;
import org.apache.http.nio.reactor.ConnectingIOReactor;
import org.apache.http.nio.reactor.IOReactorException;
import vfuzz.config.ConfigAccessor;
import vfuzz.logging.Metrics;
import vfuzz.network.ratelimiter.RateLimiterLeakyBucket;
import java.nio.charset.StandardCharsets;

import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The {@code WebRequester} class handles sending HTTP requests asynchronously with built-in support for rate limiting,
 * retries, and optional jitter. It uses an asynchronous HTTP client to send requests and provides functionality for
 * retrying requests if they fail.
 *
 * <p>This class is designed to work in a high-throughput environment where multiple requests are sent continuously.
 * It integrates with a leaky bucket rate limiter to ensure requests are sent within predefined rate limits.
 * Optional jitter is applied to simulate network variability, and retries are handled for failed requests.
 */
public class WebRequester {

    private static final AtomicInteger activeFutures = new AtomicInteger(0);

    private static double futureLimit = 1000;

    private static final RateLimiterLeakyBucket rateLimiter;

    private static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    private static final CloseableHttpAsyncClient client;

    private static final Random random = new Random();

    public static void initialize() {}

    private final static boolean jitterEnabled = true;

    static {
        rateLimiter = new RateLimiterLeakyBucket(ConfigAccessor.getConfigValue("rateLimit", Integer.class));

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
        connManager.setDefaultMaxPerRoute(2000);
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
    }


    /**
     * Sends an HTTP request asynchronously, applying an optional jitter to simulate network variability. This method first
     * acquires a token from a rate limiter to ensure compliance with rate limiting policies before sending the request.
     * If the initial request fails, it automatically retries the request based on the specified delay and time unit.
     *
     * @param request The {@link HttpRequestBase} object representing the HTTP request to be sent. It must be fully
     *                configured with the target URL, headers, and any necessary request body.
     * @param retryDelay The delay between retries, if the initial request fails.
     * @param unit The {@link TimeUnit} of the {@code retryDelay}, specifying time unit of the delay.
     * @return A {@link CompletableFuture<HttpResponse>} that eventually completes with the result of the HTTP request.
     *         The future completes exceptionally if all retry attempts fail or if an error occurs during request processing.
     *         On successful completion, returns the HTTP response. If the request fails, the method retries sending the
     *         request as per the specified delay and continues to do so indefinitely.
     */
    public static CompletableFuture<HttpResponse> sendRequest(HttpRequestBase request, long retryDelay, TimeUnit unit) {

        while (!futureSlotAvailable()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        activeFutures.incrementAndGet();
        // rateLimiter.awaitToken();

        CompletableFuture<HttpResponse> responseFuture = new CompletableFuture<>();

        Runnable requestTask = () -> executeRequest(request, responseFuture);

        if (jitterEnabled) {
            int jitter = random.nextInt(500);
            CompletableFuture.delayedExecutor(jitter, TimeUnit.MILLISECONDS).execute(requestTask);
        } else {
            requestTask.run();
        }

        return responseFuture.handle((response, throwable) -> {
            activeFutures.decrementAndGet();
            Metrics.incrementRequestsCount();
            if (throwable != null) {
                Metrics.incrementRetriesCount();
                return handleRetries(request, retryDelay, unit);
            } else {
                Metrics.incrementSuccessfulRequestsCount();
                return CompletableFuture.completedFuture(response);
            }
        }).thenCompose(Function.identity());
    }

    private static boolean futureSlotAvailable() {
        return activeFutures.get() <= futureLimit;
    }

    /**
     * Handles the retries for an HTTP request asynchronously. This method is called when an initial request fails and
     * continues to retry the request indefinitely until it succeeds or the application stops it. It uses a scheduled
     * executor to delay retries according to the specified delay and time unit.
     *
     * @param request The {@link HttpRequestBase} object that needs to be retried. This request should already be configured
     *                with all necessary settings like URL, headers, and body as required.
     * @param delay The time to wait before making another retry attempt.
     * @param unit The {@link TimeUnit} that specifies the unit of the {@code delay}.
     * @return A {@link CompletableFuture<HttpResponse>} that completes with the response of the successful HTTP request.
     *         If a retry attempt fails, the method schedules another attempt using the same delay, effectively creating
     *         an infinite loop of retries until successful completion.
     */
    private static CompletableFuture<HttpResponse> handleRetries(HttpRequestBase request, long delay, TimeUnit unit) {
        return CompletableFuture.supplyAsync(() ->
                sendRequest(request, delay, unit), scheduler
        ).thenCompose(Function.identity());
    }

    /**
     * Executes an HTTP request asynchronously using {@link HttpAsyncClient}. This method handles the completion of the
     * request whether it succeeds, fails, or is cancelled, and updates the provided {@link CompletableFuture} accordingly.
     *
     * @param request The {@link HttpRequestBase} object representing the HTTP request to be sent. It should be fully configured
     *                with the target URL, headers, and any request body as needed.
     * @param responseFuture A {@link CompletableFuture<HttpResponse>} that will be completed when the HTTP request completes.
     *                       If the request is successful, the future is completed with the response. If the request fails
     *                       due to an exception, the future completes exceptionally, wrapping the encountered exception.
     *                       If the request is cancelled, the future is cancelled as well.
     */
    private static void executeRequest(HttpRequestBase request, CompletableFuture<HttpResponse> responseFuture) {
        client.execute(request, new FutureCallback<>() {
            @Override
            public void completed(HttpResponse response) {
                responseFuture.complete(response);
            }

            @Override
            public void failed(Exception ex) {
                Throwable cause = extractRelevantCause(ex);
                if (cause instanceof ProtocolException) {
                    Optional<HttpResponse> response = tryParseHttpResponse(cause.getMessage());
                    if (response.isPresent()) {
                        responseFuture.complete(response.get());
                        return;
                    }
                }
                responseFuture.completeExceptionally(ex);
            }
            @Override
            public void cancelled() {
                responseFuture.cancel(true);
            }
        });
    }


    /**
     * Extracts the most relevant cause for an exception.
     * If no cause is found, returns the given exception.
     *
     * @param ex The exception from which to extract the cause.
     * @return The most relevant cause or the original exception if no cause is found.
     */
    private static Throwable extractRelevantCause(Throwable ex) {
        return (ex.getCause() != null) ? ex.getCause() : ex;
    }

    /**
     * Attempts to parse an HTTP response from an exception message containing unexpected response codes.
     *
     * @param message The exception message to parse.
     * @return An {@link Optional<HttpResponse>} containing the parsed HTTP response, if parsing is successful.
     */
    private static Optional<HttpResponse> tryParseHttpResponse(String message) {
        Pattern pattern = Pattern.compile("(\\S+?)/(\\d)\\.(\\d) (\\d{3})");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return Optional.of(createHttpResponseFromException(matcher));
        }
        return Optional.empty();
    }

    /**
     * Creates an {@link HttpResponse} from the matched exception message.
     *
     * @param matcher The {@link Matcher} containing matched groups from the exception message.
     * @return A {@link HttpResponse} object representing the HTTP response parsed from the exception message.
     */
    private static HttpResponse createHttpResponseFromException(Matcher matcher) {
        String protocol = matcher.group(1);
        int protocolVersionMajor = Integer.parseInt(matcher.group(2));
        int protocolVersionMinor = Integer.parseInt(matcher.group(3));
        int statusCode = Integer.parseInt(matcher.group(4));
        ProtocolVersion protocolVersion = new ProtocolVersion(protocol, protocolVersionMajor, protocolVersionMinor);

        HttpResponse response = new BasicHttpResponse(protocolVersion, statusCode, null);
        response.setEntity(new StringEntity("", StandardCharsets.UTF_8));
        return response;
    }

    public static void setRateLimit(int i) {
        rateLimiter.setRateLimitPerSecond(i);
    }

    public static RateLimiterLeakyBucket getRateLimiter() {
        return rateLimiter;
    }

    public static void setFutureLimit(int i) {
        futureLimit = i;
    }

    public static double getFutureLimit() {
        return futureLimit;
    }

    public static void increaseFutureLimit() {
        futureLimit = Math.min(futureLimit * 1.005, 10000);
    }

    public static void decreaseFutureLimit() {
        futureLimit = Math.max((int) (futureLimit * .995), 10);
    }
}