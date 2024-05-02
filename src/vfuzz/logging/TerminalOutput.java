package vfuzz.logging;

import vfuzz.network.WebRequester;

@SuppressWarnings("CommentedOutCode")
public class TerminalOutput implements Runnable {


    private volatile boolean running = true;

    @SuppressWarnings("CommentedOutCode")
    @Override
    public void run() {
        while (running) {

            updateMetrics();
            // updateDynamicRateLimiter();
			/*
			if (ArgParse.getMetricsEnabled()) {
				updateMetrics();
			}
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
        System.out.println("---------------------------------------------------------");
        System.out.println("Rate Limit: " + WebRequester.getRateLimiter().getRateLimitPerSecond());
        System.out.println("Attempted Requests per Second: \t\t" + (int)Metrics.getRequestsPerSecond());
        System.out.println("Successful requests per Second: \t" + (int)Metrics.getSuccessfulRequestsPerSecond());
        System.out.println("Retries per Second: \t\t\t\t" + (int)Metrics.getRetriesPerSecond());
        System.out.println("\tfailure rate: " + String.format("%.3f", Metrics.getFailureRate()*100) + "%\t\tretry rate: " + String.format("%.3f", Metrics.getRetryRate()*100) + "%"); // print the rates in percent
        System.out.println();
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
