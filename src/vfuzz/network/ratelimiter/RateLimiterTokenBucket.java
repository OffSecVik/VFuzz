package vfuzz.network.ratelimiter;

public class RateLimiterTokenBucket {

    private int maxTokens;
    private int refillRatePerSecond;
    private long availableTokens;
    private long lastRefillTimestamp;
    private boolean enabled = true; // TODO: don't forget you enabled this by default!

    public RateLimiterTokenBucket(int refillRatePerSecond) {
        this.maxTokens = refillRatePerSecond;
        this.refillRatePerSecond = refillRatePerSecond;
        this.availableTokens = 0;
        this.lastRefillTimestamp = System.currentTimeMillis();
    }

    public void setRateLimit(int refillRatePerSecond) { // synonymous to requests per second
        this.maxTokens = refillRatePerSecond;
        this.refillRatePerSecond = refillRatePerSecond;
    }

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

    private void refill() {
        long now = System.currentTimeMillis();
        long elapsedTime = now - lastRefillTimestamp;
        long tokensToAdd = (elapsedTime / 1000) * refillRatePerSecond; // calculates number of tokens to refill
        if (tokensToAdd > 0) {
            availableTokens = Math.min(maxTokens,  availableTokens + tokensToAdd); // ensures the tokens top off at maxTokens
            lastRefillTimestamp = now; // updates
        }
    }

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