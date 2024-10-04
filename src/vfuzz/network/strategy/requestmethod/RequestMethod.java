package vfuzz.network.strategy.requestmethod;

/**
 * The {@code RequestMethod} enum represents the different HTTP request methods
 * supported by the fuzzer.
 *
 * <p>This enum is used to determine the HTTP method that will be applied when
 * sending requests to the target. Each method represents a different HTTP action
 * that can be performed (e.g., GET for retrieving resources, POST for submitting data).
 */
public enum RequestMethod {

    /**
     * Represents an HTTP GET request, typically used for retrieving data from the server.
     */
    GET,

    /**
     * Represents an HTTP POST request, typically used for sending data to the server.
     */
    POST,

    /**
     * Represents an HTTP HEAD request, similar to GET but only retrieves headers.
     */
    HEAD

    // Uncomment the below line to support HTTP PUT requests in the future.
    // PUT
}
