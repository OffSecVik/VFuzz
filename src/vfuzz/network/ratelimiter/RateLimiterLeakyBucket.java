package vfuzz.network.ratelimiter;

import java.util.concurrent.locks.LockSupport;

/**
 * The {@code RateLimiterLeakyBucket} class implements a leaky bucket rate-limiting algorithm
 * to control the rate of requests. This rate limiter ensures that requests do not exceed
 * a specified rate per second. If the limit is reached, requests will be blocked until
 * they can be processed.
 *
 * <p>The leaky bucket algorithm works by "leaking" requests at a constant rate. The class
 * tracks available space in the bucket (capacity) and ensures that requests do not exceed
 * the configured rate limit.
 *
 * <p>This class is thread-safe and ensures that concurrent requests are handled correctly
 * with synchronized methods. It provides methods to control rate limiting dynamically
 * by enabling or disabling it, and changing the rate limit.
 */
public class RateLimiterLeakyBucket {

    private int rateLimitPerSecond;       // rate at which requests are allowed (leak rate)
    private final long capacity;          // capacity of the bucket
    private long availableSpace;          // current available space in the bucket
    private long lastCheck;               // last time the bucket was checked
    private boolean enabled = true;       // controls whether rate limiting is enabled

    /**
     * Constructs a new {@code RateLimiterLeakyBucket} with the specified rate limit.
     *
     * @param rateLimitPerSecond The number of requests allowed per second.
     */
    public RateLimiterLeakyBucket(int rateLimitPerSecond) {
        this.rateLimitPerSecond = rateLimitPerSecond;
        this.capacity = rateLimitPerSecond;
        this.availableSpace = 0;  // start without any tokens
        this.lastCheck = 0;
    }

    /**
     * Attempts to request a token from the bucket.
     *
     * <p>This method checks the current available space in the bucket and calculates how much
     * space has been freed since the last check. If there is enough space available, the request
     * is allowed, and the space is reduced. Otherwise, the request is rejected.
     *
     * @return {@code true} if the request is allowed; {@code false} if the request exceeds the limit.
     */
    public synchronized boolean request() {
        if (!enabled) {
            return true;
        }

        if (lastCheck == 0) {
            lastCheck = System.currentTimeMillis();
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

    /**
     * Blocks the calling thread until a token is available in the bucket.
     *
     * <p>This method continuously attempts to acquire a token and uses {@link LockSupport#parkNanos(long)}
     * to pause the thread for 0.5 milliseconds between attempts.
     */
    public void awaitToken() {
        if (enabled) {
            while (!request()) {
                LockSupport.parkNanos(500000); // wait for .5 milliseconds // ToDo: this is likely not optimal.
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
