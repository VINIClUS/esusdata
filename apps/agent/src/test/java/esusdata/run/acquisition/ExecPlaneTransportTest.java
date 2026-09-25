package esusdata.run.acquisition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecPlaneTransportTest {

    @TempDir
    Path directory;

    @Test
    void loopbackDestinationsMayUsePlaintext() {
        assertThat(ExecPlaneTransport.forDeployment(List.of("127.0.0.1:15433", "::1:5433", "localhost:5432"), ""))
                .isEqualTo(ExecPlaneTransport.PLAINTEXT);
        assertThat(ExecPlaneTransport.forDeployment(null, null)).isEqualTo(ExecPlaneTransport.PLAINTEXT);
    }

    @Test
    void aNonLoopbackAddressWithoutARootCertificateFailsStartup() {
        assertThatThrownBy(() -> ExecPlaneTransport.forDeployment(List.of("192.168.1.253:5433"), ""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("192.168.1.253:5433")
                .hasMessageContaining("observatorio.source.tls-root-cert");
        assertThatThrownBy(() -> ExecPlaneTransport.forDeployment(List.of("[2001:db8::1]:5432"), " "))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aReadableRootCertificateSelectsTls() throws IOException {
        Path root = Files.writeString(directory.resolve("pec-ca.pem"), "-----BEGIN CERTIFICATE-----\n");

        ExecPlaneTransport transport = ExecPlaneTransport.forDeployment(List.of("192.168.1.253:5433"), root.toString());

        assertThat(transport.rootCertificate()).isEqualTo(root.toString());
    }

    @Test
    void anUnreadableRootCertificateFailsStartupInsteadOfFallingBackToPlaintext() {
        String missing = directory.resolve("missing.pem").toString();

        assertThatThrownBy(() -> ExecPlaneTransport.forDeployment(List.of("127.0.0.1:5433"), missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(missing);
    }
}
