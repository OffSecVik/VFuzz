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
		if (ArgParse.getRequestFileFuzzing()) {
			requestFileMode();
		} else {
			standardMode();
		}
	}

	private void standardMode() {
		while (true) {
			String payload;
			try {
				payload = queue.take();
				if ("EOF".equals(payload)) {
					break;
				}

				HttpRequestBase request = WebRequester.buildRequest(url, payload);

				if (request != null) {
					// System.out.println(request.toString());
					HttpResponse response = WebRequester.makeRequest(request);
					parseResponse(response, request);
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				System.err.println("Queue consumption was interrupted.");
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
		while (true) {
			String payload;
			try {
				payload = queue.take();
				if ("EOF".equals(payload)) {
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
				Thread.currentThread().interrupt();
				System.err.println("Queue consumption was interrupted.");
			}
		}

    }

	private void parseResponse(HttpResponse response, HttpRequestBase request) {
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
				// TODO: Print Formatting for VHost mode
				// System.out.println("Hit: " + requestUrl + "\tStatus Code: " + statusCode + "\tResponse Length: " + responseLength + "\tVHost:" + request.getHeaders("Host")[0].getValue());

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

				// from here on we have a hit
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
