package esusdata.run.acquisition;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.source.SourceConnectivityCheck.Result;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.ReadBudget;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

/**
 * ADR 0022 against a real server: PostgreSQL 9.6.13 (the PEC's version) that accepts only
 * {@code hostssl} connections, with a certificate for the exact address the envelope names, issued
 * by a CA generated for this run. The real {@code observatorio-execplane} binary must complete a
 * verified TLS session with that CA and must refuse a server certificate from any other CA. A
 * plaintext envelope cannot connect at all, so a passing TLS diagnostic proves the session was
 * encrypted.
 *
 * <p>Needs {@code -Dobservatorio.execution-plane.binary}, Docker and {@code openssl}; skipped
 * otherwise, like the other live execution-plane tests.
 */
// Linux containers: excluded on Windows (package-windows.ps1).
@Tag("docker")
class ExecPlaneTlsLiveTest {

    private static final String BINARY_PROPERTY = "observatorio.execution-plane.binary";
    private static final String PASSWORD = "fixture_password";

    /** rw-r--r-- and rwxr-xr-x, as permission bits. */
    private static final int READABLE = 0b110_100_100;

    private static final int EXECUTABLE = 0b111_101_101;

    private static Path tls;

    private static GenericContainer<?> pg;
    private static String realBinary;
    private static String address;

    @AfterAll
    static void stopServerAndRemoveCertificates() throws IOException {
        if (pg != null) {
            pg.stop();
        }
        if (tls != null) {
            try (var files = Files.walk(tls)) {
                for (Path path :
                        files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                    Files.delete(path);
                }
            }
        }
    }

    @BeforeAll
    @SuppressWarnings("resource") // closed by Ryuk with the test JVM, like @Container fields
    static void startTlsOnlyPostgres() throws Exception {
        realBinary = System.getProperty(BINARY_PROPERTY);
        Assumptions.assumeTrue(
                realBinary != null && !realBinary.isBlank(), "Skipping: -D" + BINARY_PROPERTY + " not set");
        Assumptions.assumeTrue(openssl("version") == 0, "Skipping: openssl is not available");

        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Skipping: Docker is not available");

        tls = Files.createTempDirectory("execplane-tls");

        address = InetAddress.getByName(DockerClientFactory.instance().dockerHostIpAddress())
                .getHostAddress();
        issueCertificates(address);

        String initScript = """
                #!/bin/sh
                set -e
                cp /tls/server.crt /tls/server.key "$PGDATA"/
                chmod 600 "$PGDATA"/server.key
                echo "ssl = on" >> "$PGDATA"/postgresql.conf
                sed -i 's/^host /hostssl /' "$PGDATA"/pg_hba.conf
                """;
        pg = new GenericContainer<>("postgres:9.6.13")
                .withEnv("POSTGRES_USER", "fixture_user")
                .withEnv("POSTGRES_PASSWORD", PASSWORD)
                .withEnv("POSTGRES_DB", "esus_fixture")
                .withCopyToContainer(
                        Transferable.of(Files.readAllBytes(tls.resolve("server.crt")), READABLE), "/tls/server.crt")
                .withCopyToContainer(
                        Transferable.of(Files.readAllBytes(tls.resolve("server.key")), READABLE), "/tls/server.key")
                .withCopyToContainer(Transferable.of(initScript, EXECUTABLE), "/docker-entrypoint-initdb.d/tls.sh")
                .withExposedPorts(5432)
                .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*", 2)
                        .withStartupTimeout(Duration.ofMinutes(2)));
        pg.start();
    }

    private static Result diagnose(ExecPlaneTransport transport) {
        PecConnectionProperties properties = new PecConnectionProperties(
                "tls-src", address, pg.getMappedPort(5432), "esus_fixture", "fixture_user", "PASSWORD", "1100015");
        return new ExecPlaneConnectivityCheck(
                        List.of(realBinary), secretRef -> PASSWORD.toCharArray(), transport, Duration.ofSeconds(10))
                .check(properties, address, ReadBudget.initialEngineeringProposal());
    }

    @Test
    void aVerifiedTlsSessionReachesATlsOnlyServer() {
        assertThat(diagnose(new ExecPlaneTransport(tls.resolve("ca.crt").toString())))
                .isEqualTo(Result.OK);
    }

    @Test
    void aServerCertificateFromAnotherCaIsRefused() {
        assertThat(diagnose(new ExecPlaneTransport(tls.resolve("other-ca.crt").toString()))
                        .connected())
                .isFalse();
    }

    @Test
    void aPlaintextSessionCannotReachATlsOnlyServer() {
        assertThat(diagnose(ExecPlaneTransport.PLAINTEXT).connected()).isFalse();
    }

    private static void issueCertificates(String serverAddress) throws IOException, InterruptedException {
        String ca = tls.resolve("ca").toString();
        String other = tls.resolve("other-ca").toString();
        String server = tls.resolve("server").toString();
        Files.writeString(
                tls.resolve("server.ext"), "subjectAltName=IP:" + serverAddress + "\nextendedKeyUsage=serverAuth\n");
        for (String root : List.of(ca, other)) {
            require(openssl(
                    "req",
                    "-x509",
                    "-newkey",
                    "ec",
                    "-pkeyopt",
                    "ec_paramgen_curve:P-256",
                    "-nodes",
                    "-keyout",
                    root + ".key",
                    "-out",
                    root + ".crt",
                    "-days",
                    "2",
                    "-subj",
                    "/CN=execplane-tls-test",
                    "-addext",
                    "basicConstraints=critical,CA:TRUE",
                    "-addext",
                    "keyUsage=critical,keyCertSign"));
        }
        require(openssl(
                "req",
                "-newkey",
                "ec",
                "-pkeyopt",
                "ec_paramgen_curve:P-256",
                "-nodes",
                "-keyout",
                server + ".key",
                "-out",
                server + ".csr",
                "-subj",
                "/CN=" + serverAddress));
        require(openssl(
                "x509",
                "-req",
                "-in",
                server + ".csr",
                "-CA",
                ca + ".crt",
                "-CAkey",
                ca + ".key",
                "-CAcreateserial",
                "-out",
                server + ".crt",
                "-days",
                "2",
                "-extfile",
                tls.resolve("server.ext").toString()));
    }

    private static void require(int exitCode) {
        if (exitCode != 0) {
            throw new IllegalStateException("openssl failed with exit code " + exitCode);
        }
    }

    private static int openssl(String... arguments) throws IOException, InterruptedException {
        List<String> command = new java.util.ArrayList<>();
        command.add("openssl");
        command.addAll(List.of(arguments));
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
        } catch (IOException notInstalled) {
            return -1;
        }
        process.getInputStream().transferTo(java.io.OutputStream.nullOutputStream());
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return -1;
        }
        return process.exitValue();
    }
}
