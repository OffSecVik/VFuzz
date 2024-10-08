package vfuzz.operations;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The {@code Hit} record represents a successful fuzzing attempt where a unique URL,
 * along with its HTTP status code and content length, is stored. Hits are tracked globally
 * in a thread-safe manner using a synchronized set.
 *
 * <p>This class provides functionality to store and retrieve unique hits, preventing duplicates,
 * and it also keeps track of the total number of hits.
 */
public record Hit(String url, int statusCode, int length) {

    // A synchronized set that stores all unique hits
    private static final Set<Hit> hits = Collections.synchronizedSet(new HashSet<>());

    private static int hitCounter = 0;

    /**
     * Adds a hit to the global set if it is not already present. This ensures that only unique hits are recorded.
     *
     * @param url        The URL that was hit.
     * @param statusCode The HTTP status code returned for the hit.
     * @param length     The content length of the response.
     */
    public static void hitIfNotPresent(String url, int statusCode, int length) {
        Hit newHit = new Hit(url, statusCode, length);
        synchronized (hits) {
            if (hits.add(newHit)) { // Set.add returns true if the element was added (i.e., it was not already present)
                hitCounter++;
                System.out.println(newHit);
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