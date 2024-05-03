package vfuzz.core;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import vfuzz.config.ConfigAccessor;
import vfuzz.network.ParsedHttpRequest;
import vfuzz.network.strategy.requestmode.RequestMode;
import vfuzz.network.WebRequestFactory;
import vfuzz.network.WebRequester;
import vfuzz.operations.Hit;
import vfuzz.operations.Range;
import vfuzz.operations.Target;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

// TECHNICALLY there's no more queue but this still iterates over a wordlist.
public class QueueConsumer implements Runnable {

    private static int activeThreads;
    private final WordlistReader wordlistReader;
    private final String url;
    private final ThreadOrchestrator orchestrator;
    private final ExecutorService executor;
    private volatile boolean running = true;
    private static boolean firstThreadFinished = false;
    private final Target target;
    private final int recursionDepth;
    private final boolean recursionEnabled;
    private final Set<Range> excludedStatusCodes;
    private final Set<Range> excludedLength;
    private final boolean vhostMode;
    private final String baseTargetUrl;
    private final WebRequestFactory webRequestFactory;
    private final List<String> excludedResults;

    public QueueConsumer(ThreadOrchestrator orchestrator, Target target) {
        activeThreads++;
        this.target = target;
        this.orchestrator = orchestrator;
        this.executor = orchestrator.getExecutor();
        this.wordlistReader = target.getWordlistReader();
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
        this.baseTargetUrl = ConfigAccessor.getConfigValue("url", String.class) + "/";

        this.webRequestFactory = new WebRequestFactory();
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
            HttpRequestBase request = webRequestFactory.buildRequest(url, payload);
            sendAndProcessRequest(request);
        }
    }

    private void requestFileMode() {
        ParsedHttpRequest prototypeRequest;
        String payload;
        try {
            prototypeRequest = new ParsedHttpRequest().parseHttpRequestFromFile(ConfigAccessor.getConfigValue("requestFilePath", String.class));
        } catch (IOException e) {
            System.err.println("There was an error parsing the request from the file:\n" + e.getMessage());
            return;
        }
        while (running) {
            payload = wordlistReader.getNextPayload();
            if (payload == null) {
                reachedEndOfWordlist();
                break;
            }

            ParsedHttpRequest rawCopy = new ParsedHttpRequest(prototypeRequest);
            HttpRequestBase request = webRequestFactory.buildRequestFromFile(rawCopy, payload);
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

    public static int getActiveThreads() {
        return activeThreads;
    }
}