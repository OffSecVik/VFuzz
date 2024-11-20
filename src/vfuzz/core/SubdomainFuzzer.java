package vfuzz.core;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

public class SubdomainFuzzer {

    private final String domain;
    private final Set<String> wildcardIps = new HashSet<>();
    private boolean isWildcard;

    public SubdomainFuzzer(String domain) {
        this.domain = domain;
    }

    public void detectWildcard() {
        String randomSubdomain = UUID.randomUUID() + "." + domain;
        try {
            InetAddress[] addresses = InetAddress.getAllByName(randomSubdomain);
            for (InetAddress address : addresses) {
                wildcardIps.add(address.getHostAddress());
            }
            isWildcard = !wildcardIps.isEmpty();
            System.out.println("Wildcard DNS detected: " + isWildcard);
            System.out.println("Wildcard IPs: " + wildcardIps);
        } catch (UnknownHostException e) {
            isWildcard = false;
            System.out.println("No wildcard DNS detected.");
        }
    }

    public void testSubdomain(String payload) {
        String subdomain = payload + "." + domain;
        resolveSubdomain(subdomain);
    }


    private void resolveSubdomain(String subdomain) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(subdomain);
            List<String> ips = new ArrayList<>();
            for (InetAddress address : addresses) {
                ips.add(address.getHostAddress());
            }

            if (isWildcard && ips.stream().anyMatch(wildcardIps::contains)) {
                return; // Filter out wildcard matches
            }

            System.out.println("Found: " + subdomain + " -> " + ips);
        } catch (UnknownHostException e) {
            return; // Subdomain does not exist
        }
    }
}
