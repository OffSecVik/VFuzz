package vfuzz;

import java.util.List;
import java.util.ArrayList;

public class Hit {

    private static List<Hit> hits = new ArrayList<>();
    private static int hitCounter = 0;

    private final String url;
    private final int statusCode;
    private final int length;
    private boolean printed = false;

    public Hit(String url, int statusCode, int length) {
        this.url = url;
        this.statusCode = statusCode;
        this.length = length;
        addHit(this);
        hitCounter++;
    }

    private static synchronized void addHit(Hit hit) {
        hits.add(hit);
    }


    public String toString() {
        String whiteSpace = "";
        for (int i = 0; i < 50 - url.length(); i++) { // make this adapt to terminal size - is there a standard?
            whiteSpace += " ";
        }
        // String isVhost = (ArgParse.getRequestMode() == RequestMode.VHOST) ? " vhost" : ""; // String gymnastics for vhost mode // TODO: reimplement
        String isVhost = "";
        return "Found" + isVhost + ": " + url + whiteSpace + "(Status Code " + statusCode + ")" + "\t" + "(Length: " + length + ")";
    }

    public static List<Hit> getHits() {
        return hits;
    }

    public void setPrinted(boolean printed) {
        this.printed = printed;
    }

    public boolean isPrinted() {
        return printed;
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
