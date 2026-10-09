package esusdata.run.worker;

import esusdata.run.acquisition.Acquisition;
import esusdata.run.acquisition.ExecPlaneAcquisition;
import esusdata.source.pec.AllowedDestinations;
import esusdata.source.pec.EnvFileSecretResolver;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Whether {@link ReferenceScopedExtracts} may read the PEC for an extract it does not hold, and with
 * what: {@link #none()} (it only loads; whatever is missing is a refusal) or {@link Live}, the
 * connection and identity of the secret file and a factory of the {@link Acquisition} that writes
 * into a given directory, because {@link ExecPlaneAcquisition} binds its output directory when it is
 * built and every acquisition goes to a directory of its own.
 */
public sealed interface AcquisitionInputs {

    /** The read model the PEC source of the harness is read through. */
    String READ_MODEL = "PEC_DW";

    String PASSWORD_KEY = "PEC_DB_PASSWORD";

    String HOST_KEY = "PEC_DB_HOST";

    String ROLE = "PRONTUARIO";

    /** True for {@link Live}: the PEC may be read. */
    boolean allowsLiveAcquisition();

    /** Loading only: nothing is ever acquired. */
    static AcquisitionInputs none() {
        return new None();
    }

    /**
     * Live acquisition through the execution plane, with the connection of the secret file: the same
     * identity and connection {@code SensitivityExtracts.acquire} builds from the same entries. The
     * host is {@link #pinned} first, so the allowlist holds the address every acquisition goes to: a
     * DNS name is acquired from as an address is, and an address the name resolves to later is
     * refused.
     *
     * @param environment the {@code PEC_*} entries of the secret file, from {@link #environmentOf}
     * @param envFile the secret file, which resolves the password when the child is spawned
     * @param binary the execution plane binary
     */
    static Live live(Map<String, String> environment, Path envFile, String binary) {
        Map<String, String> pinned = pinned(environment, InetAddress::getAllByName);
        String host = required(pinned, HOST_KEY);
        int port = Integer.parseInt(required(pinned, "PEC_DB_PORT"));
        PecSourceIdentity identity = new PecSourceIdentity(
                required(pinned, "PEC_SOURCE_ID"), required(pinned, "PEC_VERSION"), READ_MODEL, ROLE);
        PecConnectionProperties connection = new PecConnectionProperties(
                identity.sourceId(),
                host,
                port,
                required(pinned, "PEC_DB_NAME"),
                required(pinned, "PEC_DB_USER"),
                PASSWORD_KEY,
                required(pinned, "PEC_MUNICIPALITY_IBGE"));
        AllowedDestinations destinations =
                new AllowedDestinations(Set.of(new AllowedDestinations.HostPort(host, port)));
        return new Live(
                connection,
                identity,
                directory -> new ExecPlaneAcquisition(
                        List.of(binary),
                        new EnvFileSecretResolver(envFile),
                        destinations,
                        directory,
                        Clock.systemUTC(),
                        Duration.ofSeconds(10)));
    }

    /** A live acquisition through any {@link Acquisition} (a fixture writer, in a test). */
    static AcquisitionInputs of(PecConnectionProperties connection, PecSourceIdentity identity, Adapters adapters) {
        return new Live(connection, identity, adapters);
    }

    /**
     * The {@code PEC_*} entries with the PEC host resolved, once, to the one address every connection
     * of a run then goes to: the preflight, the coverage check and the acquisitions. Given the name,
     * a JDBC URL or a child of the execution plane would resolve it again on its own and could reach
     * another endpoint than the one the preflight proved read-only; given the address, none does. An
     * IPv6 address is written in brackets, as a JDBC URL needs it; an address stays as it is.
     *
     * @param dns how the host is resolved; {@code InetAddress::getAllByName} when live
     * @throws IllegalArgumentException when the host resolves to no address
     */
    static Map<String, String> pinned(Map<String, String> environment, AllowedDestinations.DnsResolver dns) {
        InetAddress[] addresses;
        try {
            addresses = dns.resolve(required(environment, HOST_KEY));
        } catch (UnknownHostException unknown) {
            throw new IllegalArgumentException("the PEC host of the secret file does not resolve", unknown);
        }
        if (addresses == null || addresses.length == 0 || addresses[0] == null) {
            throw new IllegalArgumentException("the PEC host of the secret file resolves to no address");
        }
        String address = addresses[0].getHostAddress();
        Map<String, String> pinned = new HashMap<>(environment);
        pinned.put(HOST_KEY, addresses[0] instanceof Inet6Address ? "[" + address + "]" : address);
        return Map.copyOf(pinned);
    }

    /**
     * The {@code PEC_*} entries of a secret file, without the password: the secret stays in the file
     * and is resolved by the one that needs it, when it needs it.
     */
    static Map<String, String> environmentOf(Path envFile) throws IOException {
        Map<String, String> environment = new HashMap<>();
        for (String line : Files.readAllLines(envFile)) {
            int equals = line.indexOf('=');
            if (equals > 0 && !line.strip().startsWith("#")) {
                environment.put(
                        line.substring(0, equals).strip(),
                        line.substring(equals + 1).strip());
            }
        }
        environment.remove(PASSWORD_KEY);
        return Map.copyOf(environment);
    }

    private static String required(Map<String, String> environment, String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("the secret file has no " + key);
        }
        return value;
    }

    /** The factory of the {@link Acquisition} that writes its extracts into {@code extractsDir}. */
    @FunctionalInterface
    interface Adapters {

        Acquisition into(Path extractsDir);
    }

    /** Nothing may be acquired. */
    record None() implements AcquisitionInputs {

        @Override
        public boolean allowsLiveAcquisition() {
            return false;
        }
    }

    /**
     * The PEC may be read.
     *
     * @param connection where and as whom
     * @param identity the identity the execution plane checks the compatibility matrix with
     * @param adapters the factory of the {@link Acquisition} of a directory
     */
    record Live(PecConnectionProperties connection, PecSourceIdentity identity, Adapters adapters)
            implements AcquisitionInputs {

        @Override
        public boolean allowsLiveAcquisition() {
            return true;
        }

        /**
         * The address the allowlist of {@link AcquisitionInputs#live} validates for this PEC, as a
         * child of the execution plane is given it (the {@code validatedHost} of a {@code
         * SourceCoverageCheck}): an address, never a name.
         *
         * @throws AllowedDestinations.DestinationNotAllowedException when the host is a name that was
         *     not {@link AcquisitionInputs#pinned}
         */
        public String validatedHost() {
            AllowedDestinations.HostPort pec = new AllowedDestinations.HostPort(connection.host(), connection.port());
            return new AllowedDestinations(Set.of(pec))
                    .assertAllowed(connection.host(), connection.port())
                    .getHostAddress();
        }
    }
}
