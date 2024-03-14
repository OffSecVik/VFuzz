package vfuzz;

import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.conn.ConnectionKeepAliveStrategy;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.entity.StringEntity;
import org.apache.http.HttpResponse;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public class WebRequester {
	
	private static RateLimiter requestRateLimiter;
	public static void enableRequestRateLimiter(int rateLimit) { // gets called by ArgParse upon reading the flags if the --rate-limit flag is provided
		requestRateLimiter = new RateLimiter(rateLimit);
		requestRateLimiter.enable();
	}
	// attempting some net code
	private static final CloseableHttpClient client;
	static {
		System.setProperty("networkaddress.cache.ttl", "60");
		System.setProperty("networkaddress.cache.negative.ttl", "10");
		PoolingHttpClientConnectionManager connManager = new PoolingHttpClientConnectionManager();
		ConnectionKeepAliveStrategy keepAliveStrategy = (response, context) -> {
			return 5 * 1000; // keep alive for 5 seconds
		};
		RequestConfig requestConfig = RequestConfig.custom()
				.setConnectTimeout(5000) // timeout until connection is established
				.setConnectionRequestTimeout(5000) // timeout when requesting a connection from the connection manager
				.setSocketTimeout(5000) // maximum period of inactivity between two consecutive data packets. unstucks threads waiting for responses
				.build();
		connManager.setMaxTotal(10000); // set maximum number of total open connections
		connManager.setDefaultMaxPerRoute(100); // set default maximum number of connections per route
		client = HttpClients.custom()
				.setDefaultRequestConfig(requestConfig)
				.setConnectionManager(connManager)
				.setKeepAliveStrategy(keepAliveStrategy)
				.build();
	}

	private static String removeTrailingSlash(String url) {
		if (url != null && url.endsWith("/")) {
			return url.substring(0, url.length() - 1);
		}
		return url;
	}

	private static String urlRebuilder(String url, String payload) { // rebuilds URL for VHOST and SUBDOMAIN mode
		String httpPrefix = url.startsWith("https://") ? "https://" : "http://"; // selects which scheme the url starts with.
		String urlWithoutScheme = url.substring(httpPrefix.length()); // gets everything except the scheme
		String urlWithoutWww = urlWithoutScheme.startsWith("www") ? urlWithoutScheme.substring(4) : urlWithoutScheme; // cuts "www." if present in the url
		return httpPrefix + payload + "." + urlWithoutWww; // test this
	}

	private static String vhostRebuilder(String url, String payload) {
		String httpPrefix = url.startsWith("https://") ? "https://" : "http://"; // selects which scheme the url starts with.
		String urlWithoutScheme = url.substring(httpPrefix.length()); // gets everything except the scheme
		String urlWithoutWww = urlWithoutScheme.startsWith("www") ? urlWithoutScheme.substring(4) : urlWithoutScheme; // cuts "www." if present in the url
		return payload + "." + removeTrailingSlash(urlWithoutWww); // test this
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
	
	// method for building FILE MODE request
	public static HttpRequestBase buildRequestFromFile(ParsedHttpRequest parsedRequest, String payload) {
		try {
			String encodedPayload = URLEncoder.encode(payload, StandardCharsets.UTF_8); // urlencoding, some wordlists have weird payloads
			parsedRequest.replaceFuzzMarker(encodedPayload); // injecting the payload into the request // TODO: OPTIONAL: could avoid making deep copies of the parsedRequest in QueueConsumer if we found a way to parse for FUZZ AFTER extracting the data from the parsedRequest. This would likely involve making a method in this class right here or checking for FUZZ every time we read data from the request
			// String encodedPayload = URLEncoder.encode(payload, StandardCharsets.UTF_8);
			HttpRequestBase request = null;
			String requestUrl = parsedRequest.getUrl();
			// requestUrl = requestUrl.endsWith("/") ? requestUrl : requestUrl + "/"; // TODO: Take care of duplicate due to backslashes another way, this is a little janky

			// set request method
			switch (parsedRequest.getMethod().toUpperCase()) {
				case "GET" -> request = new HttpGet(requestUrl);
				case "HEAD" -> request = new HttpHead(requestUrl);
				case "POST" -> {
					HttpPost postRequest = new HttpPost();
					postRequest.setEntity(new StringEntity(parsedRequest.getBody())); // TODO: check if POST body is preserved, handle content-length dynamically based on payload length
					request = postRequest;
				}
			}

			// set up headers
			for (Map.Entry<String, String>entry : parsedRequest.getHeaders().entrySet()) {
                assert request != null;
                request.setHeader(entry.getKey(), entry.getValue());
			}

			return request;

		} catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }

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

			// initialize request here and set HTTP Method
			switch (ArgParse.getRequestMethod()) {
				case GET -> request = new HttpGet(requestUrl);
				case HEAD -> request = new HttpHead(requestUrl);
				case POST -> {
					HttpPost postRequest = new HttpPost();
					postRequest.setEntity(new StringEntity("my post data")); // TODO: Variable
					request = postRequest;
				}
			}

			// load the payload depending on Mode
			switch (ArgParse.getRequestMode()) {
				case STANDARD -> request.setURI(new URI(requestUrl += payload));
				case SUBDOMAIN -> {
					String rebuiltUrl = urlRebuilder(requestUrl, payload);
					request.setURI(new URI(rebuiltUrl));
				}
				case VHOST -> {
					// String rebuiltUrl = urlRebuilder(requestUrl, payload);
					String vhostUrl = vhostRebuilder(requestUrl, payload);
					request.setURI(new URI(requestUrl));
					request.setHeader("Host", vhostUrl);
					// System.out.println(request.getHeaders("Host").toString());
				}
			}

			// set up User-Agent
			if (ArgParse.getUserAgent() != null) {
				request.setHeader("User-Agent", ArgParse.getUserAgent());
			}

			// set up Headers
			if (!ArgParse.getHeaders().isEmpty()) {
				for (String header : ArgParse.getHeaders()) {
					String[] parts = header.split(":", 2); // split at the first colon
					if (parts.length == 2) {
						String headerName = parts[0].trim();
						String headerValue = parts[1].trim();
						request.setHeader(headerName, headerValue);
					} else {
						System.err.println("Invalid header format while building request: " + header);
					}
				}
			}

			return request;
			
		} catch (IllegalArgumentException e) {
			System.err.println("Invalid URI: " + e.getMessage());
		} catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e); // TODO: Find out what that shit does
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        return null;
	}
}