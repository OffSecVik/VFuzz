package vfuzz;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Metrics {
	private static ScheduledExecutorService executor = null;
	
	// request metrics variables
	private static final AtomicLong producerCount = new AtomicLong(0); // ALL the count variables reset to 0 each second
	private static final AtomicLong requestCount = new AtomicLong(0);
	private static final AtomicLong retriesCount = new AtomicLong(0);
	private static final AtomicLong failedRequestsCount = new AtomicLong(0);
	private static double producedPerSecond = 0.0;
	private static double requestsPerSecond = 0.0;
	private static double retriesPerSecond = 0.0;
	private static double failedRequestsPerSecond = 0.0;
	private static String currentRequest;

	private static long updateInterval = 100;
	private static long getUpdateInterval() {
		return updateInterval;
	}

	public static synchronized void startMetrics() { // initializes the executor
		if (executor == null || executor.isShutdown()) {
			executor = Executors.newSingleThreadScheduledExecutor();
			executor.scheduleAtFixedRate(Metrics::updateAll, updateInterval, updateInterval, TimeUnit.MILLISECONDS); // (1) executes updateRequestsPerSecond (2) waits 1 second before it executes the task for the first time (3) period between consecutive task executions (4) specifies time unit for (2) and (3)
		}
	}
	
	public static synchronized void stopMetrics() {
		if (executor != null) {
			executor.shutdown();
			executor = null; // flush the executor down the toilet
		}
	}
	
	private static void updateAll() {
		updateProducedPerSecond();
		updateRequestsPerSecond();
		updateRetriesPerSecond();
		updateFailedRequestsPerSecond();
		updateDynamicRateLimiter();
	}

	private static void updateDynamicRateLimiter() {
		double requestsPerSecond = getRequestsPerSecond();
		double failedRequestsPerSecond = getFailedRequestsPerSecond(); // making new variables here because I was worried the class variables would change values amidst operation of this method
		double retriesPerSecond = getRetriesPerSecond();
		double failureRate = 0;
		double retryRate = 0;
		if (requestsPerSecond != 0) {
			failureRate = failedRequestsPerSecond / requestsPerSecond;
			retryRate = retriesPerSecond / requestsPerSecond;
		}
		System.out.println("\tfailure rate: " + String.format("%.3f", failureRate*100) + "%\t\tretry rate: " + String.format("%.3f", retryRate*100) + "%"); // print the rates in percent

		// dynamic logic for failure and retries:
		if (failureRate > 0.1 || retryRate > 0.2) { // throttle conditions: 10% of requests fail OR 20% of requests are retries
			WebRequester.enableRequestRateLimiter((int) (requestsPerSecond * ( 1 - failureRate)));
		} else if (failureRate < 0.0001 && retryRate < 0.1) { // turn the darn thing off if we have lower than 0.01% failure
			WebRequester.disableRequestRateLimiter();
		} else if (failureRate < 0.01 && retryRate < 0.05) { // speed back up if we're below 1% failure rate and 5% retry rate
			WebRequester.setRequestRateLimiter((int) (requestsPerSecond * (1 + failureRate)));
	}

	}

	private static void updateRequestsPerSecond() {
		// snapshot current count and calculate RPS
        requestsPerSecond = requestCount.get() * (1000 / updateInterval);
		requestCount.set(0); // reset count for next interval
	}

	private static void updateProducedPerSecond() {
        producedPerSecond = producerCount.get() * (1000 / updateInterval);
		producerCount.set(0);
	}

	private static void updateRetriesPerSecond() {
		retriesPerSecond = retriesCount.get()  * (1000 / updateInterval);
		retriesCount.set(0);
	}

	private static void updateFailedRequestsPerSecond() {
		failedRequestsPerSecond = failedRequestsCount.get() * (1000 / updateInterval);
		failedRequestsCount.set(0);
	}

	// request metrics methods
	public static void incrementRequestsCount() {
		requestCount.incrementAndGet();
	}

	public static void incrementRetriesCount() {
		retriesCount.incrementAndGet();
	}

	public static void incrementFailedRequestsCount() {
		failedRequestsCount.incrementAndGet();
	}

	public static void incrementProducerCount() {
		producerCount.incrementAndGet();
	}

	public static double getRequestsPerSecond() {
		// updateRequestsPerSecond();
		return requestsPerSecond;
	}

	public static double getRetriesPerSecond() {
		return retriesPerSecond;
	}

	public static double getFailedRequestsPerSecond() {
		return failedRequestsPerSecond;
	}

	public static double getProducedPerSecond() {
		return producedPerSecond;
	}
	
	public static void setCurrentRequest(String request) {
		currentRequest = request;
	}
	
	public static String getCurrentRequest() {
		return currentRequest;
	}

	public static void shutdown() {
		executor.shutdown();
	}
}
