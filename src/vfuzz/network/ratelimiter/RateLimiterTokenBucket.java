package vfuzz.network.ratelimiter;

/**
 * The {@code RateLimiterTokenBucket} class implements a token bucket rate-limiting algorithm
 * to control the rate of requests. The rate limiter allows requests at a steady rate by providing
 * tokens at a specified refill rate per second. If no tokens are available, requests are blocked
 * until tokens become available.
 *
 * <p>The token bucket algorithm works by "refilling" tokens at a specified rate, and each request
 * consumes one token. The class can dynamically adjust the rate limit, enable or disable rate
 * limiting, and block until a token is available.
 *
 * <p>This class is thread-safe and ensures that concurrent requests are handled correctly
 * with synchronized methods. It can also block threads until tokens are available using
 * a busy-wait loop.
 */
public class RateLimiterTokenBucket {

    private int maxTokens;
    private int refillRatePerSecond;
    private long availableTokens;
    private long lastRefillTimestamp;
    private boolean enabled = true;

    /**
     * Constructs a new {@code RateLimiterTokenBucket} with the specified refill rate.
     *
     * @param refillRatePerSecond The number of tokens refilled per second, which also represents
     *                            the number of requests allowed per second.
     */
    public RateLimiterTokenBucket(int refillRatePerSecond) {
        this.maxTokens = refillRatePerSecond;
        this.refillRatePerSecond = refillRatePerSecond;
        this.availableTokens = 0;
        this.lastRefillTimestamp = System.currentTimeMillis();
    }

    /**
     * Sets the rate limit, which controls the number of tokens refilled per second.
     *
     * @param refillRatePerSecond The new rate limit value, equivalent to the number of requests per second.
     */
    public void setRateLimit(int refillRatePerSecond) { // synonymous to requests per second
        this.maxTokens = refillRatePerSecond;
        this.refillRatePerSecond = refillRatePerSecond;
    }

    /**
     * Attempts to acquire a token from the bucket.
     *
     * <p>This method first refills the bucket based on the elapsed time since the last refill.
     * If tokens are available, one token is consumed, and the request is allowed. Otherwise, the
     * request is rejected.
     *
     * @return {@code true} if a token is available and the request is allowed; {@code false} otherwise.
     */
    public synchronized boolean tokenAvailable() {
        if (!enabled) {
            return true;
        }
        refill();
        if (availableTokens > 0) { // are there tokens left?
            availableTokens--; // subtract a token
            return true; // signal we can consume a token
        }
        return false;
    }

    /**
     * Refills the token bucket based on the elapsed time since the last refill.
     *
     * <p>This method calculates how many tokens should be added based on the elapsed time
     * and adds them to the bucket, ensuring the bucket does not exceed its maximum capacity.
     */
    private void refill() {
        long now = System.currentTimeMillis();
        long elapsedTime = now - lastRefillTimestamp;
        long tokensToAdd = (elapsedTime / 1000) * refillRatePerSecond; // calculates number of tokens to refill
        if (tokensToAdd > 0) {
            availableTokens = Math.min(maxTokens,  availableTokens + tokensToAdd); // ensures the tokens top off at maxTokens
            lastRefillTimestamp = now; // updates
        }
    }

    /**
     * Blocks the calling thread until a token becomes available.
     *
     * <p>This method uses a busy-wait loop to block until a token is available. It waits
     * for a short time (100 ms) between attempts to acquire a token.
     */
    @SuppressWarnings("BusyWait")
    public void awaitToken() {
        if (enabled && maxTokens > 0) {
            while (!tokenAvailable()) {
                try {
                    Thread.sleep(100); // if this is too low there are weird issues and the requests throw IOExceptions
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public int getRateLimit() {
        return maxTokens;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void enable() {
        this.enabled = true;
    }

    public void disable() {
        this.enabled = false;
    }
}
