package vfuzz.operations;

import org.apache.http.HttpResponse;
import vfuzz.config.ConfigAccessor;
import vfuzz.logging.Color;

import java.util.*;

/**
 * The {@code Hit} record represents a successful fuzzing attempt where a unique URL,
 * along with its HTTP status code and content length, is stored. Hits are tracked globally
 * in a thread-safe manner using a synchronized set.
 *
 * <p>This class provides functionality to store and retrieve unique hits, preventing duplicates,
 * and it also keeps track of the total number of hits.
 */
public record Hit(String url, HttpResponse response, String payload) {

    // A synchronized set that stores all unique hits
    private static final Map<Integer, Hit> hits = Collections.synchronizedMap(new LinkedHashMap<>());

    private static int hitCounter = 0;

    /**
     * Adds a hit to the global set if it is not already present. This ensures that only unique hits are recorded.
     *
     * @param url        The URL that was hit
     * @param response   The HTTP response that was received for the hit
     * @param payload    The payload used
     */
    public static void hitIfNotPresent(String url, HttpResponse response, String payload) {
        Hit newHit = new Hit(url, response, payload);
        synchronized (hits) {
            if (!hits.containsValue(newHit)) {
                hits.put(hitCounter, newHit);
                hitCounter++;
            }
        }
    }

    private int getContentLength() {
        if (response.getEntity() != null) {
            if (response.getEntity().getContentLength() != -1) {
                return (int)response.getEntity().getContentLength();
            }
        }
        return 0; // return 0 if there is no ContentLength header or no response body
    }

    private int getStatusCode() {
        return response.getStatusLine().getStatusCode();
    }


    @Override
    public String toString() {
        if (ConfigAccessor.getConfigValue(("requestMethod"), String.class).equals("POST")
                && ConfigAccessor.getConfigValue(("requestMode"), String.class).equals("FUZZ")) {
            return Color.YELLOW + "Hit for payload: " + payload + Color.RESET;
        }

        int statusCode = getStatusCode();
        String color;

        if (statusCode >= 100 && statusCode < 200) {
            color = Color.GREEN;
        } else if (statusCode >= 200 && statusCode < 300) {
            color = Color.GREEN;
        } else if (statusCode >= 300 && statusCode < 500) {
            color = Color.ORANGE;
        } else if (statusCode >= 500) {
            color = Color.RED;
        } else {
            color = Color.WHITE; // Fallback, z.B. für 4xx oder andere
        }

        return color + String.format("%-40s [Status Code: %d] [Length: %d]", url, statusCode, getContentLength()) + Color.RESET;
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