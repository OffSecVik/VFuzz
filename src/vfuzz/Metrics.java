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
	
	public static synchronized void startMetrics() { // initializes the executor
		if (executor == null || executor.isShutdown()) {
			executor = Executors.newSingleThreadScheduledExecutor();
			executor.scheduleAtFixedRate(Metrics::updateAll, 1, 1, TimeUnit.SECONDS); // (1) executes updateRequestsPerSecond (2) waits 1 second before it executes the task for the first time (3) period between consecutive task executions (4) specifies time unit for (2) and (3)
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
	}

	private static void updateRequestsPerSecond() {
		// snapshot current count and calculate RPS
        requestsPerSecond = requestCount.get();
		requestCount.set(0); // reset count for next interval
	}

	private static void updateProducedPerSecond() {
        producedPerSecond = producerCount.get();
		producerCount.set(0);
	}

	private static void updateRetriesPerSecond() {
		retriesPerSecond = retriesCount.get();
		retriesCount.set(0);
	}

	private static void updateFailedRequestsPerSecond() {
		failedRequestsPerSecond = failedRequestsCount.get();
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
