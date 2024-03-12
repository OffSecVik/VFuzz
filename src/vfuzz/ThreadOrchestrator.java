package vfuzz;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThreadOrchestrator {

	private final String wordlistPath;
	private final int THREAD_COUNT;
	
	ThreadOrchestrator(String wordlistPath, int THREAD_COUNT) {
		this.wordlistPath = wordlistPath;
		this.THREAD_COUNT = THREAD_COUNT;
	}
	
	public void startFuzzing() {
		
		// add a thread for metrics if enabled
		int adjustedThreadCount = ArgParse.getMetricsEnabled() ? THREAD_COUNT + 1: THREAD_COUNT;
		ExecutorService executor = Executors.newFixedThreadPool(adjustedThreadCount);
		BlockingQueue<String> queue = new LinkedBlockingQueue<>();
		
		// Producer: reads the wordlist and puts it into the queue
		executor.submit(new WordlistReader(queue, wordlistPath));
		
		// Consumers: take payloads from the wordlist
		for (int i = 0; i < THREAD_COUNT; i++) { // keep one thread for the WordlistReader
			executor.submit(new QueueConsumer(queue));
		}
		
		// Metrics display thread
		if (ArgParse.getMetricsEnabled()) {
			TerminalOutput terminalOutput = new TerminalOutput();
				executor.submit(terminalOutput);
		}
		
		// shutdown executor after tasks submission
		executor.shutdown();
		
		try {
			// wait for all tasks to finish
			if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
				executor.shutdown();
			}
		} catch (InterruptedException e){
			executor.shutdownNow(); // force shutdown
			Thread.currentThread().interrupt();
		}
	}
}
