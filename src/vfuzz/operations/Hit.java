package vfuzz.operations;

import org.apache.http.HttpResponse;
import vfuzz.config.ConfigAccessor;

import java.util.*;

/**
 * The {@code Hit} record represents a successful fuzzing attempt where a unique URL,
 * along with its HTTP status code and content length, is stored. Hits are tracked globally
 * in a thread-safe manner using a synchronized set.
 *
 * <p>This class provides functionality to store and retrieve unique hits, preventing duplicates,
 * and it also keeps track of the total number of hits.
 */
public record Hit(String url, int statusCode, int length, String payload) {

    // A synchronized set that stores all unique hits
    private static final Map<Integer, Hit> hits = Collections.synchronizedMap(new LinkedHashMap<>());

    private static int hitCounter = 0;

    /**
     * Adds a hit to the global set if it is not already present. This ensures that only unique hits are recorded.
     *
     * @param url        The URL that was hit.
     * @param response   The HTTP response that was received for the hit
     */
    public static void hitIfNotPresent(String url, HttpResponse response, String payload) {
        Hit newHit = new Hit(url, response.getStatusLine().getStatusCode(), (int) response.getEntity().getContentLength(), payload);
        synchronized (hits) {
            if (!hits.containsValue(newHit)) {
                hits.put(hitCounter, newHit);
                hitCounter++;
            }
        }
    }

    @Override
    public String toString() {
        if (ConfigAccessor.getConfigValue(("requestMethod"), String.class).equals("POST")
                && ConfigAccessor.getConfigValue(("requestMode"), String.class).equals("FUZZ")) {
            return "Hit for payload: " + payload;
        }
        return String.format("%-40s (Status Code: %d) (Length: %d)", url, statusCode, length);
    }

    private void printHitInfo() {
        System.out.print("Found");
        if ("VHOST".equals(ConfigAccessor.getConfigValue("requestMode", String.class))) {
            System.out.print(" vhost");
        }
        System.out.print(": ");
        System.out.println(this);
        if (ConfigAccessor.getConfigValue(("requestMethod"), String.class).equals("POST")
        && ConfigAccessor.getConfigValue(("requestMode"), String.class).equals("FUZZ")) {
            System.out.println("Payload:\t" + payload);
        }
        System.out.println();
    }

    public static Collection<Hit> getHits() {
        return hits.values();
    }

    public static Map<Integer, Hit> getHitMap() {
        return hits;
    }

    public static int getHitCount() {
        return hitCounter;
    }
}