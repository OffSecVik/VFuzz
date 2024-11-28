package vfuzz.core;


import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DNSFuzzer {

    private final String domain;
    private final Resolver resolver;
    private final boolean isWildcard;
    private final List<String> wildcardIPs;

    private static final ExecutorService executor = Executors.newFixedThreadPool(50);


    public DNSFuzzer(String domain, String dnsServer) throws Exception {
        this.domain = domain;

        // Set up the resolver (custom or default)
        if (dnsServer != null) {
            this.resolver = new SimpleResolver(dnsServer);
        } else {
            this.resolver = new SimpleResolver();
        }

        // Detect wildcard
        wildcardIPs = detectWildcard();
        isWildcard = !wildcardIPs.isEmpty();

        // System.out.println("Wildcard DNS detected: " + isWildcard);
        // System.out.println("Wildcard IPs: " + wildcardIPs);
    }

    private List<String> detectWildcard() throws Exception {
        String randomSubdomain = "random-" + System.currentTimeMillis() + "." + domain;

        Lookup lookup = new Lookup(randomSubdomain, Type.A);
        lookup.setResolver(resolver);
        Record[] records = lookup.run();

        if (records != null && lookup.getResult() == Lookup.SUCCESSFUL) {
            return Arrays.stream(records)
                    .filter(record -> record instanceof ARecord)
                    .map(record -> ((ARecord) record).getAddress().getHostAddress())
                    .toList();
        }

        return List.of(); // No wildcard detected
    }

    public void fuzz(String payload) {
        try {
            String subdomain = payload + "." + domain;
            Lookup lookup = new Lookup(subdomain, Type.A);
            lookup.setResolver(resolver);

            Record[] records = lookup.run();
            if (records != null && lookup.getResult() == Lookup.SUCCESSFUL) {
                List<String> ips = Arrays.stream(records)
                        .filter(record -> record instanceof ARecord)
                        .map(record -> ((ARecord) record).getAddress().getHostAddress())
                        .toList();

                if (isWildcard && ips.stream().anyMatch(wildcardIPs::contains)) {
                    return; // Ignore wildcard matches
                }

                System.out.println("Found: " + subdomain);
            }
        } catch (Exception ignored) {
            // System.err.println("Error querying subdomain: " + e.getMessage());
        }
    }

    public CompletableFuture<Void> fuzzAsync(String payload) {

        return CompletableFuture.runAsync(() -> {
            try {
                String subdomain = payload + "." + domain;
                Lookup lookup = new Lookup(subdomain, Type.A);
                lookup.setResolver(resolver);

                Record[] records = lookup.run();
                if (records != null && lookup.getResult() == Lookup.SUCCESSFUL) {
                    List<String> ips = Arrays.stream(records)
                            .filter(record -> record instanceof ARecord)
                            .map(record -> ((ARecord) record).getAddress().getHostAddress())
                            .toList();

                    if (isWildcard && ips.stream().anyMatch(wildcardIPs::contains)) {
                        return; // Ignore wildcard matches
                    }

                    System.out.println("Found: " + subdomain);
                }
            } catch (Exception e) {
                System.err.println("Error querying subdomain: " + e.getMessage());
            }
        }, executor);
    }
}
