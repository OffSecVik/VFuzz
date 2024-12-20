package vfuzz.core;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import vfuzz.config.ConfigAccessor;
import vfuzz.logging.Metrics;
import vfuzz.network.request.ParsedRequestFactory;
import vfuzz.network.request.WebRequestFactory;
import vfuzz.network.strategy.requestmode.RequestMode;
import vfuzz.network.request.StandardRequestFactory;
import vfuzz.network.WebRequester;
import vfuzz.operations.Hit;
import vfuzz.operations.Range;
import vfuzz.operations.Target;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;

/**
 * The {@code QueueConsumer} class is responsible for managing the fuzzing logic
 * for a specific target. It operates as a worker thread that:
 * <ul>
 *     <li>Reads payloads from a wordlist.</li>
 *     <li>Sends HTTP requests using various request strategies.</li>
 *     <li>Processes HTTP responses to identify hits or excluded results.</li>
 * </ul>
 *
 * <p>This class supports recursion to explore further targets discovered during fuzzing,
 * filtering results based on status codes, content lengths, excluded URLs, and previously
 * processed results.</p>
 *
 * <h2>Features:</h2>
 * <ul>
 *     <li>Handles multiple request modes (e.g., standard, subdomain fuzzing).</li>
 *     <li>Supports file extension fuzzing by appending extensions to payloads.</li>
 *     <li>Filters results based on excluded status codes, lengths, or URLs.</li>
 *     <li>Creates {@link Hit} objects for valid results.</li>
 *     <li>Enables recursion for deeper exploration of discovered resources.</li>
 * </ul>
 *
 * <p>This class is designed to run in a multi-threaded environment, with each instance
 * managing a specific {@link Target}. Threads are managed by the {@link ThreadOrchestrator}.</p>
 *
 * @see ThreadOrchestrator
 * @see Target
 * @see Hit
 */
public class QueueConsumer implements Runnable {

    private final ThreadOrchestrator orchestrator;
    private final ExecutorService executor;
    private final ExecutorService parsingExecutor;
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


    /**
     * Constructs a new {@code QueueConsumer} instance with the specified {@link ThreadOrchestrator} and {@link Target}.
     *
     * @param orchestrator The thread orchestrator managing the execution of threads.
     * @param target       The target being fuzzed by the current consumer.
     */
    public QueueConsumer(ThreadOrchestrator orchestrator, Target target) {

        this.orchestrator = orchestrator;
        this.executor = orchestrator.getExecutor();
        parsingExecutor = Executors.newFixedThreadPool(5);
        this.wordlistReader = target.getWordlistReader();
        this.baseTargetUrl = ConfigAccessor.getConfigValue("url", String.class);
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
     * Executes the main fuzzing logic for the current {@code QueueConsumer}.
     * Determines the request mode and starts the appropriate fuzzing method.
     */
    @Override
    public void run() {
        startFuzzing();
    }

    /**
     * Initiates the fuzzing process based on the configured request mode.
     * Either standard HTTP fuzzing or subdomain fuzzing is executed.
     */

    private void startFuzzing() {
        if (ConfigAccessor.getConfigValue("requestMode", RequestMode.class) == RequestMode.SUBDOMAIN) {
            fuzzSubdomains();
        } else {
            fuzzStandard();
        }
    }

    /**
     * Handles standard HTTP fuzzing by iterating over payloads from the wordlist.
     * Supports file extension fuzzing and handles parsed requests when enabled.
     *
     * <p>Requests are built and sent for each payload, and responses are processed.</p>
     */

    private void fuzzStandard() {
        if (ConfigAccessor.getConfigValue("requestFileFuzzing", String.class) == null) {
            webRequestFactory = new StandardRequestFactory(url);
        } else {
            webRequestFactory = new ParsedRequestFactory();
        }

        boolean fileFuzzingEnabled = false;

        String[] fileExtensions = null;
        if (ConfigAccessor.getConfigValue("fileExtensions", String.class) != null) {
            fileFuzzingEnabled = true;
            fileExtensions = ConfigAccessor.getConfigValue("fileExtensions", String.class).split(",");
        }

        while (running) {
            String payload = wordlistReader.getNextPayload();
            if (payload == null) {
                reachedEndOfWordlist();
                break;
            }

            if (fileFuzzingEnabled && fileExtensions.length > 0) {
                for (String extension : fileExtensions) {
                    HttpRequestBase request = webRequestFactory.buildRequest(payload);
                    String uri = String.valueOf(request.getURI());
                    request.setURI(URI.create(uri + extension));
                    sendAndProcessRequest(request, payload);
                }
            } else {
                HttpRequestBase request = webRequestFactory.buildRequest(payload);

                sendAndProcessRequest(request, payload);
            }
        }
    }

    /**
     * Handles subdomain fuzzing by using DNS queries with payloads from the wordlist.
     * Results are processed asynchronously, with valid subdomains logged as hits.
     *
     * <p>If no domain is provided, the method exits with an error message.</p>
     */
    private void fuzzSubdomains() {
        String domain = ConfigAccessor.getConfigValue("domainName", String.class);
        if (domain == null) {
            System.out.println("Please provide a domain to fuzz using the \"-d\" flag.");
            return;
        }

        DNSFuzzer fuzzer;
        try {
            fuzzer = new DNSFuzzer(domain, ConfigAccessor.getConfigValue("DNSServer", String.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        while (running) {

            String payload = wordlistReader.getNextPayload();
            if (payload == null) {
                reachedEndOfWordlist();
                break;
            }

            executor.submit(() -> {
                try {
                    Metrics.incrementRequestsCount();
                    fuzzer.fuzzAsync(payload).join(); // Ensure the CompletableFuture completes
                    target.incrementSuccessfulRequestCount();
                    Metrics.incrementSuccessfulRequestsCount();
                } catch (Exception e) {
                    System.err.println("Error fuzzing subdomain: " + e.getMessage());
                }
            });
        }
    }

    /**
     * Marks the end of the wordlist for the current target. Stops the consumer and,
     * if recursion is enabled, redistributes threads for further exploration.
     */
    private void reachedEndOfWordlist() {
        running = false;
        firstThreadFinished = true;
        if (ConfigAccessor.getConfigValue("recursionEnabled", Boolean.class) && target.setAllocationComplete()) {
            orchestrator.redistributeThreads();
        }
    }


    /**
     * Sends an HTTP request and processes the response asynchronously.
     * The response is parsed, and valid results are logged as hits.
     *
     * @param request The HTTP request to send.
     * @param payload The payload used to generate the request.
     */
    private void sendAndProcessRequest(HttpRequestBase request, String payload) {
        target.incrementSentRequestCount();
        WebRequester.sendRequest(request, 250, TimeUnit.MILLISECONDS)
                .thenApplyAsync(response -> {
            try {
                parseResponse(response, request, payload);
                target.incrementSuccessfulRequestCount(); // we can increment early since we send the request until it arrives!
            } catch (Exception ignored) {
            }
            return response;
        }, parsingExecutor)
                .exceptionally(ex -> null);
    }

    /**
     * Parses the HTTP response to determine if it matches any exclusion criteria
     * (e.g., status codes, content length, excluded URLs).
     * Valid responses are logged as hits, and recursion is initiated if enabled.
     *
     * @param response The HTTP response received.
     * @param request  The original HTTP request sent.
     * @param payload  The payload used to generate the request.
     */
    private void parseResponse(HttpResponse response, HttpRequestBase request, String payload) {

        int responseCode = response.getStatusLine().getStatusCode();
        for (Range range : excludedStatusCodes) {
            if (range.contains(responseCode)) {
                return;
            }
        }

        // checking for excluded status codes
        int responseContentLength = (int)response.getEntity().getContentLength();
        for (Range range : excludedLength) {
            if (range.contains(responseContentLength)) {
                return;
            }
        }

        // checking if we hit an excluded url
        String requestUrl = vhostMode ? request.getHeaders("HOST")[0].getValue() : request.getURI().toString();
        if (isExcluded(requestUrl)) {
            return;
        }

        // checking for double hit (mainly due to wordlists sometimes having slashes or whitespaces)
        if (isAlreadyHit(requestUrl)) {
            return;
        }

        Hit.hitIfNotPresent(requestUrl, response, payload);

        if (recursionEnabled) {
            orchestrator.initiateRecursion(requestUrl, recursionDepth);
        }
    }

    private boolean isBaseTargetUrl(String url) {
        String normalizedUrl = normalizeUrl(url);
        String normalizedBase = normalizeUrl(baseTargetUrl);

        // Check if the target URL is the same as the base URL
        return normalizedUrl.equals(normalizedBase);
    }

    /**
     * Normalizes a URL by removing any trailing slashes.
     *
     * @param url The URL to normalize.
     * @return The normalized URL.
     */
    private String normalizeUrl(String url) {
        if (url.endsWith("/")) {
            return url.substring(0, url.length() - 1);
        }
        return url;
    }

    /**
     * Checks whether the URL  has already been hit before.
     *
     * @param url The URL to check.
     * @return {@code true} if the URL is excluded or has already been hit, {@code false} otherwise.
     */
    private boolean isAlreadyHit(String url) {
        if ("POST".equals(ConfigAccessor.getConfigValue("requestMethod", String.class))) {
            return false;
        }

        String normalizedUrl = normalizeUrl(url);

        // Check if it's the base target URL
        if (isBaseTargetUrl(normalizedUrl)) {
            return true;
        }

        // Check if it's already been hit
        for (Hit hit : Hit.getHits()) {
            if (normalizeUrl(hit.url()).equals(normalizedUrl)) {
                return true;
            }
            if (ConfigAccessor.getConfigValue("ignoreCase", String.class).equals("true")) {
                if (normalizeUrl(hit.url()).equalsIgnoreCase(url)) {
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Determines if the given URL matches any explicitly excluded results.
     *
     * @param url The URL to check.
     * @return {@code true} if the URL is excluded, {@code false} otherwise.
     */
    private boolean isExcluded(String url) {
        if (excludedResults == null) {
            return false;
        }
        for (String excludedUrl : excludedResults) {
            if (url.equals(excludedUrl)) {
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

}