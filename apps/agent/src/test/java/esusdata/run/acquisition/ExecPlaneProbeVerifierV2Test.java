package esusdata.run.acquisition;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.source.pec.ColumnMetadata;
import esusdata.source.pec.CompatibilityFingerprint;
import esusdata.source.pec.CompatibilityProbeResult;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ProbeItem;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** A canonical v2 probe passes only when every part matches its own capability's exact entry (ADR 0030). */
class ExecPlaneProbeVerifierV2Test {

    private static final String CITIZEN_CHECKSUM = "sha256:" + "a".repeat(64);
    private static final String HOME_VISIT_CHECKSUM = "sha256:" + "b".repeat(64);
    private static final PecSourceIdentity IDENTITY = new PecSourceIdentity("src", "5.5.28", "PEC_DW", "PRONTUARIO");
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String OBJECTS = "{\"test_object\":{\"columns\":[{\"name\":\"col_a\",\"data_type\":\"text\","
            + "\"udt_name\":\"text\",\"is_nullable\":\"NO\",\"ordinal_position\":1}]}}";
    private static final List<AcquisitionPart> PARTS =
            List.of(part("citizen", CITIZEN_CHECKSUM, "person"), part("home_visit", HOME_VISIT_CHECKSUM, "home_visit"));

    private static PecCompatibilityMatrix matrix() {
        String fingerprint = CompatibilityFingerprint.compute(new CompatibilityProbeResult(
                "test_object",
                Map.of("col_a", new ColumnMetadata("text", "text", "NO", 1)),
                List.of(new ProbeItem.ColumnItem("col_a"))));
        String entry = """
                {"pec_versions": ["5.5.28"], "postgresql_version": "9.6.13", "adapter_version": "0.1.0",
                 "read_model": "PEC_DW", "installation_role": "PRONTUARIO", "capability": "%s",
                 "status": "VALIDATED", "query_checksum": "%s",
                 "objects_used": [{"object": "test_object", "signature_fingerprint": "%s", "columns_used": ["col_a"]}]}""";
        return PecCompatibilityMatrix.fromJson(
                "{\"schema_version\": \"2\", \"validation_status\": \"VALIDATED\", \"tested_with\": ["
                        + entry.formatted("citizen", CITIZEN_CHECKSUM, fingerprint) + ","
                        + entry.formatted("home_visit", HOME_VISIT_CHECKSUM, fingerprint) + "]}");
    }

    private static AcquisitionPart part(String capability, String checksum, String recordKind) {
        return new AcquisitionPart(
                capability,
                "0.1.0",
                checksum,
                recordKind,
                LocalDate.of(2026, 3, 1),
                LocalDate.of(2026, 4, 1),
                new TreeMap<>(),
                new TreeMap<>());
    }

    private static String report(int index, String capability, String adapterVersion, String checksum) {
        return "{\"index\":" + index + ",\"capability\":\"" + capability + "\",\"adapter_version\":\"" + adapterVersion
                + "\",\"query_checksum\":\"" + checksum + "\",\"objects\":" + OBJECTS + "}";
    }

    private static JsonNode probe(String... reports) {
        return MAPPER.readTree(
                "{\"type\":\"probe\",\"postgres_version\":\"9.6.13\",\"parts\":[" + String.join(",", reports) + "]}");
    }

    private static String mismatch(List<AcquisitionPart> parts, JsonNode probe) {
        return ExecPlaneProbeVerifier.mismatchV2(matrix(), parts, IDENTITY, probe);
    }

    @Test
    void everyPartMatchingItsOwnEntryPasses() {
        assertThat(mismatch(
                        PARTS,
                        probe(
                                report(0, "citizen", "0.1.0", CITIZEN_CHECKSUM),
                                report(1, "home_visit", "0.1.0", HOME_VISIT_CHECKSUM))))
                .isNull();
    }

    @Test
    void aProbeWithoutOneReportPerPartIsAMismatch() {
        JsonNode v1Probe = MAPPER.readTree("{\"type\":\"probe\",\"postgres_version\":\"9.6.13\",\"query_checksum\":\""
                + CITIZEN_CHECKSUM + "\",\"objects\":" + OBJECTS + "}");
        assertThat(mismatch(PARTS, v1Probe)).isEqualTo("execution plane probed 0 parts for the 2 requested");
        assertThat(mismatch(PARTS, probe(report(0, "citizen", "0.1.0", CITIZEN_CHECKSUM))))
                .isEqualTo("execution plane probed 1 parts for the 2 requested");
    }

    @Test
    void reportsOutOfOrderOrOfAnotherVersionAreAMismatch() {
        assertThat(mismatch(
                        PARTS,
                        probe(
                                report(1, "home_visit", "0.1.0", HOME_VISIT_CHECKSUM),
                                report(0, "citizen", "0.1.0", CITIZEN_CHECKSUM))))
                .isEqualTo("part 0 (citizen): execution plane probed home_visit@0.1.0 at index 1");
        assertThat(mismatch(
                        PARTS,
                        probe(
                                report(0, "citizen", "0.2.0", CITIZEN_CHECKSUM),
                                report(1, "home_visit", "0.1.0", HOME_VISIT_CHECKSUM))))
                .isEqualTo("part 0 (citizen): execution plane probed citizen@0.2.0 at index 0");
    }

    /** Each part is held to its own entry: one capability's checksum never vouches for another's. */
    @Test
    void eachPartIsCheckedAgainstItsOwnCapabilitysEntry() {
        assertThat(mismatch(
                        PARTS,
                        probe(
                                report(0, "citizen", "0.1.0", CITIZEN_CHECKSUM),
                                report(1, "home_visit", "0.1.0", CITIZEN_CHECKSUM))))
                .isEqualTo("part 1 (home_visit): query checksum mismatch: matrix has " + HOME_VISIT_CHECKSUM
                        + " but execution plane reported " + CITIZEN_CHECKSUM);
    }

    @Test
    void aPartWithoutAMatrixEntryIsAMismatch() {
        List<AcquisitionPart> parts =
                List.of(part("citizen", CITIZEN_CHECKSUM, "person"), part("condition_list", "sha256:c", "condition"));
        assertThat(mismatch(
                        parts,
                        probe(
                                report(0, "citizen", "0.1.0", CITIZEN_CHECKSUM),
                                report(1, "condition_list", "0.1.0", "sha256:c"))))
                .startsWith("part 1 (condition_list): no compatibility matrix entry");
    }
}
