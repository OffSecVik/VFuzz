package vfuzz.operations;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public record Hit(String url, int statusCode, int length) {

    private static final Set<Hit> hits = Collections.synchronizedSet(new HashSet<>());
    private static int hitCounter = 0;

    // Static method to add a hit if it's not already present
    public static void hitIfNotPresent(String url, int statusCode, int length) {
        Hit newHit = new Hit(url, statusCode, length);
        synchronized (hits) {
            if (hits.add(newHit)) { // Set.add returns true if the element was added (i.e., it was not already present)
                hitCounter++;
                System.out.println("New Hit added: " + newHit);
            }
        }
    }

    @Override
    public String toString() {
        return String.format("Found: %-40s (Status Code %d) (Length: %d)", url, statusCode, length);
    }

    public static Set<Hit> getHits() {
        return hits;
    }

    public static int getHitCount() {
        return hitCounter;
    }
}