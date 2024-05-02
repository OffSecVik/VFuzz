package vfuzz.logging;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class Metrics2ReturnOfMetrics {

    private static double failureRate = 0;
    private static double retryRate = 0;
    private static ScheduledExecutorService executor;

    // Circular buffers for tracking metrics
    private static final int BUFFER_SIZE = 100; // size of the circular buffers
    private static final long[] requestsBuffer = new long[BUFFER_SIZE];
    private static final long[] successfulRequestsBuffer = new long[BUFFER_SIZE];
    private static final long[] retriesBuffer = new long[BUFFER_SIZE];
    private static final long[] failedRequestsBuffer = new long[BUFFER_SIZE];

    // Indices for circular buffer
    private static int currentIndex = 0;

    // Total counters for metrics
    private static final AtomicLong totalRequests = new AtomicLong();
    private static final AtomicLong totalSuccessfulRequests = new AtomicLong();
    private static final AtomicLong totalRetries = new AtomicLong();
    private static final AtomicLong totalFailedRequests = new AtomicLong();

    // Update interval in milliseconds
    private static final long updateInterval = 100;

    public static synchronized void startMetrics() {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleAtFixedRate(Metrics2ReturnOfMetrics::updateMetrics, 0, updateInterval, TimeUnit.MILLISECONDS);
        }
    }

    public static synchronized void stopMetrics() {
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }

    private static void updateMetrics() {
        currentIndex = (currentIndex + 1) % BUFFER_SIZE;

        // Reset the current index in the buffer
        requestsBuffer[currentIndex] = 0;
        successfulRequestsBuffer[currentIndex] = 0;
        retriesBuffer[currentIndex] = 0;
        failedRequestsBuffer[currentIndex] = 0;
    }

    public static void incrementRequestsCount() {
        requestsBuffer[currentIndex]++;
        totalRequests.incrementAndGet();
    }

    public static void incrementSuccessfulRequestsCount() {
        successfulRequestsBuffer[currentIndex]++;
        totalSuccessfulRequests.incrementAndGet();
    }

    public static void incrementRetriesCount() {
        retriesBuffer[currentIndex]++;
        totalRetries.incrementAndGet();
    }

    public static void incrementFailedRequestsCount() {
        failedRequestsBuffer[currentIndex]++;
        totalFailedRequests.incrementAndGet();
    }

    public static double getRequestsPerSecond() {
        return calculateSum(requestsBuffer) / (BUFFER_SIZE * (updateInterval / 1000.0));
    }

    public static double getSuccessfulRequestsPerSecond() {
        return calculateSum(successfulRequestsBuffer) / (BUFFER_SIZE * (updateInterval / 1000.0));
    }

    public static double getRetriesPerSecond() {
        return calculateSum(retriesBuffer) / (BUFFER_SIZE * (updateInterval / 1000.0));
    }

    public static double getFailedRequestsPerSecond() {
        return calculateSum(failedRequestsBuffer) / (BUFFER_SIZE * (updateInterval / 1000.0));
    }

    // Helper method to calculate sum of elements in an array
    private static long calculateSum(long[] array) {
        long sum = 0;
        for (long value : array) {
            sum += value;
        }
        return sum;
    }

    private static void updateDynamicRateLimiter() {
        double requestsPerSecond = getRequestsPerSecond();
        double failedRequestsPerSecond = getFailedRequestsPerSecond(); // making new variables here because I was worried the class variables would change values amidst operation of this method
        double retriesPerSecond = getRetriesPerSecond();
        if (requestsPerSecond != 0) {
            failureRate = failedRequestsPerSecond / requestsPerSecond;
            retryRate = retriesPerSecond / requestsPerSecond;
        } else {
            retryRate = 0;
        }

        /*
        // dynamic logic for failure and retries:
        if (failureRate > 0.1 || retryRate > 0.2) { // throttle conditions: 10% of requests fail OR 20% of requests are retries
            WebRequester.enableRequestRateLimiter((int) (requestsPerSecond * ( 1 - failureRate)));
        } else if (failureRate < 0.0001 && retryRate < 0.1) { // turn the darn thing off if we have lower than 0.01% failure
            WebRequester.disableRequestRateLimiter();
        } else if (failureRate < 0.01 && retryRate < 0.05) { // speed back up if we're below 1% failure rate and 5% retry rate
            WebRequester.setRequestRateLimiter((int) (requestsPerSecond * (1 + failureRate)));
        }
        */

    }

    public static double getFailureRate() {
        return failureRate;
    }

    public static double getRetryRate() {
        return retryRate;
    }
}
