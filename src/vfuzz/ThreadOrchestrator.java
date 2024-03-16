package vfuzz;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ThreadOrchestrator {

	private final String wordlistPath;
	private final int THREAD_COUNT;
	private ExecutorService executor = null;
	private BlockingQueue<String> queue = new LinkedBlockingQueue<>();
	private int recursionDepthLimit = 5;
	private ConcurrentHashMap<Target, AtomicInteger> taskAllocations = new ConcurrentHashMap<>(); // holds target and number of threads allocated
	private ConcurrentHashMap<Target, List<Future<?>>> consumerTasks = new ConcurrentHashMap<>(); // holds target and the number of futures allocated to it
	private List<String> completedScans = new CopyOnWriteArrayList<>();
	private TerminalOutput terminalOutput;

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

		// submit initial tasks TODO - make this a method
		BlockingQueue<String> firstQueue = queueCopy();
		// System.out.println("First queue code: " + firstQueue.hashCode()); // DEBUG PRINTS for queue size - keep these for now, need to test once again
		// System.out.println("First queue size: " + firstQueue.size()); // DEBUG PRINTS for queue size - keep these for now, need to test once again

		Target initialTarget = new Target(firstQueue, ArgParse.getUrl(), 0, THREAD_COUNT);
		taskAllocations.put(initialTarget, new AtomicInteger(THREAD_COUNT)); // denoting the initial allocation

		List<Future<?>> consumersForURL = new ArrayList<>();
		for (int i = 0; i < THREAD_COUNT; i++) {
			QueueConsumer consumerTask = new QueueConsumer(this, initialTarget);
			Future<?> future = executor.submit(consumerTask);
			consumersForURL.add(future);
		}
		consumerTasks.put(initialTarget, consumersForURL); // putting the initial target and its futures in a map for future reference (needed for recursion)

		printActiveThreadsByTarget();

		// Terminal Output Thread
		terminalOutput = new TerminalOutput();
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
	}

	public void initiateRecursion(String newTargetUrl, int currentDepth) {
		if (currentDepth >= recursionDepthLimit) return; // if max recursion depth is hit, don't add target to list
		int newDepth = currentDepth + 1;
		int allocatedThreads = Math.max((THREAD_COUNT / 2) / (taskAllocations.size()), 1); // calculates
		if (QueueConsumer.isFirstThreadFinished()) {
			allocatedThreads = Math.max((THREAD_COUNT) / (taskAllocations.size() + 1), 1);
		}
		Target recursiveTarget = new Target(queueCopy(), newTargetUrl, newDepth, allocatedThreads);
		taskAllocations.put(recursiveTarget, new AtomicInteger(allocatedThreads));

		// make new threads for new target
		List<Future<?>> consumersForRecursiveURL = new ArrayList<>();
		BlockingQueue<String> recursiveQueue = queueCopy(); // we only need one queue copy per targetSystem.out.println("First queue code: " + firstQueue.hashCode()); // DEBUG PRINTS for queue size - keep these for now, need to test once again
		// System.out.println("Recursive queue queue code: " + recursiveQueue.hashCode()); // DEBUG PRINTS for queue size - keep these for now, need to test once again
		// System.out.println("Recursive queue size: " + recursiveQueue.size()); // DEBUG PRINTS for queue size - keep these for now, need to test once again
		for (int i = 0; i < allocatedThreads; i++) {
			QueueConsumer recursiveConsumer = new QueueConsumer(this, recursiveTarget);
			Future<?> future = executor.submit(recursiveConsumer);
			consumersForRecursiveURL.add(future);
		}
		consumerTasks.put(recursiveTarget, consumersForRecursiveURL);

		// remove threads
		for (Map.Entry<Target, List<Future<?>>> entry : consumerTasks.entrySet()) {
			Target target = entry.getKey();
			String targetUrl = target.getUrl();
			List<Future<?>> futures = entry.getValue();
			if (targetUrl.equals(ArgParse.getUrl())) { // handle our initial target, it gets the chunk of the resources
				//System.out.println("HANDLING INITIAL TARGET " + ArgParse.getUrl());
				while (futures.size() > THREAD_COUNT / 2) {
					Future<?> future = futures.remove(futures.size() - 1); // remove from the end
					future.cancel(true); // attempt to cancel future
				}
			} else if (!targetUrl.equals(newTargetUrl)) {
				while (futures.size() > allocatedThreads) {
					Future<?> future = futures.remove(futures.size() - 1); // remove from the end
					future.cancel(true); // attempt to cancel future
				}
			}
			target.setAllocatedThreads(futures.size());
		}
		System.out.println(Color.GREEN + "Initiating recursion: " + Color.RESET);
		printActiveThreadsByTarget();
	}


	public void redistributeThreads() { // assuming our original target will finish first, and we can now evenly redistribute threads
		System.out.println(Color.GREEN + "Redistributing threads:" + Color.RESET);
		int allocatedThreads = Math.max((THREAD_COUNT) / (taskAllocations.size()), 1); // again get number of threads to allocate to each target
		for (Map.Entry<Target, List<Future<?>>> entry : consumerTasks.entrySet()) {
			Target target = entry.getKey();
			List<Future<?>> targetConsumers = entry.getValue();
			String targetUrl = target.getUrl();
			while (target.getAllocatedThreads() < allocatedThreads) {
				target.incrementAllocatedThreads();
				QueueConsumer fillConsumer = new QueueConsumer(this, target);
				Future<?> future = executor.submit(fillConsumer);
				targetConsumers.add(future);
				// System.out.println("Adding thread to " + targetUrl);
			}
		}
		printActiveThreadsByTarget();
	}

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
		consumerTasks.forEach((target, futureList) -> {
			int activeCount = (int) futureList.stream().filter(future -> !future.isDone()).count();
			int inactiveCount = (int) futureList.stream().filter(future -> future.isDone()).count();
			System.out.println(target.getUrl() + " has " + activeCount + " active and " + inactiveCount + " inactive threads.");
		});
	}

	public void addCompletedScan(String url) {
		completedScans.add(url);
	}

	public void removeTargetFromList(String targetUrl) {

		// removing from consumerTasks
		for (Target target : new ArrayList<>(consumerTasks.keySet())) {
			if (target.getUrl().equals(targetUrl)) {
				List<Future<?>> futures = consumerTasks.get(target);
				if (futures != null) {
					for (Future<?> future : futures) {
						future.cancel(true);
					}
				}
				consumerTasks.remove(target);
				break;
			}
		}

		// removing from taskAllocations
		for (Target target : new ArrayList<>(taskAllocations.keySet())) {
			if (target.getUrl().equals(targetUrl)) {
				taskAllocations.remove(target);
				break;
			}
		}
	}

	private volatile boolean goodbyeHasBeenSaid = false;

	public void shutdownExecutor() {
		executor.shutdown();
		try {
			if (!executor.awaitTermination(60, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
		}
		Metrics.shutdown();
		terminalOutput.setRunning(false);
		//executor.shutdownNow();
		if (!goodbyeHasBeenSaid) {
			System.out.println("Fuzzing finished. Thank you for fuzzing with VFuzz.");
			goodbyeHasBeenSaid = true;
		}

		//printActiveThreads();
	}

	public static void printActiveThreads() {
		ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
		while (rootGroup.getParent() != null) {
			rootGroup = rootGroup.getParent();
		}

		Thread[] threads = new Thread[rootGroup.activeCount()];
		while (rootGroup.enumerate(threads, true) == threads.length) {
			threads = new Thread[threads.length * 2];
		}

		for (Thread thread : threads) {
			if (thread != null) {
				System.out.println("Thread name: " + thread.getName() + " | State: " + thread.getState() + " | Is Daemon: " + thread.isDaemon());
			}
		}
	}
}

