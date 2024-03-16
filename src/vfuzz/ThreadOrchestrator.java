package vfuzz;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadOrchestrator {

	private final String wordlistPath;
	private final int THREAD_COUNT;
	private ExecutorService executor = null;
	private BlockingQueue<String> queue = new LinkedBlockingQueue<>();
	private int recursionDepthLimit = 5;
	private ConcurrentHashMap<String, AtomicInteger> taskAllocations = new ConcurrentHashMap<>(); // holds target and number of threads allocated
	private ConcurrentHashMap<String, List<Future<?>>> consumerTasks = new ConcurrentHashMap<>();

	ThreadOrchestrator(String wordlistPath, int THREAD_COUNT) {
		this.wordlistPath = wordlistPath;
		this.THREAD_COUNT = THREAD_COUNT;
	}

	private BlockingQueue<String> queueCopy() {
		return new LinkedBlockingQueue<>(queue); // TODO ensure that each copied queue retains all the elements
	}

	public void startFuzzing() {
		// starting the executor with the number of max threads
		executor = Executors.newFixedThreadPool(THREAD_COUNT + 200); // one extra for WordlistReader, one for TerminalOutput, 18 for recursion??

		// submit the wordlist reader and wait for its completion (waiting is crucial, otherwise recursive threads won't copy the full wordlist later on)
		Future<?> wordlistReaderFuture = executor.submit(new WordlistReader(queue, wordlistPath));
		try {
			wordlistReaderFuture.get();
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (ExecutionException e) {
			e.printStackTrace();
		}

		taskAllocations.put(ArgParse.getUrl(), new AtomicInteger(THREAD_COUNT)); // denoting the initial allocation

		// submit initial tasks TODO - make this a method
		BlockingQueue<String> firstQueue = queueCopy();
		//System.out.println("First queue code: " + firstQueue.hashCode()); // DEBUG PRINTS for queue size - keep these for now, need to test once again
		//System.out.println("First queue size: " + firstQueue.size()); // DEBUG PRINTS for queue size - keep these for now, need to test once again
		List<Future<?>> consumersForURL = new ArrayList<>();
		for (int i = 0; i < THREAD_COUNT; i++) {
			QueueConsumer consumerTask = new QueueConsumer(this, firstQueue, ArgParse.getUrl(), 0);
			Future<?> future = executor.submit(consumerTask);
			consumersForURL.add(future);
		}
		consumerTasks.put(ArgParse.getUrl(), consumersForURL);
		printActiveThreadsByTarget();
		// Terminal Output Thread
		TerminalOutput terminalOutput = new TerminalOutput();
		executor.submit(terminalOutput);

		// Shutdown hook
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			terminalOutput.shutdown();
			terminalOutput.setRunning(false);
			executor.shutdown();
			try {
				executor.awaitTermination(1, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			System.out.println("Goodbye");
		}));

		// await task completion
		try {
			if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
				executor.shutdown();
			}
		} catch (InterruptedException e){
			executor.shutdownNow(); // force shutdown
			Thread.currentThread().interrupt();
		}
	}

	public void initiateRecursion(String newTargetUrl, int currentDepth) {
		if (currentDepth >= recursionDepthLimit) return; // if max recursion depth is hit, don't add target to list
		int newDepth = currentDepth + 1;
		int allocatedThreads = Math.max(THREAD_COUNT / (taskAllocations.size() + 1), 1); // get one thread minimum?
		taskAllocations.put(newTargetUrl, new AtomicInteger(allocatedThreads));

		// make new threads for new target
		List<Future<?>> consumersForRecursiveURL = new ArrayList<>();
		BlockingQueue<String> recursiveQueue = queueCopy(); // we only need one queue copy per target
		for (int i = 0; i < allocatedThreads; i++) {
			QueueConsumer recursiveConsumer = new QueueConsumer(this, recursiveQueue, newTargetUrl, newDepth);
			Future<?> future = executor.submit(recursiveConsumer);
			consumersForRecursiveURL.add(future);
		}
		consumerTasks.put(newTargetUrl, consumersForRecursiveURL);


		// remove threads
		for (Map.Entry<String, List<Future<?>>> entry : consumerTasks.entrySet()) {
			String targetUrl = entry.getKey();
			List<Future<?>> futures = entry.getValue();

			if (!targetUrl.equals(newTargetUrl)) {
				while (futures.size() > allocatedThreads) {
					Future<?> future = futures.remove(futures.size() - 1); // remove from the end
					future.cancel(true); // attempt to cancel future
				}
			}
		}
	}

	/*
	public void initiateRecursion(String newTargetUrl, int currentDepth) {
		if (currentDepth >= recursionDepthLimit) return; // TODO - make this behave better (can't just skip the target)
		int newDepth = currentDepth + 1;
		int allocatedThreads = Math.max(THREAD_COUNT / (taskAllocations.size() + 1), 1); // get one thread minimum?
		taskAllocations.put(newTargetUrl, new AtomicInteger(allocatedThreads));

		// stop excess threads of previous targets
		for (Map.Entry<String, List<Future<?>>> entry : consumerTasks.entrySet()) {
			String targetUrl = entry.getKey();
			List<Future<?>> futures = entry.getValue();

			// killing excess threads of old targets
			int excess = futures.size() - allocatedThreads;
			for (int i = 0; i < excess; i++) {
				Future<?> future = futures.remove(futures.size() - 1); // remove from the end
				future.cancel(true); // attempt to cancel future
			}
		}
		// start new threads
		List<Future<?>> consumersForRecursiveURL = new ArrayList<>();
		for (int i = 0; i < allocatedThreads; i++) {
			QueueConsumer recursiveConsumer = new QueueConsumer(this, queueCopy(), newTargetUrl, newDepth);
			Future<?> future = executor.submit(recursiveConsumer);
			consumersForRecursiveURL.add(future);
		}
		consumerTasks.put(newTargetUrl, consumersForRecursiveURL);
		System.out.println("FINALLY HERE");
		printActiveThreadsByTarget();

		// System.out.println("Recursive queue code: " + deepCopy.hashCode());
		// System.out.println("Recursive queue size: " + deepCopy.size());
	}
	 */

	public int getActiveThreads() {
		int activeCount = 0;
		for (List<Future<?>> futureList : consumerTasks.values()) {
			for (Future<?> future: futureList) {
				if (!future.isDone()) {
					activeCount++;
				}
			}
		}
		return activeCount;
	}

	public void printActiveThreadsByTarget() {
		consumerTasks.forEach((targetUrl, futureList) -> {
			int activeCount = (int) futureList.stream().filter(future -> !future.isDone()).count();
			int inactiveCount = (int) futureList.stream().filter(future -> future.isDone()).count();
			System.out.println(targetUrl + " has " + activeCount + " active and " + inactiveCount + " inactive threads.");
		});
	}

}
