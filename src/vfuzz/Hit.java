package vfuzz;

import java.util.List;
import java.util.ArrayList;

public class Hit {

    private static List<Hit> hits = new ArrayList<>();
    private static int hitCounter = 0;

    private final String url; // this is the url for which a positive response was found. in VHOST mode, this is the value of the VHOST header.
    private final int statusCode;
    private final int length;

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

    public String getUrl() {
        return url;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public int getLength() {
        return length;
    }

    public static int getHitCount() {
        return hitCounter;
    }
}
