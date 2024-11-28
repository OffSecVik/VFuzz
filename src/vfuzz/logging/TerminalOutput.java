package vfuzz.logging;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import vfuzz.network.WebRequester;
import vfuzz.operations.Hit;
import vfuzz.operations.Target;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * The {@code TerminalOutput} class manages the real-time output of the VFuzz application
 * to the terminal. It updates progress bars, metrics, and hit information dynamically,
 * providing the user with live feedback about the fuzzing process.
 *
 * <p>Features:</p>
 * <ul>
 *     <li>Real-time updates of requests sent and responses processed progress bars.</li>
 *     <li>Displays metrics such as rate limits, request rates, and retry rates.</li>
 *     <li>Maintains and displays a list of discovered hits.</li>
 *     <li>Uses ANSI escape codes to redraw terminal output efficiently.</li>
 * </ul>
 *
 * <p>Design:</p>
 * <ul>
 *     <li>Runs in its own thread to avoid blocking the main fuzzing process.</li>
 *     <li>Utilizes {@code jline.Terminal} for terminal interaction.</li>
 *     <li>Handles thread-safe updates to shared output data.</li>
 * </ul>
 *
 * <p>Dependencies:</p>
 * <ul>
 *     <li>{@link Target} - For tracking requests and responses.</li>
 *     <li>{@link Hit} - For managing discovered hits.</li>
 *     <li>{@link WebRequester} - For retrieving rate-limiting information.</li>
 *     <li>{@link Metrics} - For tracking performance metrics.</li>
 * </ul>
 */
public class TerminalOutput implements Runnable {

    private String requestsSentProgressBar = "";
    private String responsesParsedProgressBar = "";

    private Terminal terminal;

    private volatile boolean running = true;

    private final ArrayList<String> output = new ArrayList<>();

    private final Set<String> hitsDisplayed = new HashSet<>();

    /**
     * Constructs a {@code TerminalOutput} instance.
     * Initializes the terminal and reserves space in the output buffer for progress bars and metrics.
     *
     * <p>If the terminal cannot be initialized, output defaults to standard output without
     * advanced terminal features.</p>
     */
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

    /**
     * Starts the real-time terminal output updates in a separate thread.
     * Continuously refreshes the terminal with the latest progress, metrics, and hits
     * until the {@code running} flag is set to {@code false}.
     */
    @Override
    public void run() {
        while (running) {
            makeAndPrintOutput();
        }
    }

    /**
     * Updates all output components (progress bars, metrics, and hits) and prints them to the terminal.
     * Moves the terminal cursor up to overwrite previous output and maintains a smooth live interface.
     */
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

        if (running) {
            moveUpAndDeleteLines(getOutputLineCount());
        }
    }

    /**
     * Moves the terminal cursor up by the specified number of lines and deletes those lines.
     * Uses ANSI escape codes for cursor movement.
     *
     * @param n The number of lines to move up.
     */
    private void moveUpAndDeleteLines(int n) {
        System.out.printf("\033[%dF",n);
    }

    /**
     * Calculates the total number of terminal lines occupied by the current output.
     * Accounts for multi-line wrapping caused by long strings.
     *
     * @return The total number of terminal lines used by the output.
     */
    private int getOutputLineCount() {
        int lineCount = 0;
        for (String line : output) {
            // Calculate how many terminal lines this string spans
            lineCount += Math.max(1, (line.length() + getTerminalWidth() - 1) / getTerminalWidth());
        }
        return lineCount;
    }

    /**
     * Updates the progress bar for requests sent.
     * Calculates the proportion of completed requests and visualizes it as a progress bar.
     */
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

    /**
     * Updates the progress bar for responses processed.
     * Calculates the proportion of successful responses and visualizes it as a progress bar.
     */
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

    /**
     * Prints the current output buffer to the terminal, line by line.
     */
    private void printOutput() {
        for (String line : output) {
            System.out.println(line);
        }
    }

    /**
     * Updates the list of discovered hits.
     * Adds new hits to the output buffer if they haven't been displayed yet.
     */
    private void updateHits() {
        for (Hit hit : Hit.getHits()) {
            String hitString = hit.toString();
            if (!hitsDisplayed.contains(hitString)) {
                output.add(hitString);
                hitsDisplayed.add(hitString);
            }
        }
    }

    /**
     * Updates various performance metrics, such as:
     * <ul>
     *     <li>Rate limit</li>
     *     <li>Attempted requests per second</li>
     *     <li>Successful requests per second</li>
     *     <li>Retry rate (with colored output based on severity)</li>
     * </ul>
     */
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

    /**
     * Determines the color to represent the retry rate based on its value.
     *
     * @param retryRate The retry rate as a percentage.
     * @return The ANSI color code corresponding to the retry rate severity.
     */
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

    /**
     * Retrieves the width of the terminal in columns.
     * Defaults to 80 columns if the terminal is not available.
     *
     * @return The terminal width in columns.
     */
    private int getTerminalWidth() {
        return terminal != null ? terminal.getSize().getColumns() : 80; // Default to 80 columns
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    /**
     * Stops the terminal output thread gracefully.
     * Clears the terminal and displays shutdown messages to the user.
     */
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
