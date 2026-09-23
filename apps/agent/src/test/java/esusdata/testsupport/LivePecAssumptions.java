package esusdata.testsupport;

import org.junit.jupiter.api.Assumptions;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Shared skip-guard for tests that talk to the real PEC on CT 133 through the dev SSH tunnel
 * (ADR 0003). These tests must degrade to <em>skipped</em>, not failed, both in CI (no secret
 * file) and on this machine after the NetBird session expires (secret file still present, but
 * the tunnel is dead) — a live test failing for either reason should never read as a code
 * regression.
 */
public final class LivePecAssumptions {

    public static final Path ENV_FILE =
            Path.of(System.getProperty("user.home"), ".config", "observatorio-aps", "pec.env");

    private LivePecAssumptions() {
    }

    public static void assumeReachable(String host, int port) {
        Assumptions.assumeTrue(Files.exists(ENV_FILE),
                "Skipping: no dev PEC secret file at " + ENV_FILE);
        Assumptions.assumeTrue(isReachable(host, port),
                "Skipping: " + host + ":" + port + " not reachable — SSH tunnel likely down "
                        + "(NetBird session may have expired; see ADR 0003 for the reconnect command)");
    }

    public static boolean isReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
