package vfuzz.core;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import vfuzz.config.ConfigAccessor;
import vfuzz.network.ParsedHttpRequest;
import vfuzz.network.RequestMode;
import vfuzz.network.WebRequester;
import vfuzz.operations.Hit;
import vfuzz.operations.Range;
import vfuzz.operations.Target;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// TECHNICALLY there's no more queue but this still iterates over a wordlist.
public class QueueConsumer implements Runnable {

    private static int activeThreads;

    private final WordlistReader wordlistReader;
    private final String url;
    private final ThreadOrchestrator orchestrator;
    public static final int MAX_RETRIES = 20; // TODO: move this somewhere it makes sense, it's just here for metrics
    private final int recursionDepth;
    private volatile boolean running = true;
    private static boolean firstThreadFinished = false;
    private final Target target;

    public static final AtomicInteger successfulRequests = new AtomicInteger(); // tracking TOTAL successful requests


    public QueueConsumer(ThreadOrchestrator orchestrator, Target target) {
        activeThreads++;
        this.target = target;
        this.orchestrator = orchestrator;
        this.wordlistReader = target.getWordlistReader();
        this.url = target.getUrl();
        this.recursionDepth = target.getRecursionDepth();
    }

    @Override
    public void run() {
        if (!ConfigAccessor.getConfigValue("followRedirects", Boolean.class)) {
            standardMode();
        } else {
            requestFileMode();
        }
    }

    private void standardMode() {
        while (running) {
            String payload = wordlistReader.getNextPayload();
            if (payload == null) {
                reachedEndOfWordlist();
                break;
            }
            HttpRequestBase request = WebRequester.buildRequest(url, payload);
            sendAndProcessRequest(request);
        }
    }

    @SuppressWarnings("CommentedOutCode")
    private void requestFileMode() {
        ParsedHttpRequest rawRequest;
        try {
            rawRequest = new ParsedHttpRequest().parseHttpRequestFromFile(ConfigAccessor.getConfigValue("requestFilePath", String.class));
        } catch (IOException e) {
            System.err.println("There was an error parsing the request from the file:\n" + e.getMessage());
            return;
        }
        while (running) {
            String payload = wordlistReader.getNextPayload();
            if (payload == null) {
                reachedEndOfWordlist();
                /*
                Target.removeTargetFromList(url); //
                orchestrator.removeTargetFromList(url);
                if (ConfigAccessor.getConfigValue("recursionEnabled", Boolean.class)) {
                    orchestrator.redistributeThreads();
                }
                 */
                break;
            }
            ParsedHttpRequest rawCopy = new ParsedHttpRequest(rawRequest);
            HttpRequestBase request = WebRequester.buildRequestFromFile(rawCopy, payload);
            sendAndProcessRequest(request);
        }
    }

    private void reachedEndOfWordlist() {
        running = false;
        firstThreadFinished = true;
        activeThreads--;
        if (ConfigAccessor.getConfigValue("recursionEnabled", Boolean.class) && target.setScanComplete()) {
            orchestrator.redistributeThreads();
        }
    }

    private void sendAndProcessRequest(HttpRequestBase request) {
        CompletableFuture<HttpResponse> webRequestFuture = WebRequester.sendRequestWithRetry(request, MAX_RETRIES, 50, TimeUnit.MILLISECONDS);
        orchestrator.addTask(webRequestFuture);
        CompletableFuture<Void> task = webRequestFuture.thenApplyAsync(response -> {
            try {
                successfulRequests.incrementAndGet();
                //// System.out.println("Successful requests: " + successfulRequests);
                parseResponse(response, request); // TODO check second argument
                //System.out.println("Received response for " + payload);
            } catch (Exception e) {
                // System.err.println("Error processing response for " + response.getStatusLine().getStatusCode() + " response: " + e.getMessage());
            }
            return response;
        }, orchestrator.getExecutor()).thenRun(() -> {
            // This block is executed after processing the response
            //System.out.println("Completed request with payload " + payload);
        }).exceptionally(ex -> {
            Throwable rootCause = ex instanceof CompletionException ? ex.getCause() : ex; // checking if we have a wrapped exception for the line below
            if (!(rootCause instanceof RejectedExecutionException) && orchestrator.isShutdown()) { // this should only occur if we're in the shutdown hook, it's done to prevent terminal clogging
                System.err.println("Exception for request " + request.getURI().toString() + ": " + ex.getMessage());
            }
            return null;
        });
    }

    private void parseResponse(HttpResponse response, HttpRequestBase request) {
        // checking for the most likely exclusion conditions first to return quickly if the response is not a match. this is done to improve performance.
        // it also means we chain if conditions. not pretty, but performant?


        int responseCode = response.getStatusLine().getStatusCode();
        for (Range range : ArgParse.getExcludedStatusCodes()) {
            if (range.contains(responseCode)) {
                return;
            }
        }


        int responseContentLength = (int)response.getEntity().getContentLength();
        for (Range range : ArgParse.getExcludedLength()) {
            if (range.contains(responseContentLength)) {
                return;
            }
        }

        String requestUrl;
        if (ConfigAccessor.getConfigValue("requestMode", RequestMode.class) == RequestMode.VHOST) { // setting the requestUrl to the vhost value for simplicity
            requestUrl = request.getHeaders("HOST")[0].getValue(); // simply setting the requestUrl (which never changes in vhost mode) to the header value (which is the interesting field)
        } else {
            requestUrl = request.getURI().toString();
        }

        // checking for double hit:
        for (Hit hit : Hit.getHits()) {
            if (hit.url().equals(requestUrl)) {
                return;
            }
            if (hit.url().equalsIgnoreCase(requestUrl)) { //TODO make this optional, this ignores case insensitive double hits
                return; // TODO also consider a dynamic setting that compares similar responses and decides whether to ignore based on the result of the comparison
            }
        }

        // preparing this target for recursion:
        boolean thisIsRecursiveTarget = false;
        if (ConfigAccessor.getConfigValue("recursionEnabled", Boolean.class)) { // checking for a redundant hit which would make recursion fork every time it's encountered, e.g. "google.com" and "google.com/" (the trailing slash!)
            if (recursionRedundancyCheck(requestUrl)) {
                thisIsRecursiveTarget = true;
            }
        }

        // TODO: make Hit object take a HttpRequest as argument, this way we could retain more information?

        new Hit(requestUrl, response.getStatusLine().getStatusCode(), (int)response.getEntity().getContentLength());

        if (thisIsRecursiveTarget) {
            orchestrator.initiateRecursion(requestUrl, recursionDepth);
        }
    }

    private boolean recursionRedundancyCheck(String url) { // TODO rethink this method and why we need it
        if (url.equals(ConfigAccessor.getConfigValue("url", String.class) + "/")) { // this does fix the initial forking
            return false;
        }
        for (Hit hit : Hit.getHits()) {
            if (hit.url().equals(url + "/") || (hit.url() + "/").equals(url)) {
                return false;
            }
        }
        return true; // true means passed the check
    }

    public static boolean isFirstThreadFinished() {
        return firstThreadFinished;
    }

    public void cancel() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    public static int getActiveThreads() {
        return activeThreads;
    }
}