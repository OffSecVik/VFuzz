package vfuzz;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.entity.StringEntity;
import org.apache.http.HttpResponse;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class WebRequester {
	
	private static RateLimiter requestRateLimiter;
	private static final CloseableHttpClient client = HttpClients.createDefault();
	
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
	public static HttpResponse makeRequest(HttpRequestBase request) {
		long retryDelay = 1000; // milliseconds
		
		// retry loop
		for (int attempt = 1; attempt <= ArgParse.getMaxRetries(); attempt++) {
			// see if metrics is enabled
			if (ArgParse.getMetricsEnabled() ) {
				Metrics.incrementRequestsCount(); // increments the requests
				Metrics.setCurrentRequest(request.getURI().toString());
			}
			
			// await a free token if we use the rate limiter
			if (ArgParse.getRateLimiterEnabled()) {
				requestRateLimiter.awaitToken();
			}
			
			// attempting to send the request
			try {
				HttpResponse response =  client.execute(request); // request succeeded
				if (attempt > 1) { // LOGGING
					// System.err.println("Attempt " + attempt + " successful for " + request.uri());
					// Debug.logRequest(request.uri().toString(), "Attempt " + attempt + " successful " + request.uri());
				}
				return response;
			} catch (IOException e) {
				// System.err.println("Attempt " + attempt + " failed for " + request.uri()); // OLD LOGGING
				// Debug.logRequest(request.uri().toString(), "Attempt " + attempt + " failed for " + request.uri());
				// e.printStackTrace();
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
	public static HttpRequestBase buildRequest(String requestUrl, String payload) {
		try {
			String encodedPayload = URLEncoder.encode(payload, StandardCharsets.UTF_8);
			HttpRequestBase request = null;

			// for now every url gets a slash
			requestUrl = requestUrl.endsWith("/") ? requestUrl : requestUrl + "/";

			if (!payload.equals(encodedPayload)) {
				payload = encodedPayload;
				// System.out.println("Encoded \"" + payload + " to \"" + encodedPayload); // debug prints
			}

			switch (ArgParse.getRequestMode()) {
				case STANDARD -> {
					requestUrl += payload;
					request = new HttpGet(requestUrl);
				}
				case SUBDOMAIN -> {
					requestUrl = urlRebuilder(requestUrl, payload);
					request = new HttpGet(requestUrl);
				}
				case VHOST -> {
					request = new HttpGet(requestUrl);
					request.setHeader("Host", payload);
				}
			}
			
			switch (ArgParse.getRequestMethod()) {
				case GET -> {
					request = new HttpGet(requestUrl);
				}
				case HEAD -> {
					request = new HttpHead(requestUrl);
				}
				case POST -> {
					request = new HttpPost(requestUrl);
					((HttpPost) request).setEntity(new StringEntity("my post data")); // TODO: Variable
				}
			}
			
			return request;
			
		} catch (IllegalArgumentException e) {
			System.err.println("Invalid URI: " + e.getMessage());
		} catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e); // TODO: Find out what that shit does
        }
        return null;
	}
}