package vfuzz;

public class TerminalOutput implements Runnable {


	private volatile boolean running = true;
	
	@Override
	public void run() {
		while (running) {

			updateMetrics();
			updateDynamicRateLimiter();
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
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				running = false;
				Thread.currentThread().interrupt();
			}

		}
	}
	
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
	
	public void updateMetrics() {
		/*
		eraseToEOL();
		System.out.println("Requests per Second: " + Metrics.getRequestsPerSecond());
		System.out.println("Retries per Second: " + Metrics.getRetriesPerSecond());
		System.out.println("Failed Requests per Second: " + Metrics.getFailedRequestsPerSecond());
		 */

	}
	
	public void updatePayload() {
		eraseToEOL();
		System.out.println("Now fuzzing " + Metrics.getCurrentRequest());

	}
	
	private int getMetricsLines() { // calculates lines needed by metrics
		if (ArgParse.getMetricsEnabled()) {
			return 2;
		}
		return 0;
	}

	public void updateDynamicRateLimiter() {

	}
	
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
		moveCursorDownBegLine(Hit.getHitCount() + getMetricsLines());
	}
}
