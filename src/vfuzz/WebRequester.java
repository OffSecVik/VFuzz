package vfuzz;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class WebRequester {
	
	private static RateLimiter requestRateLimiter;
	private static final HttpClient client = HttpClient.newBuilder()
			.version(HttpClient.Version.HTTP_2)
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
	
	public static void enableRequestRateLimiter() {
		requestRateLimiter.enable();
		requestRateLimiter = new RateLimiter(ArgParse.getRateLimit());
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
				Metrics.setCurrentRequest(request.uri().toString());
			}
			
			// await a free token if we use the rate limiter
			if (ArgParse.getRateLimiterEnabled()) {
				requestRateLimiter.awaitToken();
			}
			
			// attempting to send the request
			try {
				HttpResponse<String> response =  client.send(request, BodyHandlers.ofString()); // request succeeded
				if (attempt > 1) { // LOGGING
					// System.err.println("Attempt " + attempt + " successful for " + request.uri());
					// Debug.logRequest(request.uri().toString(), "Attempt " + attempt + " successful " + request.uri());
				}
				return response;
			} catch (IOException e) {
				// System.err.println("Attempt " + attempt + " failed for " + request.uri()); // OLD LOGGING
				// Debug.logRequest(request.uri().toString(), "Attempt " + attempt + " failed for " + request.uri());
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
	public static HttpRequest buildRequest(String requestUrl, String payload) {
		HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();
		
		try {
			String encodedPayload = URLEncoder.encode(payload, StandardCharsets.UTF_8.toString());
			// for now every url gets a slash
			requestUrl = requestUrl.endsWith("/") ? requestUrl : requestUrl + "/";
			
			if (!payload.equals(encodedPayload)) {
				payload = encodedPayload;
				// System.out.println("Encoded \"" + payload + " to \"" + encodedPayload); // debug prints
			}
				
			switch (ArgParse.getRequestMode()) {
				case STANDARD -> requestUrl += payload;
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
		} catch (UnsupportedEncodingException uee) {
			System.out.println("There was an error URL-encoding " + payload + ".");
		}
		return null;
	}
}

