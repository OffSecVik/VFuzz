package vfuzz.operations;

import java.util.List;
import java.util.ArrayList;

/**
 * @param url this is the url for which a positive response was found. in VHOST mode, this is the value of the VHOST header.
 */
public record Hit(String url, int statusCode, int length) {

    private static final List<Hit> hits = new ArrayList<>();
    private static int hitCounter = 0;

    public Hit(String url, int statusCode, int length) {
        this.url = url;
        this.statusCode = statusCode;
        this.length = length;
        addHit(this);
        hitCounter++;
        System.out.println(this);
    }

    private static synchronized void addHit(Hit hit) {
        hits.add(hit);
    }

    public String toString() {
        // make this adapt to terminal size - is there a standard?
        // String isVhost = (ArgParse.getRequestMode() == RequestMode.VHOST) ? " vhost" : ""; // String gymnastics for vhost mode

        return "Found: " + url + " ".repeat(Math.max(0, 40 - url.length()))
                // String isVhost = (ArgParse.getRequestMode() == RequestMode.VHOST) ? " vhost" : ""; // String gymnastics for vhost mode
                + "(Status Code " + statusCode + ")" + " ".repeat(10) + "(Length: " + length + ")";
    }

    public static List<Hit> getHits() {
        return hits;
    }

    @SuppressWarnings("unused")
    public static int getHitCount() {
        return hitCounter;
    }
}
