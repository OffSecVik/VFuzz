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
        Target initialTarget = new Target(ArgParse.getUrl(), 0, wordlistReader);

        // submitting the initial tasks to the executor
        List<QueueConsumer> consumersForURL = new ArrayList<>();
        for (int i = 0; i < THREAD_COUNT; i++) {
            QueueConsumer consumerTask = new QueueConsumer(this, initialTarget);
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

    private void allocateThreads() {
        int numberOfTargets = Target.getActiveTargets();
        int[] allocatedThreads = new int[numberOfTargets];
        int availableThreads = THREAD_COUNT;
        int helper = QueueConsumer.isFirstThreadFinished() ? 0 : 1;
        if (!QueueConsumer.isFirstThreadFinished()) {
            allocatedThreads[0] = Math.max(THREAD_COUNT / 2, 1); // for our initial target, which gets the chunk of the resources
            availableThreads -= allocatedThreads[0];
        }
        int remainingThreads = availableThreads % (numberOfTargets - helper);
        // System.out.println("Total threads " + availableThreads + " remaining threads " + remainingThreads);
        for (int i = helper; i < numberOfTargets; i++) {
            allocatedThreads[i] = (availableThreads / (numberOfTargets - helper));
        }
        int index = 0;
        while (remainingThreads > 0) {
            allocatedThreads[(index + helper) % allocatedThreads.length] += 1;
            remainingThreads--;
            index++;

        }
        index = 0;
        for (Target target : Target.getTargets()) {
            if (!target.isScanComplete()) {
                target.setAllocatedThreads(allocatedThreads[index]);
                index++;
            }
        }
    }

    public void initiateRecursion(String newTargetUrl, int currentDepth) {
        if (currentDepth >= recursionDepthLimit) return; // if max recursion depth is hit, don't add target to list
        int newDepth = currentDepth + 1;

        // make new target and equip it with threads
        WordlistReader recursiveReader = new WordlistReader(wordlistPath);
        Target recursiveTarget = new Target(newTargetUrl, newDepth, recursiveReader);
        allocateThreads();
        List<QueueConsumer> consumersForRecursiveURL = new ArrayList<>();
        for (int i = 0; i < recursiveTarget.getAllocatedThreads(); i++) {
            QueueConsumer recursiveConsumer = new QueueConsumer(this, recursiveTarget);
            executor.submit(recursiveConsumer);
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
                    consumer.cancel(); // cancel the QueueConsumer
                }
            } else if (!targetUrl.equals(newTargetUrl)) {
                while (consumers.size() > target.getAllocatedThreads()) {
                    QueueConsumer consumer = consumers.remove(consumers.size() - 1); // remove from the end
                    consumer.cancel(); // cancel QueueConsumer
                }
            }
        }
        System.out.println(Color.GREEN + "Initiating recursion: " + Color.RESET);
        printActiveThreadsByTarget();
    }

    public void awaitCompletion() throws InterruptedException {
        CompletableFuture.allOf(tasks.toArray(new CompletableFuture[0])).join();
    }

    public void redistributeThreads() { // assuming our original target will finish first, and we can now evenly redistribute threads
        allocateThreads();
        // System.out.println(Color.GREEN + "Redistributing threads:" + Color.RESET);
        for (Map.Entry<Target, List<QueueConsumer>> entry : consumerTasks.entrySet()) {
            Target target = entry.getKey();
            if (target.isScanComplete()) continue; // find active targets
            List<QueueConsumer> consumers = entry.getValue();
            int activeConsumers = 0;
            for (QueueConsumer consumer : consumers) {
                if (consumer.isRunning()) {
                    activeConsumers++;
                }
            }
            int threads = 0;
            for (int i = 0; i < (target.getAllocatedThreads() - activeConsumers); i++) {
                QueueConsumer fillConsumer = new QueueConsumer(this, target);
                executor.submit(fillConsumer);
                consumers.add(fillConsumer);
                threads++;
            }
            // System.out.println("Adding " + threads + " threads to " + target.getUrl());
            // System.out.println("-----------" + target.getUrl() + " now has " + target.getAllocatedThreads() + " threads.");
        }
        // printActiveThreadsByTarget();
    }

    public void printActiveThreadsByTarget() {
        consumerTasks.forEach((target, consumers) -> {
            int activeCount = (int) consumers.stream().filter(consumer -> consumer.isRunning()).count();
            int inactiveCount = (int) consumers.stream().filter(consumer -> !consumer.isRunning()).count();
            System.out.println(target.getUrl() + (target.isScanComplete() ? "(finished)" :"") + " has " + activeCount + " working threads."); // and " + inactiveCount + " completed thread(s).");
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