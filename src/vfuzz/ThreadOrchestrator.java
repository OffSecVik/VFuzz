package vfuzz;

import java.io.IOException;
import org.apache.http.HttpResponse;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadOrchestrator {

    private final String wordlistPath;
    private final AtomicInteger activeTasks = new AtomicInteger(0);
    private final List<CompletableFuture<HttpResponse>> tasks = new CopyOnWriteArrayList<>();
    private ExecutorService executor;
    private final int THREAD_COUNT;
    private int recursionDepthLimit = 5;
    private ConcurrentHashMap<Target, List<QueueConsumer>> consumerTasks = new ConcurrentHashMap<>();

    public ExecutorService getExecutor() {
        return executor;
    }

    public void addTask(CompletableFuture<HttpResponse> task) {
        tasks.add(task);
    }

    public ThreadOrchestrator(String wordlistPath, int threadLimit) throws IOException {
        this.wordlistPath = wordlistPath;
        this.THREAD_COUNT = threadLimit;
    }

    public void startFuzzing() {

        this.executor = Executors.newFixedThreadPool(THREAD_COUNT + 11); // plus one for Terminal Output

        TerminalOutput terminalOutput = new TerminalOutput();
        executor.submit(terminalOutput);

        WordlistReader wordlistReader = new WordlistReader(wordlistPath);
        Target initialTarget = new Target(ArgParse.getUrl(), 0, THREAD_COUNT, wordlistReader);

        // submitting the initial tasks to the executor
        List<QueueConsumer> consumersForURL = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            QueueConsumer consumerTask = new QueueConsumer(this, wordlistReader, ArgParse.getUrl(), 0);
            executor.submit(consumerTask);
            consumersForURL.add(consumerTask);
        }

        // making the Target object
        consumerTasks.put(initialTarget, consumersForURL);

        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            terminalOutput.shutdown();
            terminalOutput.setRunning(false);
            executor.shutdown();
            try {
                if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("Goodbye");
        }));
    }

    public void initiateRecursion(String newTargetUrl, int currentDepth) {
        if (currentDepth >= recursionDepthLimit) return; // if max recursion depth is hit, don't add target to list
        int newDepth = currentDepth + 1;
        int allocatedThreads = Math.max((THREAD_COUNT / 2) / (consumerTasks.size()), 1); // calculates
        if (QueueConsumer.isFirstThreadFinished()) {
            allocatedThreads = Math.max((THREAD_COUNT) / (consumerTasks.size() + 1), 1);
        }

        WordlistReader recursiveReader = new WordlistReader(wordlistPath);
        Target recursiveTarget = new Target(newTargetUrl, newDepth, allocatedThreads, recursiveReader);

        List<QueueConsumer> consumersForRecursiveURL = new ArrayList<>();
        // first make new threads for new target TODO: this calculation can be improved
        for (int i = 0; i < allocatedThreads; i++) {
            QueueConsumer recursiveConsumer = new QueueConsumer(this, recursiveReader, newTargetUrl, newDepth);
            Future<?> future = executor.submit(recursiveConsumer);
            consumersForRecursiveURL.add(recursiveConsumer);
        }
        consumerTasks.put(recursiveTarget, consumersForRecursiveURL);

        // then we remove threads from our targets:
        for (Map.Entry<Target, List<QueueConsumer>> entry : consumerTasks.entrySet()) {
            Target target = entry.getKey();
            String targetUrl = target.getUrl();
            List<QueueConsumer> consumers = entry.getValue();
            if (targetUrl.equals(ArgParse.getUrl())) { // handle our initial target, it gets the chunk of the resources
                //System.out.println("HANDLING INITIAL TARGET " + ArgParse.getUrl());
                while (consumers.size() > Math.max(THREAD_COUNT / 2, 1)) {
                    QueueConsumer consumer = consumers.remove(consumers.size() - 1); // remove from the end
                    consumer.cancel(); // attempt to cancel future
                }
            } else if (!targetUrl.equals(newTargetUrl)) {
                while (consumers.size() > allocatedThreads) {
                    QueueConsumer consumer = consumers.remove(consumers.size() - 1); // remove from the end
                    consumer.cancel(); // cancel consumer
                }
            }
            target.setAllocatedThreads(consumers.size());
        }
        System.out.println(Color.GREEN + "Initiating recursion: " + Color.RESET);
        printActiveThreadsByTarget();
    }

    public void awaitCompletion() throws InterruptedException {
        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
    }

    public void removeTargetFromList(String targetUrl) {
        // removing from consumerTasks
        for (Target target : new ArrayList<>(consumerTasks.keySet())) {
            if (target.getUrl().equals(targetUrl)) {
                List<QueueConsumer> consumers = consumerTasks.get(target);
                if (consumers != null) {
                    for (QueueConsumer consumer : consumers) {
                        consumer.cancel();
                    }
                }
                consumerTasks.remove(target);
                break;
            }
        }
    }

    public void redistributeThreads() { // assuming our original target will finish first, and we can now evenly redistribute threads
        System.out.println(Color.GREEN + "Redistributing threads:" + Color.RESET);
        int allocatedThreads = Math.max((THREAD_COUNT) / QueueConsumer.getActiveThreads(), 1); // again get number of threads to allocate to each target
        for (Map.Entry<Target, List<QueueConsumer>> entry : consumerTasks.entrySet()) {
            Target target = entry.getKey();
            List<QueueConsumer> consumers = entry.getValue();
            while (target.getAllocatedThreads() < allocatedThreads) {
                target.incrementAllocatedThreads();
                QueueConsumer fillConsumer = new QueueConsumer(this, target.getWordlistReader(), target.getUrl(), target.getRecursionDepth());
                executor.submit(fillConsumer);
                consumers.add(fillConsumer);
            }
        }
        printActiveThreadsByTarget();
    }

    public void printActiveThreadsByTarget() {
        consumerTasks.forEach((target, consumers) -> {
            int activeCount = (int) consumers.stream().filter(consumer -> consumer.isRunning()).count();
            int inactiveCount = (int) consumers.stream().filter(consumer -> !consumer.isRunning()).count();
            System.out.println(target.getUrl() + " has " + activeCount + " working and " + inactiveCount + " completed thread(s).");
        });
    }

    // simple shutdown method //TODO handle program shutdown (shutdown hook, all tasks finished)
    public void shutdown() {
        executor.shutdown();
    }

    public boolean isShutdown() {
        return executor.isShutdown();
    }
}