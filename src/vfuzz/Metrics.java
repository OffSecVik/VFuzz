package vfuzz;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Metrics {
    private static ScheduledExecutorService executor = null;

    // request metrics variables
    private static final AtomicLong requestCount = new AtomicLong(0);
    private static final AtomicLong retriesCount = new AtomicLong(0);
    private static final AtomicLong failedRequestsCount = new AtomicLong(0);
    private static String currentRequest;
    private static double failureRate = 0;
    private static double retryRate = 0;
    private static long updateInterval = 100; // time in milliseconds, this is how often the thread updates its metrics
    private static double requestsPerSecond[] = new double[1000 / (int)updateInterval]; // calculating actual RPS through this
    private static double retriesPerSecond[] = new double[1000 / (int)updateInterval]; // calculating actual RPS through thisprivate static double
    private static double failedRequestsPerSecond[] = new double[1000 / (int)updateInterval];
    private static int timesUpdated = 0;


    public static synchronized void startMetrics() { // initializes the executor
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleAtFixedRate(Metrics::updateAll, updateInterval, updateInterval, TimeUnit.MILLISECONDS); // (1) executes updateAll (2) waits 1 second before it executes the task for the first time (3) period between consecutive task executions (4) specifies time unit for (2) and (3)
        }
    }

    public static synchronized void stopMetrics() {
        if (executor != null) {
            executor.shutdown();
            executor = null; // flush the executor down the toilet
        }
    }

    private static void updateAll() {

        updateRequestsPerSecond();
        updateRetriesPerSecond();
        updateFailedRequestsPerSecond();
        updateDynamicRateLimiter();
        updateShutdown();
        timesUpdated++;
    }

    private static void updateShutdown() {
        if (Target.getActiveTargets() == 0 && getRequestsPerSecond() == 0) {
            // TODO shutdown everything from this condition.
            // TODO find better solution for shutdown?
        }
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

    private static void updateRequestsPerSecond() {
        requestsPerSecond[timesUpdated % (1000 / (int)updateInterval)] = requestCount.get(); // places Requests/updateInterval in the array
        requestCount.set(0); // reset count for next interval
    }


    private static void updateRetriesPerSecond() {
        retriesPerSecond[timesUpdated % (1000 / (int)updateInterval)] = retriesCount.get();
        retriesCount.set(0);
    }

    private static void updateFailedRequestsPerSecond() {
        failedRequestsPerSecond[timesUpdated % (1000 / (int)updateInterval)] = failedRequestsCount.get();
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


    public static double getRequestsPerSecond() {
        // updateRequestsPerSecond();
        return Arrays.stream(requestsPerSecond).sum();
    }

    public static double getRetriesPerSecond() {
        return Arrays.stream(retriesPerSecond).sum();
    }

    public static double getFailedRequestsPerSecond() {
        return Arrays.stream(failedRequestsPerSecond).sum();
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

    public static double getFailureRate() {
        return failureRate;
    }

    public static double getRetryRate() {
        return retryRate;
    }
}
