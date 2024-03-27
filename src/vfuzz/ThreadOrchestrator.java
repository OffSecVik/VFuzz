package vfuzz;

import java.io.IOException;
import org.apache.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadOrchestrator {

    private final String wordlistPath;
    private final AtomicInteger activeTasks = new AtomicInteger(0);
    private final List<CompletableFuture<HttpResponse>> tasks = new CopyOnWriteArrayList<>();
    private ExecutorService executor;
    private final int THREAD_COUNT;
    private final String targetUrl;

    public ExecutorService getExecutor() {
        return executor;
    }

    public void addTask(CompletableFuture<HttpResponse> task) {
        tasks.add(task);
    }


    public ThreadOrchestrator(String wordlistPath, String targetUrl, int threadLimit) throws IOException {
        this.wordlistPath = wordlistPath;
        this.THREAD_COUNT = threadLimit;
        this.targetUrl = targetUrl;
        // Utilize a cached thread pool for handling asynchronous tasks without a fixed thread limit
        // Adjust according to your system's capabilities and task characteristics

    }

    public void startFuzzing() {
        this.executor = Executors.newFixedThreadPool(THREAD_COUNT);
        WordlistReader wordlistReader = new WordlistReader(wordlistPath);
        // submitting the initial tasks to the executor
        for (int i = 0; i < THREAD_COUNT; i++) {
            QueueConsumer consumerTask = new QueueConsumer(this, wordlistReader, targetUrl, 0);
            executor.submit(consumerTask);
        }
    }

    public void awaitCompletion() throws InterruptedException {
        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
    }

    // Example shutdown method to stop executor service gracefully
    public void shutdown() {
        executor.shutdown();
    }

    // Include additional methods for recursion, target management, etc., based on your requirements
}