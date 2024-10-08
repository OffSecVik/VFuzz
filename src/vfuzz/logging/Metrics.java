package vfuzz.logging;

import vfuzz.core.ThreadOrchestrator;
import vfuzz.network.WebRequester;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * The {@code Metrics} class tracks and reports performance metrics related to requests,
 * successful requests, and retries within the application.
 *
 * <p>Metrics are updated at a fixed interval, stored in circular buffers for a
 * historical view, and used to calculate rates such as requests per second and retry rate.
 *
 * <p>Key features of this class include:
 * <ul>
 *     <li>Tracking the number of total requests, successful requests, and retries.</li>
 *     <li>Calculating the rate of requests, successful requests, and retries per second.</li>
 *     <li>Providing retry rates as a ratio of retries to requests.</li>
 *     <li>Updating metrics at a configurable interval.</li>
 * </ul>
 *
 * <p>This class uses a circular buffer to store recent history and averages the metrics over
 * time, ensuring that the system can quickly adapt to changes in behavior while keeping historical data.
 */
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

    // Counter for successive measuring points with increased retry rate
    private static int requestsWithIncident = 0;

    // Number of successive measuring points after which rate limiting starts. Acts as a buffer to prevent jitter
    private static final int incidentLimit = 20;

    // Declares minimum threshold of a problematic retry rate. Everything above this value is considered problematic
    private static final double acceptableRetryRate = 0.1;

    /**
     * Starts the scheduled task to update metrics and rate limits at regular intervals.
     * <p>This method is synchronized to ensure only one instance of the update task is running.
     */
    public static synchronized void startMetrics() {
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleAtFixedRate(Metrics::updateAll, 0, updateInterval, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stops the scheduled task that updates the metrics and rate limits.
     * <p>This method is synchronized to ensure that the task is safely shut down without leaving any running tasks.
     */
    public static synchronized void stopMetrics() {
        if (executor != null) {
            executor.shutdown();
            executor = null;
        }
    }

    /**
     * Updates both metrics and the dynamic rate limiter at each interval.
     * This method is run at a fixed rate and ensures that metrics are kept current.
     */
    private static void updateAll() {
        updateMetrics();
        updateDynamicRateLimiter();
    }

    /**
     * Updates the circular buffers for requests, successful requests, and retries,
     * and resets the current buffer index for the next interval.
     */
    private static void updateMetrics() {
        currentIndex = (currentIndex + 1) % BUFFER_SIZE;

        // Reset the current index in the buffer
        requestsBuffer[currentIndex] = 0;
        successfulRequestsBuffer[currentIndex] = 0;
        retriesBuffer[currentIndex] = 0;
    }

    /**
     * Increments the count of total requests and updates the circular buffer at the current index.
     */
    public static void incrementRequestsCount() {
        requestsBuffer[currentIndex]++;
        totalRequests.incrementAndGet();
    }

    /**
     * Increments the count of successful requests and updates the circular buffer at the current index.
     */
    public static void incrementSuccessfulRequestsCount() {
        successfulRequestsBuffer[currentIndex]++;
        totalSuccessfulRequests.incrementAndGet();
    }

    /**
     * Increments the count of retries and updates the circular buffer at the current index.
     */
    public static void incrementRetriesCount() {
        retriesBuffer[currentIndex]++;
        totalRetries.incrementAndGet();
    }

    /**
     * Calculates and returns the average number of requests per second over the duration of the buffer.
     *
     * @return The average requests per second.
     */
    public static double getRequestsPerSecond() {
        return calculateSum(requestsBuffer) / (BUFFER_SIZE * (updateInterval / 1000.0));
    }

    /**
     * Calculates and returns the average number of successful requests per second over the duration of the buffer.
     *
     * @return The average successful requests per second.
     */
    public static double getSuccessfulRequestsPerSecond() {
        return calculateSum(successfulRequestsBuffer) / (BUFFER_SIZE * (updateInterval / 1000.0));
    }

    /**
     * Calculates and returns the average number of retries per second over the duration of the buffer.
     *
     * @return The average retries per second.
     */
    public static double getRetriesPerSecond() {
        return calculateSum(retriesBuffer) / (BUFFER_SIZE * (updateInterval / 1000.0));
    }

    /**
     * Helper method to calculate the sum of elements in a given array.
     *
     * @param array The array to sum.
     * @return The sum of all elements in the array.
     */
    private static long calculateSum(long[] array) {
        long sum = 0;
        for (long value : array) {
            sum += value;
        }
        return sum;
    }

    /**
     * Updates the retry rate, which is calculated as the ratio of retries to requests over time.
     */
    private static void updateDynamicRateLimiter() {
        double requestsPerSecond = getRequestsPerSecond();
        double retriesPerSecond = getRetriesPerSecond();

        retryRate = (requestsPerSecond != 0) ? retriesPerSecond / requestsPerSecond : retriesPerSecond;

        if (retryRate > acceptableRetryRate) {
            requestsWithIncident++;

            if (requestsWithIncident >= incidentLimit) {
                WebRequester.decreaseFutureLimit();
                // System.out.println("Decreasing future limit to " + WebRequester.getFutureLimit());
            }
        } else {
            requestsWithIncident = 0;
            WebRequester.increaseFutureLimit();
            // System.out.println("Increasing future limit to " + WebRequester.getFutureLimit());
        }
    }

    public static double getRetryRate() {
        return retryRate;
    }

    public static long getTotalSuccessfulRequests() {
        return totalSuccessfulRequests.get();
    }
}
