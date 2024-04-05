package vfuzz;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class VFuzz {
    public static void main(String[] args) {


        ConfigManager configManager = ConfigManager.getInstance();

        ArgParse.registerArguments();

        configManager.processArguments(args);

        // configManager.verifyRequiredArguments(); // TODO: Fix verifyRequiredArguments
        // configManager.applyDefaultValues(); // TODO: method is unnecessary.

        configManager.setConfigValue("rateLimit", "5"); // TODO: Find a solution for this case

        System.out.println(Color.BLUE + "Thread Count: " + ArgParse.getThreadCount());
        System.out.println("Wordlist Path: " + ArgParse.getWordlistPath());
        System.out.println("URL: " + ArgParse.getUrl());
        System.out.println("Excluded Status Codes: " + ArgParse.getExcludedStatusCodes().toString());
        System.out.println("Excluded Lengths: " + ArgParse.getExcludedLength());
        System.out.println("Request Mode: " + ArgParse.getRequestMode());
        System.out.println("Request Method: " + ArgParse.getRequestMethod());
        System.out.println("Max Retries: " + ArgParse.getMaxRetries());
        System.out.println("Rate Limit: " + ArgParse.getRateLimit());
        System.out.println("Rate Limiter Enabled: " + ArgParse.getRateLimiterEnabled());
        System.out.println("Metrics Enabled: " + ArgParse.getMetricsEnabled());
        System.out.println("Debug Enabled: " + ArgParse.getDebugEnabled());
        System.out.println("Recursion Enabled: " + ArgParse.getRecursionEnabled());
        System.out.println("User Agent: " + ArgParse.getUserAgent());
        System.out.println("Headers: " + ArgParse.getHeaders());
        System.out.println("Request File Fuzzing: " + ArgParse.getRequestFileFuzzing());
        System.out.println("Request File Path: " + ArgParse.getRequestFilePath());
        System.out.println("Fuzz Marker: " + ArgParse.getFuzzMarker() + Color.RESET);

        int threadCount = ArgParse.getThreadCount();
        String wordlistPath = ArgParse.getWordlistPath();

        try {
            ThreadOrchestrator orchestrator = new ThreadOrchestrator(wordlistPath, threadCount + 19);
            Metrics.startMetrics();
            orchestrator.startFuzzing();
            orchestrator.awaitCompletion();
        } catch (IOException ie) {
            System.out.println("orchestartor took shit.");
        }  catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Execution was interrupted.");
        }
    }
}