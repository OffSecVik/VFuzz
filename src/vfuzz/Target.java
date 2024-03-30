package vfuzz;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class Target {

    private static CopyOnWriteArrayList<Target> targets = new CopyOnWriteArrayList<>();

    private final String url; // the url to fuzz
    private final int recursionDepth; // the recursion depth at which this is fuzzed
    private int allocatedThreads;
    private WordlistReader wordlistReader;
    private final AtomicBoolean scanComplete = new AtomicBoolean(false);

    public boolean isScanComplete() {
        return scanComplete.get();
    }

    public static int getActiveTargets() {
        int activeTargets = 0;
        for (Target target : targets) {
            if (!target.isScanComplete()) {
                activeTargets++;
            }
        }
        return activeTargets;
    }

    Target(String url, int recursionDepth, WordlistReader wordlistReader) {
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

    public boolean setScanComplete() {
        return scanComplete.compareAndSet(false, true);
    }
}
