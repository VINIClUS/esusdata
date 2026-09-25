package esusdata.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.config.SqliteProperties;
import esusdata.run.acquisition.ExecPlaneAcquisition;
import esusdata.run.acquisition.ExecPlaneTransport;
import esusdata.source.SourceConnectionProperties;
import esusdata.source.pec.AllowedDestinations;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

/** ADR 0016: the execution plane is the only acquisition path — no JDBC fallback exists to fall to. */
class AcquisitionWiringTest {

    @TempDir
    Path dataDir;

    private Object wire(String binary) {
        return new RunConfig()
                .acquisitionPort(
                        new SqliteProperties(dataDir.toString()),
                        Clock.systemUTC(),
                        new ExecPlaneProperties(binary, Duration.ofSeconds(30)),
                        secretRef -> new char[0],
                        new AllowedDestinations(Set.of()),
                        ExecPlaneTransport.PLAINTEXT);
    }

    @Test
    void anEmptyBinaryRefusesToStartInsteadOfFallingBackToJdbc() {
        assertThatThrownBy(() -> wire(""))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("observatorio.execution-plane.binary");
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "Windows has no executable bit to withhold")
    void aBinaryThatIsNotExecutableRefusesToStart() throws Exception {
        Path notExecutable = Files.createFile(dataDir.resolve("observatorio-execplane"));

        assertThatThrownBy(() -> wire(notExecutable.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(notExecutable.toString());
    }

    @Test
    void aPlaintextDeploymentThatAllowsTheLanFailsStartup() {
        assertThatThrownBy(() -> new RunConfig()
                        .execPlaneTransport(new SourceConnectionProperties(List.of("192.168.1.253:5433"), "", "")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("observatorio.source.tls-root-cert");
    }

    @Test
    void anExecutableBinaryWiresTheExecutionPlane() {
        String anyExecutable = ProcessHandle.current().info().command().orElseThrow();

        assertThat(wire(anyExecutable)).isInstanceOf(ExecPlaneAcquisition.class);
    }
}
