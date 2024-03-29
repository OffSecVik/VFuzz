package vfuzz;

import java.util.concurrent.CopyOnWriteArrayList;

public class Target {

    private static CopyOnWriteArrayList<Target> targets = new CopyOnWriteArrayList<>();

    private String url; // the url to fuzz
    private int recursionDepth; // the recursion depth at which this is fuzzed
    private int allocatedThreads;
    private WordlistReader wordlistReader;

    Target(String url, int recursionDepth) {
        this.url = url;
        this.recursionDepth = recursionDepth;
        targets.add(this);
    }
    Target(String url, int recursionDepth, int allocatedThreads, WordlistReader wordlistReader) {
        this.url = url;
        this.recursionDepth = recursionDepth;
        this.allocatedThreads = allocatedThreads;
        this.wordlistReader = wordlistReader;
        targets.add(this);
    }

    public static void removeTargetFromList(String url){
        targets.removeIf(target -> target.getUrl().equals(url));
    }

    public static CopyOnWriteArrayList<Target> getTargets() {
        return targets;
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

    public WordlistReader getWordlistReader() {
        return wordlistReader;
    }
}
