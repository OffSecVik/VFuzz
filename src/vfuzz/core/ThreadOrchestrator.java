package vfuzz.core;

import vfuzz.config.ConfigAccessor;
import vfuzz.logging.Color;
import vfuzz.logging.Metrics;
import vfuzz.operations.Target;
import vfuzz.logging.TerminalOutput;
import vfuzz.network.strategy.requestmode.RequestMode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * The {@code ThreadOrchestrator} class manages the lifecycle of multiple fuzzing threads
 * in a multithreaded environment. It coordinates the thread allocation, handles recursion
 * when new targets are discovered, and ensures that resources are dynamically distributed
 * between active fuzzing targets. It also manages shutdown operations gracefully.
 *
 * <p>This class uses a {@link ConcurrentHashMap} to keep track of targets and their associated
 * {@link QueueConsumer} tasks, and it adjusts thread allocation based on task completion
 * and new target discovery.
 *
 * <p>A shutdown hook is registered to ensure proper shutdown and thread termination when the fuzzing process ends.
 */
public class ThreadOrchestrator {

    private final String wordlistPath;
    private ExecutorService executor;
    private ScheduledExecutorService scheduler;
    private final int THREAD_COUNT;
    private final ConcurrentHashMap<Target, List<QueueConsumer>> consumerTasks = new ConcurrentHashMap<>();

    /**
     * Initializes the {@code ThreadOrchestrator} with a wordlist path and thread limit.
     *
     * @param wordlistPath The path to the wordlist file used during fuzzing.
     * @param threadLimit  The maximum number of threads allowed for fuzzing.
     */
    public ThreadOrchestrator(String wordlistPath, int threadLimit) {
        this.wordlistPath = wordlistPath;
        this.THREAD_COUNT = threadLimit;
    }

    /**
     * Starts the fuzzing process by initializing the executor service, submitting {@link QueueConsumer}
     * tasks for the initial target, and registering a shutdown hook for graceful termination.
     *
     * <p>Each {@code QueueConsumer} consumes payloads from the wordlist and sends HTTP requests.
     * The initial target is created based on the URL fetched from the configuration.
     */
    public void startFuzzing() {
        try {
            this.executor = Executors.newFixedThreadPool(THREAD_COUNT + 1); // plus one for Terminal Output

            TerminalOutput terminalOutput = new TerminalOutput();
            executor.submit(terminalOutput);

            WordlistReader wordlistReader = new WordlistReader(wordlistPath);
            Target initialTarget = new Target(ConfigAccessor.getConfigValue("url", String.class), 0, wordlistReader);

            // Submit the initial tasks to the executor
            List<QueueConsumer> consumersForURL = new ArrayList<>();
            for (int i = 0; i < THREAD_COUNT; i++) {
                QueueConsumer consumerTask = new QueueConsumer(this, initialTarget);
                executor.submit(consumerTask);
                consumersForURL.add(consumerTask);
            }

            // Add the target and associated consumers to the task map
            consumerTasks.put(initialTarget, consumersForURL);

            // Register a shutdown hook for graceful termination
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                terminalOutput.shutdown();
                terminalOutput.setRunning(false);
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
                        executor.shutdownNow();
                    }
                } catch (InterruptedException e) {
                    //noinspection CallToPrintStackTrace
                    e.printStackTrace();
                }
                System.out.println("Goodbye");
            }));

            scheduleCompletionCheck();

        } catch (RuntimeException e) {
            System.err.println("Failed to start fuzzing due to an error: " + e.getMessage());
            //noinspection CallToPrintStackTrace
            e.printStackTrace();
        }
    }

    /**
     * Allocates available threads to active fuzzing targets. Ensures the initial target
     * gets more resources initially, and adjusts the allocation dynamically when new
     * targets are added or tasks complete.
     */
    private void allocateThreads() {
        int activeTargets = Target.getActiveTargets();
        int[] allocatedThreads = new int[activeTargets];
        int availableThreads = THREAD_COUNT;
        int helper = QueueConsumer.isFirstThreadFinished() ? 0 : 1;

        if (!QueueConsumer.isFirstThreadFinished()) {
            allocatedThreads[0] = Math.max(THREAD_COUNT / 2, 1); // for our initial target, which gets the chunk of the resources
            availableThreads -= allocatedThreads[0];
        }

        int extraThreads = availableThreads % (activeTargets - helper);
        for (int i = helper; i < activeTargets; i++) {
            // allocatedThreads[i] = (availableThreads / (activeTargets - helper));
            allocatedThreads[i] = Math.max((availableThreads / (activeTargets - helper)), 1); // this is the implementation for non-conservative recursive fuzzing where each new target gets one baseline thread
        }

        int index = 0;
        while (extraThreads > 0) {
            allocatedThreads[(index + helper) % allocatedThreads.length] += 1;
            extraThreads--;
            index++;
        }
        index = 0;
        for (Target target : Target.getTargets()) {
            if (!target.getAllocationComplete()) {
                target.setAllocatedThreads(allocatedThreads[index]);
                index++;
            }
        }
    }

    /**
     * Initiates recursive fuzzing when a new target URL is discovered, unless
     * the recursion depth limit is reached. The new target is assigned threads
     * from the existing pool, and the thread allocation is adjusted dynamically.
     *
     * @param newTargetUrl The newly discovered target URL.
     * @param currentDepth The current recursion depth.
     */
    public void initiateRecursion(String newTargetUrl, int currentDepth) {
        int recursionDepthLimit = 5;
        if (currentDepth >= recursionDepthLimit) return; // if max recursion depth is hit, don't add target to list
        int newDepth = currentDepth + 1;

        if (ConfigAccessor.getConfigValue("requestMode", RequestMode.class) == RequestMode.FUZZ) {
            newTargetUrl += "/FUZZ";
        }

        // Create a new target and allocate threads to it
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

        // Adjust the thread allocation for existing targets
        for (Map.Entry<Target, List<QueueConsumer>> entry : consumerTasks.entrySet()) {
            Target target = entry.getKey();
            String targetUrl = target.getUrl();
            List<QueueConsumer> consumers = entry.getValue();

            if (targetUrl.equals(ConfigAccessor.getConfigValue("url", String.class))) { // If the original target is still being fuzzed it gets the majority of resources
                while (consumers.size() > Math.max(THREAD_COUNT / 2, 1)) {
                    QueueConsumer consumer = consumers.remove(consumers.size() - 1); // remove from the end
                    consumer.cancel();
                }
            } else if (!targetUrl.equals(newTargetUrl)) {
                while (consumers.size() > target.getAllocatedThreads()) {
                    QueueConsumer consumer = consumers.remove(consumers.size() - 1); // remove from the end
                    consumer.cancel();
                }
            }
        }
        System.out.println(Color.GREEN + "Initiating recursion: " + Color.RESET);
        printActiveThreadsByTarget();
    }

    /**
     * Redistributes threads among active targets. This method assumes that the
     * original target finishes first, and then threads are reallocated to other
     * targets.
     */
    public void redistributeThreads() { // assuming our original target will finish first, and we can now evenly redistribute threads
        allocateThreads();
        for (Map.Entry<Target, List<QueueConsumer>> entry : consumerTasks.entrySet()) {
            Target target = entry.getKey();
            if (target.getAllocationComplete()) continue; // find active targets
            List<QueueConsumer> consumers = entry.getValue();
            int activeConsumers = 0;
            for (QueueConsumer consumer : consumers) {
                if (consumer.isRunning()) {
                    activeConsumers++;
                }
            }
            for (int i = 0; i < (target.getAllocatedThreads() - activeConsumers); i++) {
                QueueConsumer fillConsumer = new QueueConsumer(this, target);
                executor.submit(fillConsumer);
                consumers.add(fillConsumer);
            }
        }
    }

    /**
     * Prints the number of active threads for each target to the console.
     */
    @SuppressWarnings("unused")
    public void printActiveThreadsByTarget() {
        consumerTasks.forEach((target, consumers) -> {
            int activeCount = (int) consumers.stream().filter(QueueConsumer::isRunning).count();
            // int inactiveCount = (int) consumers.stream().filter(consumer -> !consumer.isRunning()).count();
            System.out.println(target.getUrl() + " has " + activeCount + " working threads." + (target.getAllocationComplete() ? " (finished creating CompletableFutures for this target.)" :"")); // and " + inactiveCount + " completed thread(s).");
        });
    }


    /**
     * Shuts down the executor service and quits the program.
     */
    public void shutdown() {
        executor.shutdown();
        System.exit(0);
    }

    public ExecutorService getExecutor() {
        return executor;
    }


    /**
     * Schedules a periodic task to check for fuzzing completion every 2 seconds.
     */
    private void scheduleCompletionCheck() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(() -> {
            if (Target.allTargetsAreFuzzed()) {
                System.out.println("\n\nAll fuzzing tasks are complete. Initiating shutdown...");
                String s = Target.getTargets().size() == 1 ? "target" : "targets";
                System.out.println("Fuzzing completed after sending " + Metrics.getTotalSuccessfulRequests() + " web requests to " + Target.getTargets().size() + " " + s + ".");
                System.out.println("Thank you for fuzzing with VFuzz.");
                shutdown();
                scheduler.shutdown(); // Stop the scheduler
            }
        }, 0, 2, TimeUnit.SECONDS); // Initial delay of 0, repeat every 2 seconds
    }
}