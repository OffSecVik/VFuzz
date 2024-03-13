package vfuzz;

import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.BlockingQueue;

public class QueueConsumer implements Runnable {
	
	private final BlockingQueue<String> queue;
	private final String url;
	private final ThreadOrchestrator orchestrator;
	public final int threadNumber;
	private static int threadCounter = 0;
	
	public QueueConsumer(ThreadOrchestrator orchestrator, BlockingQueue<String> queue, String url) {
		this.queue = queue;
		this.url = url;
		this.threadNumber = threadCounter;
		// System.out.println("Number of Consumers so far: " + threadCounter);
		this.orchestrator = orchestrator;
		threadCounter++;
	}

	@Override
	public void run() {
		WaitingIterator<String> iterator = new WaitingIterator<String>(queue);
		while (true) {
			String payload = iterator.next(); // this will block if the queue is empty
			if (payload.equals("EOF")) { // special marker to indicate the end of the queue
				break; // Frau Steinberger rotiert
			}
			
			// building the request
			HttpRequest request = WebRequester.buildRequest(url, payload);
			// sending the request
			HttpResponse<String> response = WebRequester.makeRequest(request);
			// parsing the response
			parseResponse(response);
		}
	}
	
	private void parseResponse(HttpResponse<String> response) {
		// Response Handling //
		if (response != null) {
			
			// checking for excluding the response due to flags:
			int statusCode = response.statusCode();
			int responseLength = response.body().length();
			if (this.threadNumber == 10) {
				System.out.println("Response for recursive thread " + 10 + ": \t" + response.uri().toString());
			}
			if (!excludeRequest(statusCode, responseLength)) {
				// System.out.printf("Found: %s\t\t(Status Code %d)\t(Length: %d)%n", response.uri(), statusCode, responseLength);
				new Hit(response.uri().toString(), statusCode, responseLength);
				// handle recursion right here
				if (ArgParse.getRecursionEnabled()) {
					System.out.println("Hit: " + response.uri().toString());
					orchestrator.initiateRecursion(response.uri().toString());
				}
			}
		}
	}
	
	private boolean excludeRequest(int statusCode, int responseLength) {
		return ArgParse.getExcludedStatusCodes().contains(statusCode) || ArgParse.getExcludedLength().contains(responseLength);
	}
}
