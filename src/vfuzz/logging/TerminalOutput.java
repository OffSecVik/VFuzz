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

    private String requestsSentProgressBar = "";
    private String responsesParsedProgressBar = "";

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
        for (int i = 0; i < 7; i++) {
            output.add(""); // Reserve space for 2 progress bars + 4 metrics
        }
    }

    @Override
    public void run() {
        while (running) {
            makeAndPrintOutput();
        }
    }

    public void makeAndPrintOutput() {

        updateRequestsSentProgressBar();

        updateResponsesProcessedProgressBar();

        updateHits();

        updateMetrics();

        printOutput();

        try {
            Thread.sleep(250);
        } catch (InterruptedException e) {
            running = false;
            Thread.currentThread().interrupt();
        }

        moveUpAndDeleteLines(getOutputLineCount());
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

    private void updateRequestsSentProgressBar() {
        int total = Target.getTotalRequestNumberToSend();
        int current = Target.getSentRequestsForAllTargets();
        int progressWidth = 30;
        int completed = (int) ((double) current / total * progressWidth);

        StringBuilder progressBar = new StringBuilder();
        progressBar.append("[").append("=".repeat(completed)).append(" ".repeat(progressWidth - completed)).append("]");
        progressBar.append(" ").append(current).append("/").append(total).append(" requests sent");

        requestsSentProgressBar = progressBar.toString();
        output.set(0, requestsSentProgressBar); // Update first line
    }

    private void updateResponsesProcessedProgressBar() {
        int total = Target.getTotalRequestNumberToSend();
        int current = Target.getSuccessfulRequestsForAllTargets();
        int progressWidth = 30;
        int completed = (int) ((double) current / total * progressWidth);

        StringBuilder progressBar = new StringBuilder();
        progressBar.append("[").append("=".repeat(completed)).append(" ".repeat(progressWidth - completed)).append("]");
        progressBar.append(" ").append(current).append("/").append(total).append(" responses processed");

        responsesParsedProgressBar = progressBar.toString();
        output.set(1, responsesParsedProgressBar); // Update second line
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

    public void updateMetrics() {
        String metrics1 = "Rate limit: " + WebRequester.getRateLimiter().getRateLimitPerSecond();
        output.set(2, metrics1);

        String metrics2 = "Attempted R/s:  " + Metrics.getRequestsPerSecond();
        output.set(3, metrics2);

        String metrics3 = "Successful R/s: " + Metrics.getSuccessfulRequestsPerSecond();
        output.set(4, metrics3);

        double retryRate = Metrics.getRetryRate() * 100;
        if (retryRate > 100) {
            retryRate = 100;
        }
        String retryRateString = String.format("%.3f", retryRate) + "%";
        String retryRateColor = getRetryRateColor(retryRate);

        String metrics4 = "\033[0KRetry rate:     " + Color.RESET + retryRateColor + retryRateString + Color.RESET;
        output.set(5, metrics4);
        output.set(6, "");

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
