package vfuzz;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.io.IOException;

public class WebRequester {
	
	private static RateLimiter requestRateLimiter = new RateLimiter(ArgParse.getRateLimit());
	private static final HttpClient client = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_2)
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	
	public static void enableRequestRateLimiter() {
		requestRateLimiter.enable();
	}
	
	private static String urlRebuilder(String url, String payload) { // rebuilds URL for VHOST and SUBDOMAIN mode
		String httpPrefix = url.startsWith("https://") ? "https://" : "http://"; // selects which scheme the url starts with.
		String urlWithoutScheme = url.substring(httpPrefix.length()); // gets everything except the scheme
		String urlWithoutWww = urlWithoutScheme.startsWith("www") ? urlWithoutScheme.substring(4) : urlWithoutScheme; // cuts "www." if present in the url
		return httpPrefix + payload + "." + urlWithoutWww; // test this
	}
	
	
	// method for SENDING the request
	public static HttpResponse<String> makeRequest(HttpRequest request) {
		
		long retryDelay = 1000; // milliseconds
		
		// retry loop
		for (int attempt = 1; attempt <= ArgParse.getMaxRetries(); attempt++) {
			// see if metrics is enabled
			if (ArgParse.getMetricsEnabled() ) {
				Metrics.incrementRequestsCount(); // increments the requests
			}
			
			// await a free token if we use the rate limiter
			if (ArgParse.getRateLimiterEnabled()) {
				requestRateLimiter.awaitToken();
			}
			
			// attempting to send the request
			try {
				HttpResponse<String> response =  client.send(request, BodyHandlers.ofString()); // request succeeded
				if (attempt > 1) { // LOGGING
					System.err.println("Attempt " + attempt + " successful for " + request.uri());
				}
				return response;
			} catch (IOException e) {
				System.err.println("Attempt " + attempt + " failed for " + request.uri()); // LOGGING
				// e.printStackTrace();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				System.err.println("Request was interrupted: " + e.getMessage());
				return null;
			} catch (Exception e) {
				System.err.println("Unexpected error during request: " + e.getMessage());
				return null;
			}
			// retrying
			try {
				Thread.sleep(retryDelay);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return null;
			}
			retryDelay *= 2; // exponential backoff
		}
		return null;
	}
	

	// method for BUILDING the request
	public static HttpRequest buildRequest(String payload) {
		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
		
		try {
			String requestUrl = ArgParse.getUrl();
				
			switch (ArgParse.getRequestMode()) {
				case STANDARD -> requestUrl += "/" + payload;
				case SUBDOMAIN -> requestUrl = urlRebuilder(requestUrl, payload);
				case VHOST -> requestBuilder.header("Host", urlRebuilder(requestUrl, payload));
			}
			
			switch (ArgParse.getRequestMethod()) {
				case GET -> requestBuilder.uri(URI.create(requestUrl)).GET();
				case HEAD -> requestBuilder.uri(URI.create(requestUrl)).method("HEAD", HttpRequest.BodyPublishers.noBody());
				case POST -> requestBuilder.uri(URI.create(requestUrl)).POST(HttpRequest.BodyPublishers.ofString("my post data")); // post data?
			}
			
			return requestBuilder.build();
			
		} catch (IllegalArgumentException e) {
			System.err.println("Invalid URI: " + e.getMessage());
		}
		return null;
	}
}

