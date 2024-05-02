package vfuzz.network.ratelimiter;

import java.util.concurrent.locks.LockSupport;

public class RateLimiterLeakyBucket {

    private int rateLimitPerSecond;       // rate at which requests are allowed (leak rate)
    private final long capacity;          // capacity of the bucket
    private long availableSpace;          // current available space in the bucket
    private long lastCheck;               // last time the bucket was checked
    private boolean enabled = true;       // controls whether rate limiting is enabled

    public RateLimiterLeakyBucket(int rateLimitPerSecond) {
        this.rateLimitPerSecond = rateLimitPerSecond;
        this.capacity = rateLimitPerSecond;
        this.availableSpace = 0;  // Start with full capacity
        this.lastCheck = System.currentTimeMillis();
    }

    public synchronized boolean request() {
        if (!enabled) {
            return true;
        }
        long now = System.currentTimeMillis();
        long elapsedTime = now - lastCheck;

        // Calculate the space that has been freed since the last check.
        long spaceFreed = elapsedTime * rateLimitPerSecond / 1000;
        availableSpace = Math.min(capacity, availableSpace + spaceFreed);
        lastCheck = now;

        // Check if there is enough space in the bucket to accommodate the incoming requests
        if (availableSpace >= 1) {
            availableSpace--;
            return true;
        }
        return false;
    }

    @SuppressWarnings("BusyWait")
    public void awaitToken() {
        if (enabled) {
            while (!request()) {
                LockSupport.parkNanos(500000);
            }
        }
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setRateLimitPerSecond(int rateLimitPerSecond) {
        this.rateLimitPerSecond = rateLimitPerSecond;
    }

    public int getRateLimitPerSecond() {
        return rateLimitPerSecond;
    }
}
