package br.gov.observatorioaps.sourceconnector;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Set;

/**
 * Deployment-administered allowlist of (host, port) destinations a source is permitted to
 * connect to — Tech Spec §1.12.6 / ENG-46: "porta/destino fora da allowlist... são recusados
 * antes da conexão", with "DNS re-resolution on every connection" so a destination whose DNS
 * record changes after approval does not get a silent free pass.
 *
 * <p>The allowlist itself is not widened by the same call that tests a connection (§1.12.6) — it
 * is a constructor argument here, supplied at deployment time, not mutable through any code path
 * this class exposes.
 */
public final class AllowedDestinations {

    private final Set<HostPort> allowed;
    private final DnsResolver dnsResolver;

    public AllowedDestinations(Set<HostPort> allowed) {
        this(allowed, InetAddress::getAllByName);
    }

    public AllowedDestinations(Set<HostPort> allowed, DnsResolver dnsResolver) {
        this.allowed = Set.copyOf(allowed);
        this.dnsResolver = dnsResolver;
    }

    /**
     * Resolves the host via DNS (never trusting a cached/previous resolution) and checks both the
     * literal host:port pair and the resolved address against the allowlist. Every resolved
     * IPv4/IPv6 address must be explicitly approved — prevents DNS rebinding attacks.
     */
    public InetAddress assertAllowed(String host, int port) {
        HostPort requested = new HostPort(host, port);
        if (!allowed.contains(requested)) {
            throw new DestinationNotAllowedException(
                    "Destination " + host + ":" + port + " is not in the approved allowlist.");
        }
        String lookupHost = requested.host();
        InetAddress[] resolvedAddresses;
        try {
            resolvedAddresses = dnsResolver.resolve(lookupHost);
        } catch (UnknownHostException e) {
            throw new DestinationNotAllowedException("Could not resolve host: " + host, e);
        }
        if (resolvedAddresses == null || resolvedAddresses.length == 0) {
            throw new DestinationNotAllowedException("Host resolved to no addresses: " + host);
        }

        for (InetAddress resolved : resolvedAddresses) {
            if (resolved == null) {
                throw new DestinationNotAllowedException("Host resolved to a null address: " + host);
            }
            String resolvedIp = resolved.getHostAddress();
            HostPort resolvedAddr = new HostPort(resolvedIp, port);
            if (!allowed.contains(resolvedAddr)) {
                throw new DestinationNotAllowedException(
                        "Host " + host + " resolved to address " + resolvedIp + ":" + port
                                + " which is not in the approved allowlist — DNS rebinding protection.");
            }
        }
        return resolvedAddresses[0];
    }

    @FunctionalInterface
    public interface DnsResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    public record HostPort(String host, int port) {
        public HostPort {
            if (host == null || host.isBlank()) {
                throw new IllegalArgumentException("allowlist host is required");
            }
            if (port <= 0 || port > 65535) {
                throw new IllegalArgumentException("allowlist port out of range: " + port);
            }
            host = normalizeHost(host);
        }

        private static String normalizeHost(String value) {
            String normalized = value.trim();
            if (normalized.startsWith("[") && normalized.endsWith("]")) {
                normalized = normalized.substring(1, normalized.length() - 1);
            }
            return normalized.toLowerCase(Locale.ROOT);
        }
    }

    public static final class DestinationNotAllowedException extends RuntimeException {
        public DestinationNotAllowedException(String message) {
            super(message);
        }

        public DestinationNotAllowedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
