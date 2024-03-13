package vfuzz;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.util.EntityUtils;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.client.HttpClient;
import java.util.concurrent.BlockingQueue;
import java.io.IOException;

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
			String payload;
			try {
				payload = queue.take();
				if ("EOF".equals(payload)) {
					break;
				}

				HttpRequestBase request = WebRequester.buildRequest(url, payload);
				if (request != null) {
					HttpResponse response = WebRequester.makeRequest(request);
					parseResponse(response, request.getURI().toString());
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				System.err.println("Queue consumption was interrupted.");
			}
		}
	}
	
	private void parseResponse(HttpResponse response, String requestUrl) {
		if (response != null) {
			try {
				// checking for excluding the response due to flags:
				int statusCode = response.getStatusLine().getStatusCode();
				String responseBody = EntityUtils.toString(response.getEntity());
				int responseLength = responseBody.length();

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
					if (hit.getUrl().equals(requestUrl)) {
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
				System.out.println("Thread " + this.threadNumber + " found " + requestUrl); //******
				// handle recursion right here
				if (ArgParse.getRecursionEnabled()) {
					if (recursionRedundancyCheck(requestUrl)) {
						orchestrator.initiateRecursion(requestUrl);
					}
				}
				// finally make the hit object
				new Hit(requestUrl, statusCode, responseLength); // this has to be last, otherwise recursionRedundancyCheck takes a huge shit
			} catch (IOException e) {
				System.err.println("Error parsing response body for URL " + requestUrl);
			}
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
