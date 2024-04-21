package vfuzz.network;

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

public class ParsedHttpRequest {
    private String method;
    private String url;
    private Map<String, String> headers = new HashMap<>();
    private String body;

    public ParsedHttpRequest() {
    }

    public ParsedHttpRequest(ParsedHttpRequest that) { // copy constructor - this will be called very very often
        method = that.method;
        url = that.url;
        headers = that.headers; // TODO: Ensure that this actually makes a deep copy of the original headers Map
        body = that.body;
    }

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

    public String toString() {
        String r = "";
        r += "Method: " + method;
        r += "\nUrl " + url;
        r += "\nHeaders: " + headers.toString();
        r += "\nBody: " + body;
        return r;
    }
}
