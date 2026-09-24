package esusdata.run.acquisition;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.source.SourceConnectivityCheck.Result;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.ReadBudget;
import java.io.File;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecPlaneConnectivityCheckTest {

    private static final PecConnectionProperties PROPERTIES = new PecConnectionProperties(
            "src-1", "pec.example", 5432, "esus", "esus_leitura", "PEC_DB_PASSWORD", "3541307");

    private static Result run(String scenario, ReadBudget budget) {
        List<String> command = List.of(
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                "-cp",
                System.getProperty("java.class.path"),
                StubDiagnoseMain.class.getName(),
                scenario);
        return new ExecPlaneConnectivityCheck(
                        command, secretRef -> "fixture-password".toCharArray(), Duration.ofSeconds(5))
                .check(PROPERTIES, "127.0.0.1", budget);
    }

    private static Result run(String scenario) {
        return run(scenario, ReadBudget.initialEngineeringProposal());
    }

    @Test
    void diagnosedAndExitZeroIsConnected() {
        assertThat(run("diagnosed").connected()).isTrue();
    }

    @Test
    void theChildsSqlStateIsReturnedAndItsDetailIsNot() {
        assertThat(run("auth-failed")).isEqualTo(new Result("28P01"));
    }

    @Test
    void anErrorWithoutSqlStateIsAConnectionFailure() {
        assertThat(run("error-without-sqlstate")).isEqualTo(new Result("08001"));
    }

    @Test
    void exitZeroWithoutDiagnosedIsAProtocolViolation() {
        assertThat(run("exit0-without-diagnosed")).isEqualTo(new Result("08001"));
    }

    @Test
    void diagnosedFollowedByANonZeroExitIsAProtocolViolation() {
        assertThat(run("diagnosed-then-nonzero")).isEqualTo(new Result("08001"));
    }

    @Test
    void aMalformedEnvelopeMakesTheStubExitWithoutAnswering() {
        assertThat(run("unknown-scenario")).isEqualTo(new Result("08001"));
    }

    @Test
    void aChildThatHangsIsKilledAtTheDeadline() {
        ReadBudget shortBudget =
                new ReadBudget(1, Duration.ofMillis(500), Duration.ofMillis(500), 500, 500, 500, 1, 1_000);
        long started = System.nanoTime();
        Result result = new ExecPlaneConnectivityCheck(
                        List.of(
                                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                                "-cp",
                                System.getProperty("java.class.path"),
                                StubDiagnoseMain.class.getName(),
                                "hang"),
                        secretRef -> "fixture-password".toCharArray(),
                        Duration.ofMillis(500))
                .check(PROPERTIES, "127.0.0.1", shortBudget);
        assertThat(result).isEqualTo(new Result("08001"));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(20));
    }

    @Test
    void aBinaryThatCannotStartIsAConnectionFailure() {
        Result result = new ExecPlaneConnectivityCheck(
                        List.of("/nonexistent/observatorio-execplane"),
                        secretRef -> "p".toCharArray(),
                        Duration.ofSeconds(1))
                .check(PROPERTIES, "127.0.0.1", ReadBudget.initialEngineeringProposal());
        assertThat(result).isEqualTo(new Result("08001"));
    }
}
