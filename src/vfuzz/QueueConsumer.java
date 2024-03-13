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

		while (true) {
			String payload = null;
			try {
				payload = queue.take();
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			if (payload.equals("EOF")) { // special marker to indicate the end of the queue
				break; // Frau Steinberger rotiert
			}
			
			// building the request
			HttpRequest request = WebRequester.buildRequest(url, payload);
			/* more debug prints for the recursive threads
			if (this.threadNumber == 11) {
				System.out.println("Testing " + request.uri() + " with payload " + payload);
			}
			*/
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
			String responseUrl = response.uri().toString();
			/* multithread debug adventures
			if (this.threadNumber == 10) {
				System.out.println("Response for recursive thread " + 10 + ": \t" + response.uri().toString());
			}
			*/
			if (excludeRequest(statusCode, responseLength)) {
				return;
			}
			
			// check if it's a false positive
			for (Hit hit : Hit.getHits()) { 
				if (hit.getUrl().equals(responseUrl)) {
					return;
				}
			}
			
			/*
			// check if there's a case insensitive match
			for (Hit hit : Hit.getHits()) {
				if (hit.getUrl().equalsIgnoreCase(responseUrl)) {
					
				}
			}
			*/
			
			// from here on we have a hit
			System.out.println("Thread " + this.threadNumber + " found " + responseUrl); //******
			// handle recursion right here
			if (ArgParse.getRecursionEnabled()) {
				if (recursionRedundancyCheck(responseUrl)) {
					orchestrator.initiateRecursion(responseUrl);
				}
			}
			// finally make the hit object
			new Hit(responseUrl, statusCode, responseLength); // this has to be last, otherwise recursionRedundancyCheck takes a huge shit
		}
	}
	
	private boolean excludeRequest(int statusCode, int responseLength) {
		return ArgParse.getExcludedStatusCodes().contains(statusCode) || ArgParse.getExcludedLength().contains(responseLength);
	}
	
	private boolean recursionRedundancyCheck(String url) { // shoddy patchwork
		if (url.equals(ArgParse.getUrl() + "/")) { // this does fix the initial forking
			return false;
		}
		for (Hit hit : Hit.getHits()) {
			if (hit.getUrl().equals(url + "/") || (hit.getUrl() + "/").equals(url)) {
				return false;
			}
		}
		return true; // true means passed the check
	}
}
