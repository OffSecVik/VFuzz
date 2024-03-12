package vfuzz;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.util.concurrent.BlockingQueue;

public class WordlistReader implements Runnable {
	private final BlockingQueue<String> queue;
	private final String path;
	
	public WordlistReader(BlockingQueue<String> queue, String path) {
		this.queue = queue;
		this.path = path;
	}
	
	@Override
	public void run() {
		try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
			String payload;
			while ((payload = reader.readLine()) != null) {
				queue.put(payload);
				if (ArgParse.getMetricsEnabled() ) {
					Metrics.incrementProducerCount(); // increments the requests
				}
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			e.printStackTrace();
		}
	}
}
