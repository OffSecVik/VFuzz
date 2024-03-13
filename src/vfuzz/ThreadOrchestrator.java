package vfuzz;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
	
	private BlockingQueue<String> queueCopy() {
		BlockingQueue<String> copy = new LinkedBlockingQueue<>();
		for (String item : queue) {
			copy.add(item);
		}
		return copy;
	}
	
	public void startFuzzing() {

		executor = Executors.newFixedThreadPool(THREAD_COUNT + 20); // one extra for WordlistReader, one for TerminalOutput, 18 for recursion??
		
		// Producer: reads the wordlist and puts it into the original queue
		Future<?> wordlistReaderFuture = executor.submit(new WordlistReader(queue, wordlistPath));
		try {
			wordlistReaderFuture.get();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}
		
		// Consumers: take payloads from the wordlist
		BlockingQueue<String> firstQueue = queueCopy();
		//System.out.println("First queue code: " + firstQueue.hashCode());
		//System.out.println("First queue size: " + firstQueue.size());
		for (int i = 0; i < THREAD_COUNT; i++) {
			executor.submit(new QueueConsumer(this, firstQueue, ArgParse.getUrl()));
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
		BlockingQueue<String> deepCopy = queueCopy();
		// System.out.println("Recursive queue code: " + deepCopy.hashCode());
		// System.out.println("Recursive queue size: " + deepCopy.size());
		QueueConsumer recursiveConsumer = new QueueConsumer (this, deepCopy, url);
		executor.submit(recursiveConsumer);
		// System.out.println("Initiating recursion for url " + url + " under thread " + recursiveConsumer.threadNumber); //******
		//executor.submit(new QueueConsumer(this, queueCopy(), url));
	}
}
