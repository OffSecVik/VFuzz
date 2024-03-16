package vfuzz;
/*
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
*/
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Metrics {
	private static ScheduledExecutorService executor = null;
	
	// request metrics variables
	private static final AtomicLong requestCount = new AtomicLong(0); // thread safe incrementation of requests
	private static double requestsPerSecond = 0.0;
	private static String currentRequest;
	
	// producer metrics variables
	private static final AtomicLong producerCount = new AtomicLong(0);
	private static double producedPerSecond = 0.0;
	
	public static synchronized void startMetrics() { // initializes the executor
		if (executor == null || executor.isShutdown()) {
			executor = Executors.newSingleThreadScheduledExecutor();
			executor.scheduleAtFixedRate(Metrics::updateAll, 1, 1, TimeUnit.SECONDS); // (1) executes updateRequestsPerSecond (2) waits 1 second before it executes the task for the first time (3) period between consecutive task executions (4) specifies time unit for (2) and (3)
		}
	}
	
	public static synchronized void stopMetrics() {
		if (executor != null) {
			executor.shutdown();
			executor = null;
		}
	}
	
	private static void updateAll() {
		updateProducedPerSecond();
		updateRequestsPerSecond();
	}
	
	// request metrics methods
	public static void incrementRequestsCount() {
		requestCount.incrementAndGet();
	}
	
	private static void updateRequestsPerSecond() {
		// snapshot current count and calculate RPS
		long currentCount = requestCount.get(); // current number of requests counted since the last method call
		requestsPerSecond = (double) currentCount / 1; // dividing by one second
		requestCount.set(0); // reset count for next interval
	}


	public static double getRequestsPerSecond() {
		// updateRequestsPerSecond();
		return requestsPerSecond;
	}
	
	
	// implementing a feature to find out the rate at which we add to the queue
	public static void incrementProducerCount() {
		producerCount.incrementAndGet();
	}
	
	private static void updateProducedPerSecond() {
		long currentCount = producerCount.get();
		producedPerSecond = (double) currentCount / 1;
		producerCount.set(0);
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
