package vfuzz.operations;

import vfuzz.core.WordlistReader;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;


/**
 * The {@code Target} class represents a fuzzing target in the fuzzer.
 * Each target includes information such as the URL to be fuzzed, the recursion depth,
 * the number of allocated threads, and whether the scan is complete.
 *
 * <p>Targets are stored in a thread-safe {@link CopyOnWriteArrayList} to ensure safe access
 * and modification in a multithreaded environment. The class also tracks the status of
 * each target and manages wordlist reading for the fuzzing process.
 */
public class Target {

    private static final CopyOnWriteArrayList<Target> targets = new CopyOnWriteArrayList<>();

    private final String url; // the url to fuzz
    private final int recursionDepth; // the recursion depth at which this is fuzzed
    private int allocatedThreads;
    private final WordlistReader wordlistReader;
    private final AtomicBoolean allocationComplete = new AtomicBoolean(false);
    public AtomicInteger successfulRequestCount = new AtomicInteger();

    /**
     * Checks if a target has been allocated CompletableFutures for each payload.
     *
     * @return {@code true} if the allocation is complete, {@code false} otherwise.
     */
    public boolean getAllocationComplete() {
        return allocationComplete.get();
    }

    /**
     * Retrieves the number of active (incomplete) targets.
     *
     * @return The number of active targets that have not yet been allocated CompletableFutures.
     */
    public static int getActiveTargets() {
        int activeTargets = 0;
        for (Target target : targets) {
            if (!target.getAllocationComplete()) {
                activeTargets++;
            }
        }
        return activeTargets;
    }


    /**
     * Constructs a new {@code Target} with the specified URL, recursion depth, and wordlist reader.
     *
     * <p>The new target is automatically added to the global list of targets.
     *
     * @param url The URL to be fuzzed.
     * @param recursionDepth The recursion depth for this target.
     * @param wordlistReader The wordlist reader for fuzzing payloads.
     */
    public Target(String url, int recursionDepth, WordlistReader wordlistReader) {
        this.url = url;
        this.recursionDepth = recursionDepth;
        this.wordlistReader = wordlistReader;
        targets.add(this);
    }

    public static CopyOnWriteArrayList<Target> getTargets() {
        return targets;
    }

    public String getUrl() {
        return url;
    }

    public int getRecursionDepth() {
        return recursionDepth;
    }

    public int getAllocatedThreads() {
        return allocatedThreads;
    }

    public void setAllocatedThreads(int allocatedThreads) {
        this.allocatedThreads = allocatedThreads;
    }

    public WordlistReader getWordlistReader() {
        return wordlistReader;
    }

    public boolean setAllocationComplete() {
        return allocationComplete.compareAndSet(false, true);
    }

    public void incrementSuccessfulRequestCount() {
        successfulRequestCount.incrementAndGet();
    }

    public boolean fuzzingComplete() {
        return successfulRequestCount.get() == wordlistReader.getWordlistSize();
    }
}
