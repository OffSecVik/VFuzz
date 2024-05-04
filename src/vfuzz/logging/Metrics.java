package vfuzz.logging;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Metrics {

    private static double retryRate = 0;
    private static ScheduledExecutorService executor;

    /**
     * Update interval in milliseconds for how frequently metrics are refreshed.
     * A lower update interval increases the resolution and responsiveness of the metric data,
     * allowing the system to more quickly react to changes in behavior. However, it can also increase CPU load
     * due to more frequent execution of update tasks.
     */
    private static final long updateInterval = 10;

    /**
     * The size of the circular buffers used to store historical metric data.
     * Larger buffer sizes provide a smoother calculation of metrics over a longer period,
     * which can help dampen the effect of transient spikes in data.
     * However, larger buffers may delay the recognition of recent shifts in system behavior,
     * as the data is averaged over a more extended period.
     */
    private static final int BUFFER_SIZE = 100;

    private static final long[] requestsBuffer = new long[BUFFER_SIZE];
    private static final long[] successfulRequestsBuffer = new long[BUFFER_SIZE];
    private static final long[] retriesBuffer = new long[BUFFER_SIZE];

    // Indices for circular buffer
    private static int currentIndex = 0;

    // Total counters for metrics
    private static final AtomicLong totalRequests = new AtomicLong();
    private static final AtomicLong totalSuccessfulRequests = new AtomicLong();
    private static final AtomicLong totalRetries = new AtomicLong();



    public static synchronized void startMetrics() {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleAtFixedRate(Metrics::updateAll, 0, updateInterval, TimeUnit.MILLISECONDS);
        }
    }

    private static void updateAll() {
        updateMetrics();
        updateDynamicRateLimiter();
    }

    private static void updateMetrics() {
        currentIndex = (currentIndex + 1) % BUFFER_SIZE;

        // Reset the current index in the buffer
        requestsBuffer[currentIndex] = 0;
        successfulRequestsBuffer[currentIndex] = 0;
        retriesBuffer[currentIndex] = 0;
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

    public static double getRequestsPerSecond() {
        return calculateSum(requestsBuffer) / (BUFFER_SIZE * (updateInterval / 1000.0));
    }

    public static double getSuccessfulRequestsPerSecond() {
        return calculateSum(successfulRequestsBuffer) / (BUFFER_SIZE * (updateInterval / 1000.0));
    }

    public static double getRetriesPerSecond() {
        return calculateSum(retriesBuffer) / (BUFFER_SIZE * (updateInterval / 1000.0));
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
        double retriesPerSecond = getRetriesPerSecond();

        retryRate = (requestsPerSecond != 0) ? retriesPerSecond / requestsPerSecond : retriesPerSecond;
    }

    public static double getRetryRate() {
        return retryRate;
    }

    public static synchronized void stopMetrics() {
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }
}
