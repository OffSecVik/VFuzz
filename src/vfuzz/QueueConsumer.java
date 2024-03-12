package vfuzz;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.BlockingQueue;

public class QueueConsumer implements Runnable {
	
	private final BlockingQueue<String> queue;
	
	public QueueConsumer(BlockingQueue<String> queue) {
		this.queue = queue;
	}

	@Override
	public void run() {
		try {
			while (true) {
				String payload = queue.take(); // this will block if the queue is empty
				if (payload.equals("EOF")) { // special marker to indicate the end of the queue
					break; // Frau Steinberger rotiert
				}
				
				// building the request
				HttpRequest request = WebRequester.buildRequest(payload);
				
				// sending the request
				// System.out.println("Testing " + request.uri());
				HttpResponse<String> response = WebRequester.makeRequest(request);
				
				// Response Handling //
				if (response != null) {
					
					// checking for excluding the response due to flags:
					int statusCode = response.statusCode();
					int responseLength = response.body().length();
					
					if (!excludeRequest(statusCode, responseLength)) {
						System.out.printf("Found: %s\t\t(Status Code %d)\t(Length: %d)%n", response.uri(), statusCode, responseLength);
					}
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
	
	private boolean excludeRequest(int statusCode, int responseLength) {
		return ArgParse.getExcludedStatusCodes().contains(statusCode) || ArgParse.getExcludedLength().contains(responseLength);
	}
	
}
