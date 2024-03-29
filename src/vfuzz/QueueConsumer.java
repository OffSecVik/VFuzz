package vfuzz;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.util.EntityUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// TECHNICALLY there's no more queue but this still iterates over a wordlist.
public class QueueConsumer implements Runnable {

    private WordlistReader wordlistReader;
    private String url;
    private ThreadOrchestrator orchestrator;
    public static int maxRetries = 20; // TODO: move this somewhere it makes sense, it's just here for metrics
    private int recursionDepth = 0;
    private volatile boolean running = true;

    public static AtomicInteger succesfulReqeusts= new AtomicInteger();


    public QueueConsumer(ThreadOrchestrator orchestrator, WordlistReader wordlistReader, String url, int recursionDepth) {
        this.orchestrator = orchestrator;
        this.wordlistReader = wordlistReader;
        this.url = url;
        this.recursionDepth = recursionDepth;
    }

    @Override
    public void run() {
        standardMode();
    }

    public void standardMode() {

        while (running) {
            String payload = wordlistReader.getNextPayload();
            // System.out.println("Payload: " + payload);
            if (payload == null) {
                break;
            }

            HttpRequestBase request = WebRequester.buildRequest(url, payload);

            CompletableFuture<HttpResponse> webRequestFuture = WebRequester.sendRequestWithRetry(request, maxRetries, 50, TimeUnit.MILLISECONDS);
            orchestrator.addTask(webRequestFuture);
            CompletableFuture<Void> task = webRequestFuture.thenApplyAsync(response -> {
                try {
                    // Here, you process the HTTP response

                    succesfulReqeusts.incrementAndGet();
                    //// System.out.println("Succesful requests: " + succesfulReqeusts);
                    parseResponse(response, url + payload); // TODO construct requestUrl more gracefully. -> requestBuilding?
                    //System.out.println("Received response for " + payload);
                } catch (Exception e) {
                    //System.err.println("Error processing response for payload " + payload + ": " + e.getMessage());
                }
                return response;
            }, orchestrator.getExecutor()).thenRun(() -> {
                // This block is executed after processing the response
                //System.out.println("Completed request with payload " + payload);
            }).exceptionally(ex -> {
                // This block handles any exceptions thrown during the request sending or response processing
                System.err.println("Exception for payload " + payload + ": " + ex.getMessage());
                return null;
            });
        }
    }

    private void parseResponse(HttpResponse response, String requestUrl) {
        // checking for the most likely exclusion conditions first to return quickly if the response is not a match. this is done to improve performance.
        // it also means we chain if conditions. not pretty, but performant?
        if (ArgParse.getExcludedStatusCodes().contains(response.getStatusLine().getStatusCode())) {
            return;
        }

        if (ArgParse.getExcludedLength().contains((int)response.getEntity().getContentLength())) {
            return;
        }

        // TODO: check for case insensitive double match (compare responses (Hit objects? all these comparisons probably eat CPU?))
        // checking for double hit:
        if (!ArgParse.getRequestMode().equals(RequestMode.VHOST)) { // in VHOST mode every Hit url will be the same TODO do we need this?
            for (Hit hit : Hit.getHits()) {
                if (hit.getUrl().equals(requestUrl)) {
                    return;
                }
            }
        }

        // preparing this target for recursion:
        boolean thisIsRecursiveTarget = false;
        if (ArgParse.getRecursionEnabled()) { // checking for a redundant hit which would make recursion fork every time it's encountered, e.g. "google.com" and "google.com/" (the trailing slash!)
            if (recursionRedundancyCheck(requestUrl)) {
                thisIsRecursiveTarget = true;
            }
        }

        // TODO: make Hit object take a HttpRequest as argument, this way we could retain more information?
        Hit hit = new Hit(requestUrl, response.getStatusLine().getStatusCode(), (int)response.getEntity().getContentLength());

        // TODO: initiate recursion here!
    }

    private boolean recursionRedundancyCheck(String url) { // TODO rethink this method and why we need it
        if (url.equals(ArgParse.getUrl() + "/")) { // this does fix the initial forking
            return false;
        }
        for (Hit hit : Hit.getHits()) {
            if (hit.getUrl().equals(url + "/") || (hit.getUrl() + "/").equals(url)) {
                return false;
            }
        }
        return true; // true means passed the check
    }
}
