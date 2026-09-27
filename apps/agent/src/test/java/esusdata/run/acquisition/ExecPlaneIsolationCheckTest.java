package esusdata.run.acquisition;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.source.SourceIsolationCheck.MunicipalityCount;
import esusdata.source.SourceIsolationCheck.Result;
import esusdata.source.SourceIsolationCheck.Status;
import esusdata.source.pec.ColumnMetadata;
import esusdata.source.pec.CompatibilityFingerprint;
import esusdata.source.pec.CompatibilityProbeResult;
import esusdata.source.pec.MunicipalIsolationContract;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ProbeItem;
import esusdata.source.pec.ReadBudget;
import java.io.File;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExecPlaneIsolationCheckTest {

    private static final PecConnectionProperties PROPERTIES = new PecConnectionProperties(
            "src-1", "pec.example", 5432, "esus", "esus_leitura", "PEC_DB_PASSWORD", "3541307");
    private static final PecSourceIdentity IDENTITY = new PecSourceIdentity("src-1", "5.5.28", "PEC_DW", "PRONTUARIO");
    private static final YearMonth MARCH = YearMonth.of(2026, 3);
    private static final String TEST_OBJECT_FINGERPRINT = CompatibilityFingerprint.compute(new CompatibilityProbeResult(
            "test_object",
            Map.of("col_a", new ColumnMetadata("text", "text", "NO", 1)),
            List.of(new ProbeItem.ColumnItem("col_a"))));
    private static final PecCompatibilityMatrix MATRIX = PecCompatibilityMatrix.fromJson(
            """
            {
              "schema_version": "2",
              "validation_status": "VALIDATED",
              "tested_with": [{
                "pec_versions": ["5.5.28"],
                "postgresql_version": "9.6.13",
                "adapter_version": "0.1.0",
                "read_model": "PEC_DW",
                "installation_role": "PRONTUARIO",
                "capability": "municipal_isolation",
                "status": "VALIDATED",
                "query_checksum": "%s",
                "objects_used": [
                  {"object": "test_object", "signature_fingerprint": "%s", "columns_used": ["col_a"]}
                ]
              }]
            }
            """.formatted(MunicipalIsolationContract.QUERY_CHECKSUM, TEST_OBJECT_FINGERPRINT));

    private static Result run(String scenario, ReadBudget budget, Duration exitGrace) {
        List<String> command = List.of(
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                "-cp",
                System.getProperty("java.class.path"),
                StubIsolationMain.class.getName(),
                scenario);
        return new ExecPlaneIsolationCheck(
                        command,
                        secretRef -> "fixture-password".toCharArray(),
                        ExecPlaneTransport.PLAINTEXT,
                        MATRIX,
                        exitGrace)
                .check(PROPERTIES, IDENTITY, "127.0.0.1", MARCH, budget);
    }

    private static Result run(String scenario) {
        return run(scenario, ReadBudget.initialEngineeringProposal(), Duration.ofSeconds(5));
    }

    @Test
    void aMatchingProbeIsAnsweredWithProceedAndTheCountsComeBack() {
        assertThat(run("counts"))
                .isEqualTo(Result.checked(
                        List.of(new MunicipalityCount("3541307", 10_029), new MunicipalityCount(null, 2))));
    }

    @Test
    void aProbeThatDoesNotMatchTheMatrixIsAbortedBeforeTheQuery() {
        assertThat(run("mismatch").status()).isEqualTo(Status.COMPATIBILITY_MISMATCH);
    }

    @Test
    void theChildsSqlStateIsReturnedAndItsDetailIsNot() {
        assertThat(run("auth-failed")).isEqualTo(Result.failed("28P01"));
    }

    @Test
    void aBudgetOverrunIsItsOwnOutcome() {
        assertThat(run("budget").status()).isEqualTo(Status.SOURCE_BUDGET_EXCEEDED);
    }

    @Test
    void countsFollowedByANonZeroExitAreAProtocolViolation() {
        assertThat(run("counts-then-nonzero")).isEqualTo(Result.failed("08001"));
    }

    @Test
    void aNegativeCountIsAProtocolViolation() {
        assertThat(run("negative-count")).isEqualTo(Result.failed("08001"));
    }

    @Test
    void aChildThatHangsIsKilledAtTheDeadline() {
        ReadBudget shortBudget =
                new ReadBudget(1, Duration.ofMillis(500), Duration.ofMillis(500), 500, 500, 500, 1, 500);
        long started = System.nanoTime();
        assertThat(run("hang", shortBudget, Duration.ofMillis(500))).isEqualTo(Result.failed("08001"));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(20));
    }

    @Test
    void aBinaryThatCannotStartIsAConnectionFailure() {
        Result result = new ExecPlaneIsolationCheck(
                        List.of("/nonexistent/observatorio-execplane"),
                        secretRef -> "p".toCharArray(),
                        ExecPlaneTransport.PLAINTEXT,
                        MATRIX,
                        Duration.ofSeconds(1))
                .check(PROPERTIES, IDENTITY, "127.0.0.1", MARCH, ReadBudget.initialEngineeringProposal());
        assertThat(result).isEqualTo(Result.failed("08001"));
    }
}
