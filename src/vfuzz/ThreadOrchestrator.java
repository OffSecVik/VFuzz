package vfuzz;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ThreadOrchestrator {

	private final String wordlistPath;
	private final int THREAD_COUNT;
	private ExecutorService executor = null;
	private BlockingQueue<String> queue = new LinkedBlockingQueue<>();
	
	ThreadOrchestrator(String wordlistPath, int THREAD_COUNT) {
		this.wordlistPath = wordlistPath;
		this.THREAD_COUNT = THREAD_COUNT;
	}
	
	public void startFuzzing() {

		executor = Executors.newFixedThreadPool(THREAD_COUNT + 20); // one extra for WordlistReader, one for TerminalOutput, 18 for recursion??
		
		// Producer: reads the wordlist and puts it into the queue
		executor.submit(new WordlistReader(queue, wordlistPath));
		
		// Consumers: take payloads from the wordlist
		for (int i = 0; i < THREAD_COUNT; i++) {
			executor.submit(new QueueConsumer(this, queue, ArgParse.getUrl()));
		}
				
		// Terminal Output Thread
		TerminalOutput terminalOutput = new TerminalOutput();
		executor.submit(terminalOutput);
		
		// Shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			terminalOutput.shutdown();
			terminalOutput.setRunning(false);
			executor.shutdown();
			try {
				executor.awaitTermination(1, java.util.concurrent.TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println("Goodbye");
		}));
		
		
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
	
	public void initiateRecursion(String url) {
		
		QueueConsumer recursiveConsumer = new QueueConsumer(this, queue, url);
		System.out.println("Initiating recursion for url " + url + " under thread " + recursiveConsumer.threadNumber);
		// System.out.println("Thread number for recursion: " + recursiveConsumer.threadNumber);
		executor.submit(recursiveConsumer);
		//executor.submit(new QueueConsumer(this, queue, url));
	}
}
