package vfuzz;

import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

// TECHNICALLY there's no more queue but this still iterates over a wordlist.
public class QueueConsumer implements Runnable {

    private WordlistReader wordlistReader;
    private String url;
    private ThreadOrchestrator orchestrator;
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
        System.out.println("STANDARD MODE");
        while (running) {
            String payload = wordlistReader.getNextPayload();
            System.out.println("Payload: " + payload);
            if (payload == null) {
                break;
            }

            CompletableFuture<HttpResponse> webRequestFuture = WebRequester.sendRequest(url + payload);

            CompletableFuture<Void> task = webRequestFuture.thenApplyAsync(response -> {
                try {
                    // Here, you process the HTTP response
                    String responseBody = EntityUtils.toString(response.getEntity());
                    succesfulReqeusts.incrementAndGet();
                    //System.out.println("Succesful requests: " + succesfulReqeusts);
                    // System.out.println("Response for " + payload + ": " + responseBody);
                    //System.out.println("Received response for " + payload);
                    // Here, you could include logic to determine if it's a "hit" based on the response content
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
}
