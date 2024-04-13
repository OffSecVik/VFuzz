package vfuzz;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public class ArgParse {
    public static void registerArguments() {
        ConfigManager configManager = ConfigManager.getInstance();

        configManager.registerArgument(new CommandLineArgument(
                "--threads", "-t", "threadCount",
                (cm, value) -> cm.setConfigValue("threadCount", value), // Action: Set Value
                value -> { // Sanitization
                    try {
                        int val = Integer.parseInt(value);
                        return val >= 1 && val <= 200; // TODO: Agree on max threads! Is there even a max???
                    } catch (NumberFormatException e) {
                        return false;
                    }
                },
                "Number of threads. Must be a number between 1 and 200.", // Description
                true, // Optional?
                "1", // Default Value
                false // isFlag? (e.g. --recursive)
        ));

        configManager.registerArgument(new CommandLineArgument(
                "--wordlist", "-w", "wordlistPath",
                (cm, value) -> cm.setConfigValue("wordlistPath", value),
                value -> !value.trim().isEmpty(), // Validator
                "Path to the word list. This argument is required.",
                false,
                null, // Null as default due to the arg being non optional
                false
        ));

        configManager.registerArgument(new CommandLineArgument(
                "--url", "-u", "url",
                (cm, value) -> {
                    String processedValue = (!value.startsWith("http://") && !value.startsWith("https://")) ? "http://" + value : value;
                    processedValue = processedValue.endsWith("/") ? processedValue.substring(0, processedValue.length() - 1) : processedValue;
                    cm.setConfigValue("url", processedValue);
                },
                value -> value.matches("^(http://|https://).*"),
                "URL to the target website. This argument is required and must start with http:// or https://. Trailing slashes are automatically removed.",
                false,
                null,
                false
        ));

        /*
        configManager.registerArgument(new CommandLineArgument(
                "--excludeStatusCodes", "-e", "excludedStatusCodes",
                (cm, value) -> {
                    String[] codes = value.split(",");
                    StringBuilder validCodes = new StringBuilder();
                    for (String code : codes) {
                        try {
                            Integer.parseInt(code.trim());
                            if (!validCodes.isEmpty()) validCodes.append(",");
                            validCodes.append(code.trim());
                        } catch (NumberFormatException e) {
                            System.err.println("Error: Invalid status code format '" + code + "' (example: 301,302,303,304).");
                        }
                    }

                    if (!validCodes.isEmpty()) {
                        cm.setConfigValue("excludedStatusCodes", validCodes.toString());
                    }
                },
                value -> value.matches("^[0-9]+(,[0-9]+)*$"),
                "List of HTTP status codes to exclude, separated by commas. Each code must be a valid integer.",
                true,
                "404",
                false
        ));

         */

        configManager.registerArgument(new CommandLineArgument(
                "--excludeStatusCodes", "-e", "excludedStatusCodes",
                (cm, value) -> {
                    String[] parts = value.split(",");
                    List<String> validCodesAndRanges = new ArrayList<>();
                    for (String part : parts) {
                        // in case a range was provided
                        if (part.matches("^\\d+-\\d+$")) {
                            String[] bounds = part.split("-");
                            int lowerBound = Math.min(Integer.parseInt(bounds[0].trim()), Integer.parseInt(bounds[1].trim()));
                            int upperBound = Math.max(Integer.parseInt(bounds[0].trim()), Integer.parseInt(bounds[1].trim()));
                            validCodesAndRanges.add(lowerBound + "-" + upperBound);
                        } else if (part.matches("^\\d+$")) {
                            // in case a single status code was provided
                            validCodesAndRanges.add(part.trim());
                        } else {
                            System.err.println("Error: Invalid status code format '" + part + "' (expected formats: single code, multiple codes, single range, or multiple ranges).");
                        }
                    }

                    if (!validCodesAndRanges.isEmpty()) {
                        cm.setConfigValue("excludedStatusCodes", String.join(",", validCodesAndRanges));
                    }
                },
                value -> {
                    try {
                        return value.matches("^\\d+(\\-\\d+)?(,\\d+(\\-\\d+)?)*$");
                    } catch (Exception e) {
                        return false;
                    }
                },
                "List of HTTP status codes or ranges to exclude, separated by commas. For example: 404,405-410,505-560. Each code or range must be valid.",
                true,
                "404",
                false
        ));

        configManager.registerArgument(new CommandLineArgument(
                "--excludeLength", "-l", "excludeLength",
                (cm, value) -> {
                    String[] lengths = value.split(",");
                    List<String> validLengthsAndRanges = new ArrayList<>();
                    for (String length : lengths) {
                        // in case a range was provided
                        if (length.matches("^\\d+-\\d+$")) {
                            String[] bounds = length.split("-");
                            int lowerBound = Math.min(Integer.parseInt(bounds[0].trim()), Integer.parseInt(bounds[1].trim()));
                            int upperBound = Math.max(Integer.parseInt(bounds[0].trim()), Integer.parseInt(bounds[1].trim()));
                            validLengthsAndRanges.add(lowerBound + "-" + upperBound);
                        } else if (length.matches("^\\d+$")) {
                            // in case a single length was provided
                            validLengthsAndRanges.add(length.trim());
                        } else {
                            System.err.println("Error: Invalid length format '" + length + "' (expected formats: single length, multiple lengths, single range, or multiple ranges).");
                        }
                    }
                    if (!validLengthsAndRanges.isEmpty()) {
                        cm.setConfigValue("excludeLength", String.join(",", validLengthsAndRanges));
                    }
                },
                value -> {
                    try {
                        return value.matches("^\\d+(\\-\\d+)?(,\\d+(\\-\\d+)?)*$");
                    } catch (Exception e) {
                        return false;
                    }
                },
                "List of content lengths or length ranges to exclude, separated by commas. Each length must be a valid integer.",
                true,
                null,
                false
        ));


        configManager.setConfigValue("requestMode", RequestMode.STANDARD.name()); // Is used to avoid setting a defaultValue twice

        configManager.registerArgument(new CommandLineArgument(
                "--vhost", "", "requestMode",
                (cm, value) -> cm.setConfigValue("requestMode", RequestMode.VHOST.name()),
                value -> true,
                "Activates the virtual host fuzzing mode.",
                true,
                null,
                true
        ));

        configManager.registerArgument(new CommandLineArgument(
                "--subdomain", "", "requestMode",
                (cm, value) -> cm.setConfigValue("requestMode", RequestMode.SUBDOMAIN.name()),
                value -> true,
                "Activates the subdomain fuzzing mode.",
                true,
                null,
                true
        ));

        configManager.registerArgument(new CommandLineArgument(
                "--fuzz", "", "requestMode",
                (cm, value) -> cm.setConfigValue("requestMode", RequestMode.FUZZ.name()),
                value -> true,
                "Activates the FUZZ-marker fuzzing mode.",
                true,
                null,
                true
        ));


        configManager.registerArgument(new CommandLineArgument(
                "--method", "", "requestMethod",
                (cm, value) -> {
                    try {
                        RequestMethod method = RequestMethod.valueOf(value.toUpperCase());
                        cm.setConfigValue("requestMethod", method.name());
                    } catch (IllegalArgumentException e) {
                        System.err.println("Error: Unsupported HTTP method. Currently supported methods are: GET, POST, HEAD");
                    }
                },
                value -> EnumSet.allOf(RequestMethod.class).stream()
                        .map(Enum::name)
                        .toList()
                        .contains(value.toUpperCase()), // Validator, which ensures that the value corresponds to one of the supported methods
                "Specifies the HTTP method to use for requests. Supported methods are GET, POST, and HEAD. Default is GET.",
                true,
                RequestMethod.GET.name(),
                false
        ));

        configManager.registerArgument(new CommandLineArgument(


                "--max-retries", "", "maxRetries",
                (cm, value) -> {
                    try {
                        int maxRetries = Integer.parseInt(value);
                        cm.setConfigValue("maxRetries", String.valueOf(maxRetries));
                    } catch (NumberFormatException e) {
                        System.out.println("Error: --max-retries requires an integer.");
                    }
                },
                value -> {
                    try {
                        int val = Integer.parseInt(value);
                        return val >= 0;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                },
                "Specifies the maximum number of retries for a request. This value must be an integer. Default is 5.",
                true,
                "5",
                false
        ));

        configManager.registerArgument(new CommandLineArgument(
                "--rate-limit", "", "rateLimiterEnabled",
                (cm, value) -> {
                    try {
                        int rateLimit = Integer.parseInt(value);
                        cm.setConfigValue("rateLimit", String.valueOf(rateLimit));
                        cm.setConfigValue("rateLimiterEnabled", value);
                    } catch (NumberFormatException e) {
                        System.out.println("Error: --rate-limit requires an integer (max requests per second).");
                    }
                },
                value -> {
                    try {
                        int val = Integer.parseInt(value);
                        return val > 0;
                    } catch (NumberFormatException e) {
                        return false;
                    }
                },
                "Sets the maximum number of requests per second. This value must be a positive integer.",
                true,
                "4000",
                false
        ));

        configManager.registerArgument(new CommandLineArgument(
                "--metrics", "", "metricsEnabled",
                (cm, value) -> {
                    cm.setConfigValue("metricsEnabled", value);
                    if ("true".equalsIgnoreCase(value)) {
                        Metrics.startMetrics();
                    }
                },
                value -> true,
                "Enables metrics collection.",
                true,
                "false",
                true
        ));

        configManager.registerArgument(new CommandLineArgument(
                "--debug", "", "debugEnabled",
                (cm, value) -> cm.setConfigValue("debugEnabled", value),
                value -> true,
                "Enables debug mode.",
                true,
                "false",
                true
        ));

        configManager.registerArgument(new CommandLineArgument(
                "--recursive", "", "recursionEnabled",
                (cm, value) -> cm.setConfigValue("recursionEnabled", value),
                value -> true,
                "Enables recursive fuzzing mode.",
                true,
                "false",
                true
        ));

        configManager.registerArgument(new CommandLineArgument(
                "--user-agent", "-A", "userAgent",
                (cm, value) ->{

                    headers.add("User-Agent: " + value);
                },
                value -> !value.trim().isEmpty(),
                "Sets the user agent for requests. Example: --user-agent \"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3\"",
                true,
                null,
                false
        ));

        configManager.registerArgument(new CommandLineArgument(
                "-r", "", "requestFileFuzzing",
                (cm, value) -> {
                    cm.setConfigValue("requestFilePath", value);
                    cm.setConfigValue("requestFileFuzzing", "true");
                },
                value -> !value.trim().isEmpty() && new File(value).exists(),
                "Specifies the filepath to the HTTP request file for fuzzing. This activates file-based fuzzing mode. Ensure the file exists. Example: -r \"/path/to/requestfile.txt\"",
                true,
                null,
                false
        ));

        configManager.registerArgument(new CommandLineArgument(
                "-H", "", "headers",
                (cm, value) -> {
                    if (!value.contains(":") || value.startsWith(":") || value.endsWith(":")) {
                        System.out.println("Error: Invalid header format. Header must be in 'Name: Value' format.");
                    } else {
                        headers.add(value);
                    }
                },
                value -> value.contains(":") && !value.startsWith(":") && !value.endsWith(":"),
                "Sets custom headers for the requests. Each header must be in the 'Name: Value' format. Can be used multiple times for multiple headers. Example: -H \"Content-Type: application/json\"",
                true,
                null,
                false
        ));

        configManager.registerArgument(new CommandLineArgument(
                "-C", "--cookie", "cookies",
                (cm, value) -> {
                    if (cm.getConfigValue("cookies") == null) {
                        cm.setConfigValue("cookies", value);
                    } else {
                        String newCookie = (cm.getConfigValue("cookies") + "; " + value);
                        cm.setConfigValue("cookies", newCookie);
                    }
                },
                value -> true,
                "Sets custom cookies for the requests. Can be used multiple times for multiple cookies. Example: -C \"username=JohnDoe\"",
                true,
                null,
                false
        ));

        configManager.registerArgument(new CommandLineArgument(
                "-d", "--post-data", "postRequestData",
                (cm, value) -> {
                    System.out.println("SETTING TO POST");
                    cm.setConfigValue("postRequestData", value);
                    cm.setConfigValue("requestMethod", "POST"); // using any kind of POST data sets the request method to POST
                },
                value -> true,
                "Sets data to be used in POST request.",
                true,
                null,
                false
        ));

        configManager.registerArgument(new CommandLineArgument(
                "--fuzz-marker", "", "fuzzMarker",
                (cm, value) -> cm.setConfigValue("fuzzMarker", value),
                value -> !value.trim().isEmpty(),
                "Specifies the fuzz marker within the request file that will be replaced with dynamic content. Example: --fuzz-marker \"FUZZ\"",
                true,
                "FUZZ",
                false
        ));

        configManager.registerArgument(new CommandLineArgument(
                "--follow-redirects", "", "followRedirects",
                (cm, value) -> cm.setConfigValue("followRedirects", value),
                value -> true,
                "Makes the fuzzer follow redirects.",
                true,
                "false",
                true
        ));

        configManager.registerArgument(new CommandLineArgument(
                "--random-agent","","randomAgent",
                (cm, value) -> cm.setConfigValue("randomAgent", value),
                value -> true,
                "Enables randomization of User-Agent header.",
                true,
                "false",
                true
        ));
    }

    private static final ConfigManager configManager = ConfigManager.getInstance();

    public static int getThreadCount() {
        return Integer.parseInt(configManager.getConfigValue("threadCount"));
    }

    public static String getWordlistPath() {
        return configManager.getConfigValue("wordlistPath");
    }

    public static String getUrl() {
        return configManager.getConfigValue("url");
    }

    public static Set<Range> getExcludedStatusCodes() {
        String codes = configManager.getConfigValue("excludedStatusCodes");
        if (codes == null || codes.trim().isEmpty()) {
            return new HashSet<>(); // Return an empty set if no value has been set
        }
        return Stream.of(codes.split(","))
                .map(String::trim)
                .map(Range::parseToRange)
                .collect(Collectors.toSet());
    }

    public static Set<Range> getExcludedLength() {
        String lengths = configManager.getConfigValue("excludeLength");
        if (lengths == null || lengths.trim().isEmpty()) {
            return new HashSet<>(); // Return an empty set if no value has been set
        }
        return Stream.of(lengths.split(","))
                .map(String::trim)
                .map(Range::parseToRange)
                .collect(Collectors.toSet());
    }

    public static RequestMode getRequestMode() {
        return RequestMode.valueOf(configManager.getConfigValue("requestMode"));
    }

    public static RequestMethod getRequestMethod() {
        return RequestMethod.valueOf(configManager.getConfigValue("requestMethod"));
    }

    public static int getMaxRetries() {
        return Integer.parseInt(configManager.getConfigValue("maxRetries"));
    }

    public static int getRateLimit() {
        return Integer.parseInt(configManager.getConfigValue("rateLimit"));
    }

    public static boolean getRateLimiterEnabled() {
        return Boolean.parseBoolean(configManager.getConfigValue("rateLimiterEnabled"));
    }

    public static boolean getMetricsEnabled() {
        return Boolean.parseBoolean(configManager.getConfigValue("metricsEnabled"));
    }

    public static boolean getDebugEnabled() {
        return Boolean.parseBoolean(configManager.getConfigValue("debugEnabled"));
    }

    public static boolean getRecursionEnabled() {
        return Boolean.parseBoolean(configManager.getConfigValue("recursionEnabled"));
    }

    public static String getUserAgent() {
        return configManager.getConfigValue("userAgent");
    }


    public static String getCookies() {
        return configManager.getConfigValue("cookies");
    }

    private static Set<String> headers = new HashSet<>();

    public static Set<String> getHeaders() {
        return headers;
        /*
        String headers = configManager.getConfigValue("headers");
        return headers == null ? Set.of() : Stream.of(headers.split(",\\s*")).collect(Collectors.toSet());

         */
    }

    public static boolean getRequestFileFuzzing() {
        return Boolean.parseBoolean(configManager.getConfigValue("requestFileFuzzing"));
    }

    public static String getRequestFilePath() {
        return configManager.getConfigValue("requestFilePath");
    }

    public static String getFuzzMarker() {
        return configManager.getConfigValue("fuzzMarker");
    }

    public static String getPostData() {
        return configManager.getConfigValue("postRequestData");
    }

    public static boolean getFollowRedirects() {
        return Boolean.parseBoolean(configManager.getConfigValue("followRedirects"));
    }

    public static boolean getRandomAgent() {
        return Boolean.parseBoolean(configManager.getConfigValue("randomAgent"));
    }
}
