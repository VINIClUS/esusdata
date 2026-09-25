package esusdata.run.acquisition;

import esusdata.source.pec.AllowedDestinations;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * How the execution plane reaches the source (ADR 0022). With a root certificate, every session is
 * TLS-only and the server must present a chain to it for the exact address in the envelope; without
 * one, sessions are plaintext.
 *
 * <p>Tech Spec §1.12.6 requires encrypted transport with certificate validation for SQL over a
 * network, so {@link #forDeployment} refuses a plaintext deployment whose allowlist names any
 * non-loopback address — plaintext stays possible only through a local tunnel (ADR 0003). The
 * allowlist already requires every address a host resolves to to be listed literally, so checking
 * the literal addresses covers every destination a session can actually reach. Entries are parsed
 * exactly as the allowlist parses them, so both see the same host.
 *
 * @param rootCertificate PEM file with the trusted root(s), or {@code null} for plaintext
 */
public record ExecPlaneTransport(String rootCertificate) {

    /** Only for loopback destinations — see {@link #forDeployment}. */
    public static final ExecPlaneTransport PLAINTEXT = new ExecPlaneTransport(null);

    private static final Pattern IPV4_LITERAL = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    /**
     * Fails startup instead of every run later: a configured certificate that cannot be read, or a
     * plaintext deployment that allows a non-loopback address.
     */
    public static ExecPlaneTransport forDeployment(List<String> allowedDestinations, String rootCertificate) {
        if (rootCertificate != null && !rootCertificate.isBlank()) {
            if (!Files.isReadable(Path.of(rootCertificate))) {
                throw new IllegalStateException(
                        "observatorio.source.tls-root-cert is not a readable file: " + rootCertificate);
            }
            return new ExecPlaneTransport(rootCertificate);
        }
        for (String entry : allowedDestinations == null ? List.<String>of() : allowedDestinations) {
            String host = AllowedDestinations.HostPort.parse(entry).host();
            if (isAddressLiteral(host) && !isLoopback(host)) {
                throw new IllegalStateException("observatorio.source.allowed-destinations allows " + entry
                        + ", which is not loopback: set observatorio.source.tls-root-cert so the session is "
                        + "TLS with certificate validation (Tech Spec §1.12.6, ADR 0022)");
            }
        }
        return PLAINTEXT;
    }

    private static boolean isAddressLiteral(String host) {
        return host.indexOf(':') >= 0 || IPV4_LITERAL.matcher(host).matches();
    }

    /** Address literals only: {@link InetAddress#getByName} never queries DNS for them. */
    private static boolean isLoopback(String addressLiteral) {
        try {
            return InetAddress.getByName(addressLiteral).isLoopbackAddress();
        } catch (UnknownHostException malformed) {
            return false;
        }
    }
}
