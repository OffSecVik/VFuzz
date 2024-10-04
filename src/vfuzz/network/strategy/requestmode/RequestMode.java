package vfuzz.network.strategy.requestmode;

/**
 * The {@code RequestMode} enum represents the different modes of
 * making HTTP requests in the fuzzing process.
 *
 * <p>This enum is used by the fuzzer to determine the specific request
 * mode strategy to apply when sending HTTP requests. Each mode represents
 * a different approach to constructing and sending requests to the target.
 */
public enum RequestMode {

    /**
     * The standard request mode, where requests are made to the base URL
     * without any modifications.
     */
    STANDARD,

    /**
     * The virtual host (VHOST) request mode, where requests are made
     * using virtual host headers to simulate different hosts on the same server.
     */
    VHOST,

    /**
     * The subdomain request mode, where requests are made to
     * dynamically generated subdomains of the target URL.
     */
    SUBDOMAIN,

    /**
     * The fuzzing request mode, where the request is modified dynamically
     * with different payloads (e.g., fuzzing inputs) inserted into the URL path.
     */
    FUZZ
}