package vfuzz.core;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import vfuzz.config.ConfigAccessor;
import vfuzz.network.request.ParsedRequestFactory;
import vfuzz.network.request.WebRequestFactory;
import vfuzz.network.strategy.requestmode.RequestMode;
import vfuzz.network.request.StandardRequestFactory;
import vfuzz.network.WebRequester;
import vfuzz.operations.Hit;
import vfuzz.operations.Range;
import vfuzz.operations.Target;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The {@code QueueConsumer} class is responsible for handling the fuzzing logic within the fuzzer.
 * It reads payloads from the wordlist, sends HTTP requests using various request strategies,
 * and processes the responses.
 *
 * <p> The class also supports recursion for further exploration of targets based on specific configurations.
 * It filters results based on excluded status codes, content lengths, and certain URLs, and creates
 * "Hit" objects when a valid target is found.
 *
 * <p> This class works as a core component of the fuzzer and operates in a multi-threaded environment
 * managed by the {@link ThreadOrchestrator}.
 */
public class QueueConsumer implements Runnable {

    private final ThreadOrchestrator orchestrator;
    private final ExecutorService executor;
    private final WordlistReader wordlistReader;
    private final String baseTargetUrl;
    private final Target target;
    private final String url;
    private final boolean recursionEnabled;
    private final int recursionDepth;
    private final boolean vhostMode;
    private volatile boolean running = true;
    private static boolean firstThreadFinished = false;
    private final Set<Range> excludedStatusCodes;
    private final Set<Range> excludedLength;
    private final List<String> excludedResults;
    private WebRequestFactory webRequestFactory;

    private AtomicInteger totalFutures = new AtomicInteger(0);
    private AtomicInteger completedFutures = new AtomicInteger(0);

    /**
     * Constructs a new {@code QueueConsumer} object with the given {@link ThreadOrchestrator} and {@link Target}.
     *
     * @param orchestrator The thread orchestrator managing the execution of threads.
     * @param target The target being fuzzed by the current consumer.
     */
    public QueueConsumer(ThreadOrchestrator orchestrator, Target target) {

        this.orchestrator = orchestrator;
        this.executor = orchestrator.getExecutor();
        this.wordlistReader = target.getWordlistReader();
        this.baseTargetUrl = ConfigAccessor.getConfigValue("url", String.class) + "/";
        this.target = target;
        this.url = target.getUrl();
        this.recursionEnabled = ConfigAccessor.getConfigValue("recursionEnabled", Boolean.class);
        this.recursionDepth = target.getRecursionDepth();
        this.excludedStatusCodes = ArgParse.getExcludedStatusCodes();
        this.excludedLength = ArgParse.getExcludedLength();

        String excludedResults = ConfigAccessor.getConfigValue("excludedResults", String.class);
        if (excludedResults != null) {
            this.excludedResults = Arrays.stream((excludedResults).split(",")).toList();
        } else {
            this.excludedResults = null;
        }

        this.vhostMode = ConfigAccessor.getConfigValue("requestMode", RequestMode.class) == RequestMode.VHOST;
    }

    /**
     * The main logic for the QueueConsumer thread.
     * It reads payloads from the wordlist, constructs HTTP requests, sends them, and processes the responses.
     */
    @Override
    public void run() {
        if (ConfigAccessor.getConfigValue("requestFileFuzzing", String.class) == null) {
            webRequestFactory = new StandardRequestFactory(url);
        } else {
            webRequestFactory = new ParsedRequestFactory();
        }

        while (running) {
            String payload = wordlistReader.getNextPayload();
            if (payload == null) {
                reachedEndOfWordlist();
                break;
            }

            HttpRequestBase request = webRequestFactory.buildRequest(payload);
            sendAndProcessRequest(request);
        }
    }

    /**
     * Called when the end of the wordlist is reached.
     * It stops the current consumer and redistributes threads if recursion is enabled.
     */
    private void reachedEndOfWordlist() {
        running = false;
        firstThreadFinished = true;
        if (ConfigAccessor.getConfigValue("recursionEnabled", Boolean.class) && target.setScanComplete()) {
            orchestrator.redistributeThreads();
        }
    }


    /**
     * Sends an HTTP request and processes the response asynchronously.
     *
     * @param request The HTTP request to be sent.
     */
    private void sendAndProcessRequest(HttpRequestBase request) {
        CompletableFuture<HttpResponse> c = WebRequester.sendRequest(request, 250, TimeUnit.MILLISECONDS)
                .thenApplyAsync(response -> {
            try {
                parseResponse(response, request);
            } catch (Exception e) {
                // System.err.println("Error processing response for " + response.getStatusLine().getStatusCode() + " response: " + e.getMessage());
            }
            return response;
        }, executor)
                .exceptionally(ex -> null)
                .thenApply(response -> {
                    completedFutures.incrementAndGet();
                    return response;
                });

        totalFutures.incrementAndGet();
    }

    /**
     * Parses the HTTP response and determines whether it should be excluded based on
     * status codes, content length, or previously excluded results.
     * If the response is valid, a {@link Hit} object is created.
     *
     * @param response The HTTP response received.
     * @param request The original HTTP request sent.
     */
    private void parseResponse(HttpResponse response, HttpRequestBase request) {

        int responseCode = response.getStatusLine().getStatusCode();
        for (Range range : excludedStatusCodes) {
            if (range.contains(responseCode)) {
                return;
            }
        }

        int responseContentLength = (int)response.getEntity().getContentLength();
        for (Range range : excludedLength) {
            if (range.contains(responseContentLength)) {
                return;
            }
        }

        String requestUrl;
        if (vhostMode) { // setting the requestUrl to the vhost value for simplicity
            requestUrl = request.getHeaders("HOST")[0].getValue(); // simply setting the requestUrl (which never changes in vhost mode) to the header value (which is the interesting field)
        } else {
            requestUrl = request.getURI().toString();
        }

        if (isExcluded(requestUrl)) {
            return;
        }

        // checking for double hit:
        for (Hit hit : Hit.getHits()) {
            if (hit.url().equals(requestUrl)) {
                return;
            }
            if (hit.url().equalsIgnoreCase(requestUrl)) { //TODO make this optional, this ignores case insensitive double hits
                return;
            }
        }

        new Hit(requestUrl, responseCode, responseContentLength); // Idea: make Hit object take a HttpRequest as argument, this way we could retain more information?
        System.out.println("Hit for " + request + " : " + responseCode);
        if (recursionEnabled && isRecursiveTarget(requestUrl)) {
            orchestrator.initiateRecursion(requestUrl, recursionDepth);
        }
    }

    /**
     * Determines if the URL is a valid target for recursion based on previous hits and the base URL.
     *
     * @param url The URL to check.
     * @return {@code true} if the URL is a recursive target, {@code false} otherwise.
     */
    private boolean isRecursiveTarget(String url) {
        if (url.equals(baseTargetUrl)) { // this does fix the initial forking
            return false;
        }
        for (Hit hit : Hit.getHits()) { // checking for a redundant hit which would make recursion fork every time it's encountered, e.g. "google.com" and "google.com/" (the trailing slash!)
            if (hit.url().equals(url + "/") || (hit.url() + "/").equals(url)) {
                return false;
            }
        }
        return true; // we have a new and unique target!
    }

    /**
     * Checks if the URL is in the list of excluded results.
     *
     * @param url The URL to check.
     * @return {@code true} if the URL is excluded, {@code false} otherwise.
     */
    private boolean isExcluded(String url) {
        if (excludedResults == null) {
            return false;
        }
        int lastSlashIndex = url.lastIndexOf('/');
        String subdomain = url.substring(lastSlashIndex + 1);
        for (String result : excludedResults) {
            if (result.equals(subdomain)) {
                System.out.println("Excluded " + subdomain + " successfully!");
                return true;
            }
        }
        return false;
    }

    /**
     * Returns whether the first thread has finished its execution.
     *
     * @return {@code true} if the first thread has finished, {@code false} otherwise.
     */
    public static boolean isFirstThreadFinished() {
        return firstThreadFinished;
    }

    /**
     * Cancels the execution of the current {@code QueueConsumer} by setting {@code running} to false.
     */
    public void cancel() {
        running = false;
    }

    /**
     * Checks if the {@code QueueConsumer} is currently running.
     *
     * @return {@code true} if the consumer is running, {@code false} otherwise.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Gets the list of all tracked CompletableFutures.
     * @return The list of CompletableFutures.
     */
    public boolean allFuturesCompleted() {
        if (totalFutures.get() == 0) {
            return false;
        }
        return (completedFutures.get() == totalFutures.get());
    }
}