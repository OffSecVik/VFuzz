package vfuzz.core;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import vfuzz.config.ConfigAccessor;
import vfuzz.network.request.ParsedHttpRequest;
import vfuzz.network.request.ParsedRequestFactory;
import vfuzz.network.request.WebRequestFactory;
import vfuzz.network.strategy.requestmode.RequestMode;
import vfuzz.network.request.StandardRequestFactory;
import vfuzz.network.WebRequester;
import vfuzz.operations.Hit;
import vfuzz.operations.Range;
import vfuzz.operations.Target;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

public class QueueConsumer implements Runnable {

    private final ThreadOrchestrator orchestrator;
    private final ExecutorService executor;

    private final WordlistReader wordlistReader;

    private final String baseTargetUrl;
    private final Target target;
    private final String url;

    private final boolean recursionEnabled;
    private final int recursionDepth;

    private volatile boolean running = true;
    private static boolean firstThreadFinished = false;

    private final Set<Range> excludedStatusCodes;
    private final Set<Range> excludedLength;
    private final List<String> excludedResults;

    private final boolean vhostMode;

    private WebRequestFactory webRequestFactory;

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

    private void reachedEndOfWordlist() {
        running = false;
        firstThreadFinished = true;
        if (ConfigAccessor.getConfigValue("recursionEnabled", Boolean.class) && target.setScanComplete()) {
            orchestrator.redistributeThreads();
        }
    }

    private void sendAndProcessRequest(HttpRequestBase request) {
        WebRequester.sendRequest(request, 250, TimeUnit.MILLISECONDS)
                .thenApplyAsync(response -> {
            try {
                parseResponse(response, request);
            } catch (Exception e) {
                // System.err.println("Error processing response for " + response.getStatusLine().getStatusCode() + " response: " + e.getMessage());
            }
            return response;
        }, executor)
        .exceptionally(ex -> null);
    }

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
                return; // TODO also consider a dynamic setting that compares similar responses and decides whether to ignore based on the result of the comparison
            }
        }

        new Hit(requestUrl, responseCode, responseContentLength); // Idea: make Hit object take a HttpRequest as argument, this way we could retain more information?
        System.out.println("Hit for " + request + " : " + responseCode);
        if (recursionEnabled && isRecursiveTarget(requestUrl)) {
            orchestrator.initiateRecursion(requestUrl, recursionDepth);
        }
    }

    private boolean isRecursiveTarget(String url) { // TODO rethink this method and why we need it
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

    public static boolean isFirstThreadFinished() {
        return firstThreadFinished;
    }

    public void cancel() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }
}