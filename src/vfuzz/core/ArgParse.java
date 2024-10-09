package vfuzz.core;

import org.apache.http.entity.ContentType;
import vfuzz.config.ConfigAccessor;
import vfuzz.config.ConfigManager;
import vfuzz.logging.Metrics;
import vfuzz.network.strategy.requestmethod.RequestMethod;
import vfuzz.network.strategy.requestmode.RequestMode;
import vfuzz.operations.Range;
import vfuzz.utils.Validator;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The {@code ArgParse} class is responsible for handling command-line argument registration and parsing.
 * It registers multiple arguments using the {@code ConfigManager} singleton, allowing various
 * options like thread count, URL, wordlist, HTTP methods, request modes, and other parameters
 * for the fuzzing tool to be set through the command line.
 *
 * <p>Each argument is registered with a validator to ensure that the user-provided values meet
 * expected formats (e.g., integers, valid URLs, or HTTP status codes). The class also provides
 * functionality for retrieving headers, excluded status codes, and excluded lengths.</p>
 */
public class ArgParse {

    private static final ConfigManager configManager = ConfigManager.getInstance();

    private static final Set<String> headers = new HashSet<>();


    /**
     * Registers all available command-line arguments with the {@code ConfigManager} instance.
     * Each argument can be configured with an associated action, validator, description,
     * and other attributes such as optionality or default values.
     */
    public static void registerArguments() {
        ConfigManager configManager = ConfigManager.getInstance();

        configManager.registerArgument(new CommandLineArgument(
                "-t", "--threads", "threadCount",
                (cm, value) -> cm.setConfigValue("threadCount", value), // Action: Set Value
                value -> Validator.isIntegerInRange(value, 1, 200),
                "Number of threads. Must be a number between 1 and 200.", // Description
                true, // Optional?
                "1", // Default Value
                false // isFlag? (e.g. --recursive)
        ));

        configManager.registerArgument(new CommandLineArgument(
                "-w", "--wordlist", "wordlistPath",
                (cm, value) -> cm.setConfigValue("wordlistPath", value),
                Validator::isNotEmpty, // Validator
                "Path to the word list. This argument is required.",
                false,
                null, // Null as default due to the arg being non-optional
                false
        ));

        configManager.registerArgument(new CommandLineArgument(
                "-u", "--url", "url",
                (cm, value) -> cm.setConfigValue("url", value),
                Validator::isValidUrlWithProcessing,
                "URL to the target website. This argument is required and must start with http:// or https://. Trailing slashes are automatically removed.",
                false,
                null,
                false
        ));

        configManager.registerArgument(new CommandLineArgument(
                "-E", "--excludeResult", "excludedResults",
                (cm, value) -> cm.setConfigValue("excludedResults", value),
                value -> true,
                "Results to exclude from being shown and used in recursive mode.",
                true,
                null,
                false
        ));

        configManager.registerArgument(new CommandLineArgument(
                "-e", "--excludeStatusCodes", "excludedStatusCodes",
                (cm, value) -> {
                    String[] parts = value.split(",");
                    List<String> validCodesAndRanges = new ArrayList<>();
                    for (String part : parts) {
                        // in case a range was provided
                        if (part.matches("^\\d+-\\d+$")) {
                            //noinspection DuplicatedCode
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
                Validator::isValidStatusCodeCsv,
                "List of HTTP status codes or ranges to exclude, separated by commas. For example: 404,405-410,505-560. Each code or range must be valid.",
                true,
                "404",
                false
        ));

        configManager.registerArgument(new CommandLineArgument(
                "-x", "--extensions","fileExtensions",
                (cm, value) -> {
                    String[] extensions = value.split(",");
                    List<String> fileExtensions = new ArrayList<>();
                    for (String e : extensions) {
                        e = e.trim();
                        if (!e.startsWith(".")) {
                            e = "." + e;
                        }
                        fileExtensions.add(e);
                    }
                    cm.setConfigValue("fileExtensions", String.join(",", fileExtensions));
                },
                value -> true,
                "List of file extensions to fuzz for.",
                true,
                null,
                false


        ));

        configManager.registerArgument(new CommandLineArgument(
                "-l", "--excludeLength", "excludeLength",
                (cm, value) -> {
                    String[] lengths = value.split(",");
                    List<String> validLengthsAndRanges = new ArrayList<>();
                    for (String length : lengths) {
                        // in case a range was provided
                        if (length.matches("^\\d+-\\d+$")) {
                            //noinspection DuplicatedCode
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
                Validator::isValidStatusCodeCsv,
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
                "--rate-limit", "", "rateLimit",
                (cm, value) -> {
                    try {
                        int rateLimit = Integer.parseInt(value);
                        cm.setConfigValue("rateLimit", String.valueOf(rateLimit));
                    } catch (NumberFormatException e) {
                        System.out.println("Error: --rate-limit requires an integer (max requests per second).");
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
                "Sets the maximum number of requests per second. This value must be a positive integer. Default is 4000.",
                true,
                "0",
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
                "-A", "--user-agent", "userAgent",
                (cm, value) -> headers.add("User-Agent: " + value),
                value -> !value.trim().isEmpty(),
                "Sets the user agent for requests. Example: --user-agent \"Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3\"",
                true,
                null,
                false
        ));

        configManager.registerArgument(new CommandLineArgument(
                "-r", "--request-file", "requestFileFuzzing",
                (cm, value) -> {
                    cm.setConfigValue("requestFilePath", value); //TODO: Fix wrong ConfigurationPrinter
                    cm.setConfigValue("requestFileFuzzing", "true");
                },
                Validator::isValidFile,
                "Specifies the filepath to the HTTP request file for fuzzing. This activates file-based fuzzing mode. Ensure the file exists. Example: -r \"/path/to/requestfile.txt\"",
                true,
                null,
                false
        ));

        configManager.registerArgument(new CommandLineArgument(
                "-H", "", "headers",
                (cm, value) -> headers.add(value),
                Validator::isValidHeader,
                "Sets custom headers for the requests. Each header must be in the 'Name: Value' format. Can be used multiple times for multiple headers. Example: -H \"Content-Type: application/json\"",
                true, null, false
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
                "--fuzz-marker", "", "fuzzMarker",
                (cm, value) -> cm.setConfigValue("fuzzMarker", value),
                value -> !value.trim().isEmpty(),
                "Specifies the fuzz marker within the request file that will be replaced with dynamic content. Example: --fuzz-marker \"FUZZ\"",
                true,
                "FUZZ",
                false
        ));

        configManager.registerArgument(new CommandLineArgument(
                "-d", "--post-data", "postRequestData",
                (cm, value) -> {
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

        configManager.registerArgument(new CommandLineArgument(
                "-h", "--help", "help",
                (cm, value) -> cm.setConfigValue("help", value),
                value -> true,
                "Displays this menu.",
                true,
                "false",
                true
        ));

        configManager.registerArgument(new CommandLineArgument(
                "--ignore-case", "", "ignoreCase",
                (cm, value) -> cm.setConfigValue("ignoreCase", "true"),
                value -> true,
                "Makes the fuzzer case insensitive. Can lead to forking with recursion, depending on the wordlist.",
                true,
                "false",
                true
        ));
    }

    /**
     * Retrieves a set of HTTP status codes or ranges that should be excluded from results.
     *
     * @return A set of {@link Range} objects representing the excluded status codes or ranges.
     * If no exclusions are configured, an empty set is returned.
     */
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

    /**
     * Retrieves a set of content lengths or length ranges that should be excluded from results.
     *
     * @return A set of {@link Range} objects representing the excluded content lengths or ranges.
     * If no exclusions are configured, an empty set is returned.
     */
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

    /**
     * Retrieves a set of custom headers that have been set for requests.
     *
     * @return A set of strings representing the custom headers. If no headers are set, an empty set is returned.
     */
    public static Set<String> getHeaders() {
        return headers;
    }

    public static ContentType getContentType() {
        ContentType contentType = null;
        for (String h : headers) {
            String[] header = h.split(":");
            if (header[0].equals("Content-Type")) {
                contentType = ContentType.parse(header[1]);
                break;
            }
        }
        return contentType;
    }
}
