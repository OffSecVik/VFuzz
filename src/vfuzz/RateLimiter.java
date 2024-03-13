package vfuzz;

public class RateLimiter {
	
	private final int maxTokens;
	private final int refillRatePerSecond;
	private long availableTokens;
	private long lastRefillTimestamp;
	private boolean enabled = false;
	
	
	public RateLimiter(int refillRatePerSecond) {
		this.maxTokens = refillRatePerSecond;
		this.refillRatePerSecond = refillRatePerSecond;
		this.availableTokens = maxTokens;
		this.lastRefillTimestamp = System.currentTimeMillis();
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


	public void awaitToken() {
		while (!tokenAvailable()) {
			try {
				Thread.sleep(100); // if this is too low there are weird issues and the requests throw IOExceptions
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}
	
	public void enable() {
		this.enabled = true;
	}
	
	public void disable() {
		this.enabled = false;
	}
}