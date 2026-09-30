package esusdata.run.acquisition;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.source.SourceCoverageCheck.PeriodCount;
import esusdata.source.SourceCoverageCheck.Result;
import esusdata.source.pec.ColumnMetadata;
import esusdata.source.pec.CompatibilityFingerprint;
import esusdata.source.pec.CompatibilityProbeResult;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.PeriodCoverageContract;
import esusdata.source.pec.ProbeItem;
import esusdata.source.pec.ReadBudget;
import java.io.File;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExecPlaneCoverageCheckTest {

    private static final PecConnectionProperties PROPERTIES = new PecConnectionProperties(
            "src-1", "pec.example", 5432, "esus", "esus_leitura", "PEC_DB_PASSWORD", "3541307");
    private static final PecSourceIdentity IDENTITY = new PecSourceIdentity("src-1", "5.5.28", "PEC_DW", "PRONTUARIO");
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
                "capability": "period_coverage",
                "status": "VALIDATED",
                "query_checksum": "%s",
                "objects_used": [
                  {"object": "test_object", "signature_fingerprint": "%s", "columns_used": ["col_a"]}
                ]
              }]
            }
            """.formatted(PeriodCoverageContract.QUERY_CHECKSUM, TEST_OBJECT_FINGERPRINT));

    private static Result run(String scenario) {
        List<String> command = List.of(
                System.getProperty("java.home") + File.separator + "bin" + File.separator + "java",
                "-cp",
                System.getProperty("java.class.path"),
                StubCoverageMain.class.getName(),
                scenario);
        return new ExecPlaneCoverageCheck(
                        command,
                        secretRef -> "fixture-password".toCharArray(),
                        ExecPlaneTransport.PLAINTEXT,
                        MATRIX,
                        Duration.ofSeconds(5))
                .check(
                        PROPERTIES,
                        IDENTITY,
                        "127.0.0.1",
                        YearMonth.of(2024, 3),
                        YearMonth.of(2026, 4),
                        ReadBudget.initialEngineeringProposal());
    }

    @Test
    void theWindowGoesInTheEnvelopeAndTheCountsPerCompetenciaComeBack() {
        assertThat(run("counts"))
                .isEqualTo(Result.checked(List.of(
                        new PeriodCount("3541307", "2026-02", 9_800),
                        new PeriodCount("3541307", "2026-03", 10_029),
                        new PeriodCount(null, "2026-03", 2))));
    }

    @Test
    void aPeriodThatIsNotAYearMonthIsAProtocolViolation() {
        assertThat(run("bad-period")).isEqualTo(Result.failed("08001"));
    }
}
