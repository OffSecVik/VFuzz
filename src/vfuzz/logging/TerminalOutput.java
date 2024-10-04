package vfuzz.logging;

import vfuzz.network.WebRequester;
import vfuzz.config.ConfigManager;

@SuppressWarnings("CommentedOutCode")
public class TerminalOutput implements Runnable {

    private ConfigManager config = ConfigManager.getInstance();

    private volatile boolean running = true;

    @SuppressWarnings("CommentedOutCode")
    @Override
    public void run() {
        while (running) {

            if (config.getConfigValue("metricsEnabled").equals("true")) {
                updateMetrics();
            }

            // updateDynamicRateLimiter();

			/*
			updateOutput();
			if (ArgParse.getMetricsEnabled()) {
				updatePayload();
			}
			returnCursorToTop();
			*/

            try {
                //noinspection BusyWait
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                running = false;
                Thread.currentThread().interrupt();
            }

        }
    }

    /*
    public void returnCursorToTop() {
        if (Hit.getHitCount() + getMetricsLines() > 0) {
            moveCursorUpBegLine(Hit.getHitCount() + getMetricsLines());
        }
    }

    public void returnCursorToBottom() {
        if (Hit.getHitCount() + getMetricsLines() > 0) {
            moveCursorDownBegLine(Hit.getHitCount() + getMetricsLines());
        }
    }

    public void updateOutput() {
        for (Hit hit : Hit.getHits()) {
            System.out.println(hit.toString());
        }
    }
     */

    public void updateMetrics() {
        System.out.println(Color.GRAY + "---------------------------------------------------------" + Color.RESET);
        System.out.println(Color.YELLOW_BOLD + "Rate Limit: " + Color.RESET + Color.WHITE_BOLD + "\t\t\t\t\t\t" + WebRequester.getRateLimiter().getRateLimitPerSecond() + Color.RESET);
        System.out.println(Color.BLUE_BOLD + "Attempted Requests per Second: " + Color.RESET + Color.WHITE_BOLD + "\t\t" + (int) Metrics.getRequestsPerSecond() + Color.RESET);
        System.out.println(Color.GREEN_BOLD + "Successful Requests per Second: " + Color.RESET + Color.WHITE_BOLD + "\t" + (int) Metrics.getSuccessfulRequestsPerSecond() + Color.RESET);
        System.out.println(Color.ORANGE_BOLD + "Retries per Second: " + Color.RESET + Color.WHITE_BOLD + "\t\t\t\t" + (int) Metrics.getRetriesPerSecond() + Color.RESET);

        double retryRate = Metrics.getRetryRate() * 100;
        String retryRateString = String.format("%.3f", retryRate) + "%";
        String retryRateColor = getRetryRateColor(retryRate);

        System.out.println("\t\t" + Color.ORANGE_BOLD + "Retry Rate: " + Color.RESET + retryRateColor + "\t\t\t\t" + retryRateString + Color.RESET);
        System.out.println();
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

    public void updatePayload() {
        eraseToEOL();
        // System.out.println("Now fuzzing " + Metrics.getCurrentRequest());
    }

    /* // TODO: reimplement
    private int getMetricsLines() { // calculates lines needed by metrics
        if (ArgParse.getMetricsEnabled()) {
            return 2;
        }
        return 0;
    }
     */

    private void clearScreen() {
        System.out.print("\033[J2");
    }

    private void moveUp(int n) {
        System.out.printf("\033[%dA", n);
    }

    private void moveDown(int n) {
        System.out.printf("\033[%dB", n);
    }

    private void eraseToEOL() {
        System.out.print("\033[0K");
    }

    private void moveCursorDownBegLine(int n) {
        System.out.printf("\033[%dE", n);
    }

    private void moveCursorUpBegLine(int n) {
        System.out.printf("\033[%dF", n);
    }

    public void setRunning(boolean running) {
        this.running = running;
    }

    public void shutdown() {
        running = false;
        // moveCursorDownBegLine(Hit.getHitCount() + getMetricsLines());
    }

}
