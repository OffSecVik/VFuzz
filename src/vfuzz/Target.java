package vfuzz;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;

public class Target {

    private static CopyOnWriteArrayList<Target> targets = new CopyOnWriteArrayList<>();

    private  BlockingQueue<String> queue; // the queue of this target
    private String url; // the url to fuzz
    private int recursionDepth; // the recursion depth at which this is fuzzed
    private int allocatedThreads;

    Target(BlockingQueue queue, String url, int recursionDepth) {
        this.queue = queue;
        this.url = url;
        this.recursionDepth = recursionDepth;
        targets.add(this);
    }
    Target(BlockingQueue queue, String url, int recursionDepth, int allocatedThreads) {
        this.queue = queue;
        this.url = url;
        this.recursionDepth = recursionDepth;
        this.allocatedThreads = allocatedThreads;
        targets.add(this);
    }

    public static void removeTargetFromList(String url){
        for (Target target : targets) {
            if (target.getUrl().equals(url)) {
                targets.remove(target);
            }
        }
    }

    public static CopyOnWriteArrayList<Target> getTargets() {
        return targets;
    }

    public static void setTargets(CopyOnWriteArrayList<Target> targets) {
        Target.targets = targets;
    }

    public BlockingQueue<String> getQueue() {
        return queue;
    }

    public void setQueue(BlockingQueue<String> queue) {
        this.queue = queue;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getRecursionDepth() {
        return recursionDepth;
    }

    public void setRecursionDepth(int recursionDepth) {
        this.recursionDepth = recursionDepth;
    }

    public int getAllocatedThreads() {
        return allocatedThreads;
    }

    public void setAllocatedThreads(int allocatedThreads) {
        this.allocatedThreads = allocatedThreads;
    }

    public void incrementAllocatedThreads() {
        allocatedThreads++;
    }
}
