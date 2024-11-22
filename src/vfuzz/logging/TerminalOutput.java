package vfuzz.logging;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import vfuzz.network.WebRequester;
import vfuzz.config.ConfigManager;
import vfuzz.operations.Hit;
import vfuzz.operations.Target;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

public class TerminalOutput implements Runnable {

    private String progressBarLine = "";

    private Terminal terminal;

    private final ConfigManager config = ConfigManager.getInstance();

    private volatile boolean running = true;

    private final ArrayList<String> output = new ArrayList<>();

    private final Set<String> hitsDisplayed = new HashSet<>();

    public TerminalOutput() {
        try {
            terminal = TerminalBuilder.builder()
                    .system(true) // Use the system terminal
                    .build();
        } catch (IOException ignored) {
        }
    }

    @Override
    public void run() {
        while (running) {
            makeAndPrintOutput();
        }
    }

    public void makeAndPrintOutput() {

        moveUpAndDeleteLines(getOutputLineCount());

        updateProgressBar();

        updateHits();

        updateMetrics();

        printOutput();

        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            running = false;
            Thread.currentThread().interrupt();
        }
    }

    private void moveUpAndDeleteLines(int n) {
        System.out.printf("\033[%dF",n);
    }

    private int getOutputLineCount() {
        int lineCount = 0;
        for (String line : output) {
            // Calculate how many terminal lines this string spans
            lineCount += Math.max(1, (line.length() + getTerminalWidth() - 1) / getTerminalWidth());
        }
        return lineCount;
    }

    private void updateProgressBar() {
        int total = Target.getTotalRequestNumberToSend();
        int current = Target.getSuccessfulRequestsForAllTargets();
        int progressWidth = 30;
        int completed = (int) ((double) current / total * progressWidth);

        StringBuilder progressBar = new StringBuilder();
        progressBar.append("[");
        for (int i = 0; i < progressWidth; i++) {
            progressBar.append(i < completed ? "=" : " ");
        }
        progressBar.append("] ").append(current).append("/").append(total);

        // Update progress bar as the first line
        progressBarLine = progressBar.toString();
        if (output.isEmpty()) {
            output.add(progressBarLine);
        } else {
            output.set(0, progressBarLine);
        }
    }

    private void printOutput() {
        for (String line : output) {
            System.out.println(line);
        }
    }

    private void updateHits() {
        for (Hit hit : Hit.getHits()) {
            String hitString = hit.toString();
            if (!hitsDisplayed.contains(hitString)) {
                output.add(hitString);
                hitsDisplayed.add(hitString);
            }
        }
    }

    public void updateMetricsOld() {
        System.out.println(Color.GRAY + "---------------------------------------------------------" + Color.RESET);
        System.out.println("Future limit: " + WebRequester.getFutureLimit());

        // Print rate limit
        int rateLimit = WebRequester.getRateLimiter().getRateLimitPerSecond();
        String rateLimitString = rateLimit == 0 ? "-" : String.valueOf(rateLimit);
        System.out.println(Color.YELLOW_BOLD + "Rate Limit: " + Color.RESET + Color.WHITE_BOLD + "\t\t\t\t\t\t" + rateLimitString + Color.RESET);

        System.out.println(Color.BLUE_BOLD + "Attempted Requests per Second: " + Color.RESET + Color.WHITE_BOLD + "\t\t" + (int) Metrics.getRequestsPerSecond() + Color.RESET);
        System.out.println(Color.GREEN_BOLD + "Successful Requests per Second: " + Color.RESET + Color.WHITE_BOLD + "\t" + (int) Metrics.getSuccessfulRequestsPerSecond() + Color.RESET);
        System.out.println(Color.ORANGE_BOLD + "Retries per Second: " + Color.RESET + Color.WHITE_BOLD + "\t\t\t\t" + (int) Metrics.getRetriesPerSecond() + Color.RESET);

        double retryRate = Metrics.getRetryRate() * 100;
        String retryRateString = String.format("%.3f", retryRate) + "%";
        String retryRateColor = getRetryRateColor(retryRate);

        System.out.println("\t\t" + Color.ORANGE_BOLD + "Retry Rate: " + Color.RESET + retryRateColor + "\t\t\t\t" + retryRateString + Color.RESET);
        System.out.println();
    }

    public void updateMetrics() {
        String metrics1 = "Rate limit: " + WebRequester.getRateLimiter().getRateLimitPerSecond();
        if (output.size() > 1) {
            output.set(1, metrics1);
        } else {
            output.add(metrics1);
        }

        String metrics2 = "Attempted R/s:  " + Metrics.getRequestsPerSecond();
        if (output.size() > 2) {
            output.set(2, metrics2);
        } else {
            output.add(metrics2);
        }

        String metrics3 = "Successful R/s: " + Metrics.getSuccessfulRequestsPerSecond();
        if (output.size() > 3) {
            output.set(3, metrics3);
        } else {
            output.add(metrics3);
        }

        double retryRate = Metrics.getRetryRate() * 100;
        if (retryRate > 100) {
            retryRate = 100;
        }
        String retryRateString = String.format("%.3f", retryRate) + "%";
        String retryRateColor = getRetryRateColor(retryRate);

        String metrics4 = "\033[0KRetry rate:     " + Color.RESET + retryRateColor + retryRateString + Color.RESET;
        if (output.size() > 4) {
            output.set(4, metrics4);
        } else {
            output.add(metrics4);
        }

        if  (output.size() > 5) {
            output.set(5, "");
        } else {
            output.add("");
        }

    }

    private String getRetryRateColor(double retryRate) {
        if (retryRate >= 90) {
            return Color.RED;
        } else if (retryRate >= 70) {
            return Color.ORANGE;
        } else if (retryRate >= 50) {
            return Color.YELLOW;
        } else if (retryRate >= 30) {
            return Color.GREEN_BRIGHT;
        } else if (retryRate >= 10) {
            return Color.GREEN;
        } else {
            return Color.GREEN_BOLD;
        }
    }

    private int getTerminalWidth() {
        return terminal != null ? terminal.getSize().getColumns() : 80; // Default to 80 columns
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public void shutdown() {
        running = false;

        // Clear the terminal and prepare for shutdown messages
        moveUpAndDeleteLines(getOutputLineCount());

        // Append shutdown messages
        output.add("");
        output.add("All fuzzing tasks are complete. Initiating shutdown...");
        String s = Target.getTargets().size() == 1 ? "target" : "targets";
        output.add("Fuzzing completed after sending " + Metrics.getTotalSuccessfulRequests() + " requests to " + Target.getTargets().size() + " " + s + ".");
        output.add("Thank you for fuzzing with VFuzz.");

        printOutput();
    }
}
