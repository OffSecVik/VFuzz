package vfuzz.logging;

import org.jline.jansi.Ansi;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import vfuzz.config.ConfigManager;
import vfuzz.network.WebRequester;
import vfuzz.operations.Hit;
import vfuzz.operations.Target;

import java.io.IOException;

import java.util.concurrent.atomic.AtomicInteger;

public class TerminalOutput2 implements Runnable {


    private Terminal terminal;
    private volatile boolean running = true;
    private AtomicInteger linesPrinted = new AtomicInteger();
    private ConfigManager config = ConfigManager.getInstance();

    public TerminalOutput2() {
        try {
            // Initialize the terminal using TerminalBuilder
            terminal = TerminalBuilder.builder()
                    .system(true) // Use the system terminal
                    .build();
        } catch (IOException e) {
            e.printStackTrace();
            System.err.println("Failed to initialize the terminal. Falling back to System.out.");
        }
    }

    @Override
    public void run() {
        while (running) {
            try {
                clearScreen();
                //printMetrics();
                //printHits();
                moveCursorToTop();
                progressBar(Target.getTotalRequestNumberToSend(),Target.getSuccessfulRequestsForAllTargets());
                Thread.sleep(1000); // Refresh every second
            } catch (InterruptedException e) {
                running = false;
                Thread.currentThread().interrupt();
            }
        }
    }


    public void printMetrics() {
        if (config.getConfigValue("metricsEnabled").equals("false")) {
            return;
        }
        StringBuilder metrics = new StringBuilder();
        metrics.append(Ansi.ansi().fgBrightYellow().a("Metrics:").reset()).append("\n");
        metrics.append(Ansi.ansi().fgBlue().a("Future limit: ").reset())
                .append(WebRequester.getFutureLimit())
                .append("\n");
        metrics.append(Ansi.ansi().fgGreen().a("Rate Limit: ").reset())
                .append(WebRequester.getRateLimiter().getRateLimitPerSecond())
                .append("\n");

        print(metrics.toString());
    }

    public void print(String message) {
        if (terminal != null) {
            terminal.writer().print(message);
            terminal.writer().flush();
        } else {
            System.out.print(message); // Fallback to System.out
        }
    }

    public void progressBar(int total, int current) {
        int progressWidth = 30; // Width of the progress bar
        int completed = (int) ((double) current / total * progressWidth);
        StringBuilder progressBar = new StringBuilder();

        progressBar.append("[");
        for (int i = 0; i < progressWidth; i++) {
            if (i < completed) {
                progressBar.append("=");
            } else {
                progressBar.append(" ");
            }
        }
        progressBar.append("] ");
        progressBar.append(current).append("/").append(total);

        // Overwrite the first line with the progress bar
        overwriteLine(0, progressBar.toString());
    }


    public void overwriteLine(int lineNumber, String message) {
        if (terminal != null) {
            // Move the cursor to the specified line
            terminal.writer().print(Ansi.ansi().cursor(lineNumber, 0)); // Move to lineNumber, column 0
            // Clear the line
            terminal.writer().print(Ansi.ansi().eraseLine());
            // Write the message
            terminal.writer().print(message);
            terminal.writer().flush();
        } else {
            // Fallback to System.out (cannot overwrite lines in a plain console)
            System.out.println(message);
        }
    }


    public void printHits() {
        StringBuilder hits = new StringBuilder();
        for (Hit hit : Hit.getHits()) {
            hits.append(hit).append("\n");
        }
        print(hits.toString());
    }

    public void clearScreen() {
        System.out.print(Ansi.ansi().eraseScreen());
    }

    public void moveCursorToTop() {
        System.out.print(Ansi.ansi().cursor(0, 0));
    }

    public void shutdown() {
        running = false;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }
}
