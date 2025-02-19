package vfuzz.logging;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import vfuzz.network.WebRequester;
import vfuzz.operations.Hit;
import vfuzz.operations.Target;

import java.io.IOException;
import java.util.ArrayList;

public class TerminalOutput implements Runnable {

    private volatile boolean running = true;

    private Terminal terminal;

    private int hitPrintIndex = 0;

    private ArrayList<String> temporaryOutput = new ArrayList<>();

    public TerminalOutput() {
        try {
            terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();
        } catch (IOException ignored) {}
    }

    @Override
    public void run() {
        while (running) {
            handleOutput();
        }
    }

    private void handleOutput() {
        printHits(15);
        printTemporaryOutput();
        waitForNextPrintCycle();
        clearTemporaryOutputInTerminal();
    }

    /**
     * Prints a fixed number of hits from the {@code hitQueue}.
     */
    private void printHits(int numberOfHitsToPrint) {
        try {
            for (int i = 0; i < numberOfHitsToPrint; i++) {
                if (hitPrintIndex + 1 > Hit.getHitCount()) {
                    return;
                }
                System.out.println(Hit.getHitMap().get(hitPrintIndex).toString());
                hitPrintIndex++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void printTemporaryOutput() {
        // TODO - make sure this works with adaptive terminal width
        clearTemporaryOutput(); // clear it before rebuilding
        buildTemporaryOutput();
        for (String s : temporaryOutput) {
            System.out.println(s);
        }
    }

    private void clearTemporaryOutputInTerminal() {
        moveUpAndDeleteLines(temporaryOutput.size()); // TODO - again make sure this works with small terminals
    }

    private void waitForNextPrintCycle() {
        try {
            Thread.sleep(125);
        } catch (InterruptedException e) {
            running = false;
            Thread.currentThread().interrupt();
        }
    }

    private String progressBar(int total, int current, int width, String message) {
        int completed = (int) ((double) current / total * width);

        StringBuilder progressBar = new StringBuilder();
        progressBar.append("[").append("=".repeat(completed)).append(" ".repeat(width - completed)).append("]");
        progressBar.append(" ").append(current).append("/").append(total).append(" " + message);
        return progressBar.toString();
    }

    private void buildTemporaryOutput() {
        buildMetrics();
        buildProgressBars();
        buildWarnings();
    }

    private void clearTemporaryOutput() {
        temporaryOutput.clear();
    }

    private void buildMetrics() {
        temporaryOutput.add("");
        temporaryOutput.add(
                "Rate limit: " + WebRequester.getRateLimiter().getRateLimitPerSecond()
        );
        temporaryOutput.add(
                "Attempted R/s:  " + Metrics.getRequestsPerSecond()
        );
        temporaryOutput.add(
                "Successful R/s: " + Metrics.getSuccessfulRequestsPerSecond()
        );
        double retryRate = Metrics.getRetryRate() * 100;
        if (retryRate > 100) {
            retryRate = 100;
        }
        temporaryOutput.add(
                "\033[0KRetry rate:     "
                + Color.RESET
                + getRetryRateColor(retryRate)
                + String.format("%.3f", retryRate) + "%"
                + Color.RESET
        );
    }

    private void buildProgressBars() {
        temporaryOutput.add(
                progressBar(
                        Target.getTotalRequestNumberToSend(),
                        Target.getSentRequestsForAllTargets(),
                        30,
                        "requests sent"
                )
        );
        temporaryOutput.add(
                progressBar(
                        Target.getTotalRequestNumberToSend(),
                        Target.getSuccessfulRequestsForAllTargets(),
                        30,
                        "responses processed"
                )
        );
    }

    private void buildWarnings() {
        if (Hit.getHitCount() > 50) {
            temporaryOutput.add(
                    Color.RED_BRIGHT
                    + "Warning: Suspicious number of positive results. Check your parameters?"
                    + Color.RESET
            );
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

    public void shutdown() {
        running = false;
        handleOutput(); // print one last time
        printExitMessage();
    }

    private void moveUpAndDeleteLines(int n) {
        // System.out.printf("\033[%dF",n);
        System.out.printf("\033[%dF\033[J", n);
    }

    private void printExitMessage() {
        String s = Target.getTargets().size() == 1 ? "target" : "targets";
        System.out.println(
                "\nAll fuzzing tasks are complete. Initiating shutdown...\n"
                + "Fuzzing completed after sending " + Metrics.getTotalSuccessfulRequests() + " requests to " + Target.getTargets().size() + " " + s + ".\n"
                + "Thank you for fuzzing with VFuzz."
        );
    }
}