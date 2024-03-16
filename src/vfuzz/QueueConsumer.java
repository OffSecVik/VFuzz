package vfuzz;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.util.EntityUtils;
import java.util.concurrent.BlockingQueue;
import java.io.IOException;

public class QueueConsumer implements Runnable {
	
	private final BlockingQueue<String> queue;
	private final String url;
	private final ThreadOrchestrator orchestrator;
	public final int threadNumber;
	private static int threadCounter = 0;
	private int recursionDepth = 0;
	private volatile boolean running = true;
	private static boolean firstThreadFinished = false;

	public QueueConsumer(ThreadOrchestrator orchestrator, BlockingQueue<String> queue, String url, int recursionDepth) {
		this.queue = queue;
		this.url = url;
		this.threadNumber = threadCounter;
		// System.out.println("Number of Consumers so far: " + threadCounter);
		this.orchestrator = orchestrator;
		threadCounter++;
		this.recursionDepth = recursionDepth;
	}

	public QueueConsumer(ThreadOrchestrator orchestrator, Target target) {
		this.queue = target.getQueue();
		this.url = target.getUrl();
		this.threadNumber = threadCounter;
		// System.out.println("Number of Consumers so far: " + threadCounter);
		this.orchestrator = orchestrator;
		threadCounter++;
		this.recursionDepth = target.getRecursionDepth();
	}

	@Override
	public void run() {
		while (running && !Thread.currentThread().isInterrupted()) { // what does the second one check?
			if (ArgParse.getRequestFileFuzzing()) {
				requestFileMode();
			} else {
				standardMode();
			}
		}



		if (Target.getTargets().isEmpty()) {
			orchestrator.shutdownExecutor();
		}

	}

	private void standardMode() {
		while (running) {
			String payload;
			try {
				payload = queue.take();
				if ("ENDOFFILEMARKERTHATWONTBEINANYWORDLIST".equals(payload)) {
					// this chunk happens once the thread is through with the wordlist.
					firstThreadFinished = true; // need this for calculating thread redistribution. we flip this once we finish any thread, assuming that the first thread to finish is always the main fuzz thread, since it gets the chunk of the thread pool -> edge cases??
					if (ArgParse.getRecursionEnabled()) {
						orchestrator.redistributeThreads();
					}
					Target.removeTargetFromList(url); // removes the target this thread finished from the URL
					orchestrator.removeTargetFromList(url);
					running = false;
					break;
				}

				HttpRequestBase request = WebRequester.buildRequest(url, payload);

				if (request != null) {
					// System.out.println(request.toString());
					HttpResponse response = WebRequester.makeRequest(request);
					parseResponse(response, request);
				}
			} catch (InterruptedException e) {
				running = false;
				//System.err.println("Queue consumption was interrupted, finishing task");
			}
		}
	}

	private void requestFileMode() {
		ParsedHttpRequest rawRequest = null;
        try {
			rawRequest = new ParsedHttpRequest().parseHttpRequestFromFile(ArgParse.getRequestFilePath());

        } catch (IOException e) {
			System.err.println("There was an error parsing the request from the file:\n" + e.getMessage());
        }
		while (running) {
			String payload;
			try {
				payload = queue.take();
				if ("EOF".equals(payload)) {
					running = false;
					break;
				}
				if (rawRequest != null) {
					// System.out.println("RAW url before buildRequestFromFile: " + rawRequest.getUrl());
					ParsedHttpRequest rawCopy = new ParsedHttpRequest(rawRequest);
					HttpRequestBase request = WebRequester.buildRequestFromFile(rawCopy, payload); // TODO NEED TO COPY THE REQUEST; NOT SEND THE ORIGINAL
					// System.out.println("Requesting: " + request.getURI());
					HttpResponse response = WebRequester.makeRequest(request);
					parseResponse(response, request);
				}
			} catch (InterruptedException e) {
				running = false;
				Thread.currentThread().interrupt();
				System.err.println("Queue consumption was interrupted.");
			}
		}

    }

	private void parseResponse(HttpResponse response, HttpRequestBase request){
		boolean thisIsRecursiveTarget = false;
		String requestUrl = request.getURI().toString();
		if (response != null) {
			try {
				// checking for excluding the response due to flags:
				int statusCode = response.getStatusLine().getStatusCode();
				int responseLength = 0;
				if (response.getEntity() != null) {
					String responseBody = EntityUtils.toString(response.getEntity());
					responseLength = responseBody.length();
				}

				if (excludeRequest(statusCode, responseLength)) {
					return;
				}

				// check if it's a false positive
				if (!ArgParse.getRequestMode().equals(RequestMode.VHOST)) {
					for (Hit hit : Hit.getHits()) {
						if (hit.getUrl().equals(requestUrl)) {
							return;
						}
					}
				}

				/*
				// check if there's a case insensitive match // TODO: continue this
				for (Hit hit : Hit.getHits()) {
					if (hit.getUrl().equalsIgnoreCase(responseUrl)) {

					}
				}
				*/

				// from here on we have a hit // from here on we have a hit // from here on we have a hit //
				// handle recursion right here


				if (ArgParse.getRecursionEnabled()) {
					if (recursionRedundancyCheck(requestUrl)) {
						thisIsRecursiveTarget = true;
					}
				}

				// TODO: Print Formatting for VHost mode
				// System.out.println("Hit: " + requestUrl + "\tStatus Code: " + statusCode + "\tResponse Length: " + responseLength + "\tVHost:" + request.getHeaders("Host")[0].getValue());
				if (ArgParse.getRequestMode() == RequestMode.VHOST) { // attempt at vhost print formatting.
					requestUrl = request.getHeaders("HOST")[0].getValue(); // simply setting the requestUrl (which never changes in vhost mode) to the header value (which is the interesting field)
				}
				// finally make the hit object
				Hit hit = new Hit(requestUrl, statusCode, responseLength); // this has to be last, otherwise recursionRedundancyCheck takes a huge shit
				System.out.println(hit);
				if (thisIsRecursiveTarget) {
					orchestrator.initiateRecursion(requestUrl, recursionDepth);
				}
			} catch (IOException e) {
				System.err.println("Error parsing response body for URL " + requestUrl);
			} catch (Exception e) {
				System.err.println(e.getMessage());
			}
		}
	}
	
	private boolean excludeRequest(int statusCode, int responseLength) {
		return ArgParse.getExcludedStatusCodes().contains(statusCode) || ArgParse.getExcludedLength().contains(responseLength);
	}

	private boolean recursionRedundancyCheck(String url) { // shoddy patchwork
		if (url.equals(ArgParse.getUrl() + "/")) { // this does fix the initial forking
			// System.out.println("Redundant recursion.");
			return false;
		}
		for (Hit hit : Hit.getHits()) {
			if (hit.getUrl().equals(url + "/") || (hit.getUrl() + "/").equals(url)) {
				return false;
			}
		}
		// System.out.println("returning true.");
		return true; // true means passed the check
	}

	private void shutdown() {
		this.running = false;
	}

	public static boolean isFirstThreadFinished() {
		return firstThreadFinished;
	}

}
