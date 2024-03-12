package vfuzz;

import java.util.HashSet;
import java.util.Set;

public class ArgParse {

	// -t --threads
	private static int threadCount = 10; // 10 is the default number of threads
	
	// -w --wordlist
	private static String wordlistPath;
	
	// -u --url
	private static String url;
	
	// -e --excludeStatusCodes
	private static Set<Integer> excludedStatusCodes = new HashSet<>();
	
	// -l --excludeLength
	private static Set<Integer> excludedLength = new HashSet<>();
	
	// --vhost --subdomain
	private static RequestMode requestMode = RequestMode.STANDARD;
	
	// --method
	private static RequestMethod requestMethod = RequestMethod.GET;
	
	// --max-retries
	private static int maxRetries = 5;
	
	// --rate-limit
	private static int rateLimit;
	private static boolean rateLimiterEnabled = false;
	
	// --metrics
	private static boolean metricsEnabled = false;
	
	static { // static initalizer block
		excludedStatusCodes.add(404); // excluding 404 by default.
	}
	
	private static boolean argSyntaxVerified(String[] args, int i) {
		if (i + 1 >= args.length) {
			return false;
		}
		return true;
	}
	
	public static boolean checkRequired() { // needs to be public!
		if (wordlistPath == null) {
			System.out.println("Missing argument: -w (wordlist)"); // TO DO: printHelp here
			return false;
		}
		if (url == null) {
			System.out.println("Missing argument: -u (url)");
			return false;
		}
		return true;
	}
	
	private static void printHelp() {
		System.out.println("help ;_;");
	}
	
	public static String urlStripTrailingSlash(String url) {
		if (url != null && url.endsWith("/")) {
			return url.substring(0, url.length() -1);
		}
		return url;
	}
	
	private static String urlEnsureScheme(String url) {
		if (!url.startsWith("http://") && !url.startsWith("https://")) {
			return "http://" + url;
		}
		return url;
	}
	
	public static int parse(String[] args) {
		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
			
			
				case "-h":
				case "--help":
					printHelp();
					return -1;
			
					
				case "-w":
				case "--wordlist":
					if (argSyntaxVerified(args, i)) {
						wordlistPath = args[++i];
					} else {
						System.out.println("Error: -w requires a wordlist path.");
						return -1;
					}
					break;
					
					
				case "-u":
				case "--url":
					if (argSyntaxVerified(args, i)) {
						url = urlEnsureScheme(urlStripTrailingSlash(args[++i]));
					} else {
						System.out.println("Error: -u requires a URL.");
						return -1;
					}
					break;
					
					
				case "-t":
				case "--threads":
					if (argSyntaxVerified(args, i)) {
						try {
							threadCount = Integer.parseInt(args[++i]);
						} catch (NumberFormatException e) {
							System.out.println("Error: -t requires an integer.");
							return -1;
						}
					} else {
						System.out.println("Error: -t requires an integer.");
						return -1;
					}
					break;
					
				case "-e":
				case "--excludeStatusCodes":
					if (!argSyntaxVerified(args, i)) {
						System.out.println("Error: -e requires one or more HTTP Status code(s).");
						return -1;
					}
					String[] codes = args[++i].split(",");
					for (String code : codes) {
						try {
							excludedStatusCodes.add(Integer.parseInt(code.trim()));
						} catch (NumberFormatException e) {
							System.out.println("Error: Invalid status code format '" + code + "' (example: 301,302,303,304).");
							return -1;
						}
					}
					break;
						
				case "--vhost":
					requestMode = RequestMode.VHOST;
					break;
				
				case "--subdomain":
					requestMode = RequestMode.SUBDOMAIN;
					break;				
				
				case "--method":
					if (argSyntaxVerified(args, i)) {
						try {
							requestMethod = RequestMethod.valueOf(args[++i].toUpperCase());
						} catch (IllegalArgumentException e) {
							System.out.println("Error: Unsupported HTTP method. Currently supported methods are: GET, POST, HEAD");
						}
					} else {
						System.out.println("Error: --method requires a HTTP method (currently supported: GET, HEAD, POST)");
						return -1;
					}
					break;
					
				case "-l":
				case "--excludeLength":
					if (!argSyntaxVerified(args, i)) {
						System.out.println("Error: -l requires one or more Decimals (example: 187,304,101)");
						return -1;
					}
					String[] lengths = args[++i].split(",");
					for (String length: lengths) {
						try {
							excludedLength.add(Integer.parseInt(length.trim()));
						} catch (NumberFormatException e) {
							System.out.println("Error: --length requires an integer.");
							return -1;
						}
					}
					break;

				case "--max-retries":
					if (!argSyntaxVerified(args, i)) {
						System.out.println("Error: --max-retries requires an integer.");
						return -1;
					}
					try {
						maxRetries = Integer.parseInt(args[++i]);
					} catch (NumberFormatException e) {
						System.out.println("Error: --max-retries requires an integer.");
						return -1;
					}
					break;
					
				case "--rate-limit":
					if (!argSyntaxVerified(args, i)) {
						System.out.println("Error: --rate-limit requires an integer (max requests per second).");
						return -1;
					}
					try {
						rateLimiterEnabled = true;
						rateLimit = Integer.parseInt(args[++i]);
						WebRequester.enableRequestRateLimiter();
					} catch (NumberFormatException e) {
						System.out.println("Error: --rate-limit requires an integer.");
						return -1;
					}
					break;
					
				case "--metrics":
						metricsEnabled = true;
						Metrics.startMetrics();
					break;
					
				default:
					System.out.println("Unknown option: " + args[i]);
					return -1;
			}
		}
		return 0;
	}
	
	public static int getThreadCount() {
		return threadCount;
	}

	public static String getWordlistPath() {
		return wordlistPath;
	}

	public static String getUrl() {
		return url;
	}

	public static Set<Integer> getExcludedStatusCodes() {
		return excludedStatusCodes;
	}
	
	public static Set<Integer> getExcludedLength() {
		return excludedLength;
	}

	public static RequestMode getRequestMode() {
		return requestMode;
	}

	public static RequestMethod getRequestMethod() {
		return requestMethod;
	}
	
	public static int getMaxRetries() {
		return maxRetries;
	}
	
	public static int getRateLimit() {
		return rateLimit;
	}
	
	public static boolean getRateLimiterEnabled() {
		return rateLimiterEnabled;
	}
	
	public static boolean getMetricsEnabled() {
		return metricsEnabled;
	}
}
