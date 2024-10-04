package vfuzz.network.request;

import vfuzz.config.ConfigAccessor;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.AbstractMap;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;


/**
 * The {@code ParsedHttpRequest} class represents an HTTP request that has been parsed from a text file.
 *
 * <p>This class allows you to create a request by reading from a file, and it can also replace
 * specific fuzzing markers within the request with fuzzing payloads. The class stores the HTTP method,
 * URL, headers, and body, and provides utility methods for working with the parsed request.
 * </p>
 *
 * <p>It provides methods for:
 * <ul>
 *     <li>Reading a request from a file.</li>
 *     <li>Replacing fuzzing markers with provided payloads in the method, URL, headers, or body.</li>
 *     <li>Generating a deep copy of the request.</li>
 * </ul>
 * </p>
 */
public class ParsedHttpRequest {
    private String method;
    private String url;
    private Map<String, String> headers = new HashMap<>();
    private String body;

    /**
     * Default constructor for {@code ParsedHttpRequest}.
     */
    public ParsedHttpRequest() {
    }


    /**
     * Copy constructor that creates a deep copy of an existing {@code ParsedHttpRequest} object.
     *
     * @param that The {@code ParsedHttpRequest} object to copy.
     */
    public ParsedHttpRequest(ParsedHttpRequest that) {
        this.method = that.method;
        this.url = that.url;
        this.headers = new HashMap<>(that.headers); // TODO: Ensure that this actually makes a deep copy of the original headers Map
        this.body = that.body;
    }


    /**
     * Parses an HTTP request from a text file and returns a new {@code ParsedHttpRequest} object.
     *
     * <p>The file must contain an HTTP request in plain text format, with the request line (method and URL),
     * headers, and optionally a body. Each part is parsed and stored in the respective fields of the request object.</p>
     *
     * @param filePath The path to the text file containing the HTTP request.
     * @return A new {@code ParsedHttpRequest} object with the parsed values.
     * @throws IOException If there is an error reading the file.
     */
    public ParsedHttpRequest parseHttpRequestFromFile(String filePath) throws IOException {
        ParsedHttpRequest request = new ParsedHttpRequest();
        List<String> lines = Files.readAllLines(Paths.get(filePath), StandardCharsets.UTF_8);

        boolean inHeaders = true;
        StringBuilder bodyBuilder = new StringBuilder();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (i == 0) {
                // parse request line
                String[] requestLine = line.split(" ");
                request.setMethod(requestLine[0]);
                request.setUrl(requestLine[1]);
            } else if (inHeaders) {
                if (line.isEmpty()) {
                    inHeaders = false; // empty line: headers start
                } else {
                    // parse header
                    int colonIndex = line.indexOf(":");
                    String headerName = line.substring(0, colonIndex).trim();
                    String headerValue = line.substring(colonIndex + 1).trim();
                    request.getHeaders().put(headerName, headerValue);
                }
            } else {
                // append to body
                bodyBuilder.append(line).append("\n");
            }
        }
        request.setBody(bodyBuilder.toString().trim());
        return request;
    }

    /**
     * Replaces the fuzzing marker (e.g., "FUZZ") with the provided payload throughout the request.
     *
     * <p>This method checks for the fuzz marker in the URL, method, headers, and body, and replaces it with
     * the given payload. It allows the request to be customized dynamically during fuzzing operations.</p>
     *
     * @param payload The fuzzing payload that will replace the fuzz marker.
     */
    public void replaceFuzzMarker(String payload) {
        String fuzzMarker = ConfigAccessor.getConfigValue("fuzzMarker", String.class);
        // check and replace in URL
        if(this.url.contains(fuzzMarker)) {
            this.url = this.url.replace(fuzzMarker, payload);
            // System.out.println("Replacing " + fuzzMarker + " with " + payload);
            return;
        }

        // check and replace in method - included for completeness
        if (this.method.contains(fuzzMarker)) {
            this.method = this.method.replace(fuzzMarker, payload);
            return;
        }

        // check and replace in Body
        if (this.body.contains(fuzzMarker)) {
            this.body = this.body.replace(fuzzMarker, payload);
            return;
        }

        // check and replace in headers (key and value)
        this.headers = this.headers.entrySet().stream()
                .map(entry -> {
                    String key = entry.getKey().contains(fuzzMarker) ? entry.getKey().replace(fuzzMarker, payload) : entry.getKey();
                    String value = entry.getValue().contains(fuzzMarker) ? entry.getValue().replace(fuzzMarker, payload) : entry.getValue();
                    return new AbstractMap.SimpleEntry<>(key, value);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public void setHeaders(Map<String, String> headers) {
        this.headers = headers;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    /**
     * Returns a string representation of the {@code ParsedHttpRequest}, including the method, URL,
     * headers, and body.
     *
     * @return A string representation of the HTTP request.
     */
    public String toString() {
        return "Method: " + method +
                "\nUrl: " + url +
                "\nHeaders: " + headers.toString() +
                "\nBody: " + body;
    }
}
