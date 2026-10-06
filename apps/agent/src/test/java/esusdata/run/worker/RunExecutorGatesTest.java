package esusdata.run.worker;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.GateFixtures;
import esusdata.indicator.ReleaseGateRegistry;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.pack.c1.C1Pack;
import esusdata.indicator.pack.c1.C1Rule;
import esusdata.result.model.PublishedResult;
import esusdata.run.extract.ExtractFixtures;
import esusdata.run.extract.ExtractFixturesV2;
import esusdata.run.extract.ExtractionManifest;
import esusdata.source.pec.CompatibilityMatrices;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ADR 0032: the executor is the one place the release gates are applied, whatever the rule. The same
 * rule ({@link PracticeTestRule}, no standing limitation) is replayed under different gate states:
 * only with A and D passed in the registry and every capability {@code VALIDATED} for the source
 * does it publish a value, and every result carries the snapshot of the gates it was released under.
 */
class RunExecutorGatesTest {

    private static final YearMonth COMPETENCIA = YearMonth.of(2026, 3);
    private static final String IBGE = CanonicalFixtures.IBGE;
    private static final List<String> CAPABILITIES =
            List.of(Capabilities.CITIZEN, Capabilities.INDIVIDUAL_REGISTRATION, Capabilities.CARE_ENCOUNTER);

    @TempDir
    Path dataDir;

    private JobRunnerTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new JobRunnerTestFixture(dataDir, Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC));
        fixture.replayMatrix = CompatibilityMatrices.validated(List.of("5.4.37"), CAPABILITIES);
        fixture.registerSource("src-1", IBGE);
        fixture.registerPrincipal("gestor", IBGE);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private PublishedResult replay(String extractionId) throws Exception {
        ExtractionManifest extract = PracticeTestRule.population(
                        ExtractFixturesV2.forRule(new PracticeTestRule(), COMPETENCIA))
                .write(fixture.extractsDir, extractionId, "src-1");
        RunExecutor.RunOutcome run = fixture.replay(extract, new PracticeTestRule(), COMPETENCIA, "gestor");
        return fixture.published(run.resultId(), IBGE);
    }

    private JsonNode snapshotOf(PublishedResult published) {
        String json = fixture.jdbc.queryForObject(
                "select gate_snapshot_json from results where result_id = ?", String.class, published.resultId());
        return new ObjectMapper().readTree(json);
    }

    @Test
    void withNoGatePassedInTheRegistryTheValueIsBlockedAndTheSnapshotSaysWhy() throws Exception {
        PublishedResult published = replay("ext-gates-pending");

        assertThat(published.status()).isEqualTo("BLOCKED");
        assertThat(published.valueText()).isNull();
        assertThat(published.numeratorText()).isEqualTo("150");
        assertThat(published.limitationsJson())
                .contains("Portão A (fonte e vigência) incompleto", "Portão D (reconciliação) incompleto")
                .doesNotContain("Portão B", "Portão C");
        JsonNode snapshot = snapshotOf(published);
        assertThat(snapshot.get("pack").asString()).isEqualTo(PracticeTestRule.ID);
        assertThat(snapshot.get("gates").get("A").get("status").asString()).isEqualTo("PENDING");
        assertThat(snapshot.get("gates").get("B").get("status").asString()).isEqualTo("PASSED");
        assertThat(snapshot.get("gates").get("C").get("status").asString()).isEqualTo("PASSED");
    }

    @Test
    void aCapabilityTheSourceDoesNotValidateKeepsPortaoCFailedEvenWithAAndDPassed() throws Exception {
        fixture.gateRegistry = GateFixtures.registryPassing(new PracticeTestRule().descriptor());
        fixture.replayMatrix = CompatibilityMatrices.validated(List.of("5.4.37"), List.of(Capabilities.CITIZEN));

        PublishedResult published = replay("ext-gates-c");

        assertThat(published.status()).isEqualTo("BLOCKED");
        assertThat(published.limitationsJson()).contains("Portão C (adaptador) incompleto");
        assertThat(snapshotOf(published).get("gates").get("C").get("status").asString())
                .isEqualTo("FAILED");
    }

    @Test
    void anOlderRuleVersionInTheRegistryNeverCountsAndIsSnapshottedAsStale() throws Exception {
        String old = PracticeTestRule.ID + "@0.0.1";
        String json = "{\"schema_version\":\"1\",\"packs\":[{\"pack\":\"" + PracticeTestRule.ID
                + "\",\"rule_version\":\"" + old + "\",\"blocking_gaps_closed\":[],\"gates\":{"
                + "\"A\":{\"status\":\"PASSED\",\"check\":\"conferencia-fichas@1\",\"checked_at\":\"2026-10-06\","
                + "\"evidence\":[{\"kind\":\"doc\",\"ref\":\"docs/x.md\",\"sha256\":\"" + GateFixtures.SHA + "\"}]},"
                + "\"D\":{\"status\":\"PENDING\",\"evidence\":[]}}}]}";
        fixture.gateRegistry = ReleaseGateRegistry.fromJson(json, List.of(new PracticeTestRule().descriptor()));

        PublishedResult published = replay("ext-gates-stale");

        assertThat(published.status()).isEqualTo("BLOCKED");
        assertThat(snapshotOf(published).get("stale").asBoolean()).isTrue();
        assertThat(snapshotOf(published).get("gates").get("A").get("status").asString())
                .isEqualTo("PENDING");
    }

    @Test
    void c1WithoutADenominatorStaysBlockedLikeV019AndCarriesItsSnapshot() throws Exception {
        // Only unmapped encounters: 0/0. v0.1.9 published C1 BLOCKED here, never NO_DENOMINATOR.
        ExtractionManifest extract =
                ExtractFixtures.write(fixture.extractsDir, "ext-c1-empty", "src-1", IBGE, "2026-03", 0, 0, 2);

        RunExecutor.RunOutcome run = fixture.replay(extract, new C1Pack(), COMPETENCIA, "gestor");

        PublishedResult published = fixture.published(run.resultId(), IBGE);
        assertThat(published.status()).isEqualTo("BLOCKED");
        assertThat(published.numeratorText()).isEqualTo("0");
        assertThat(published.denominatorText()).isEqualTo("0");
        assertThat(published.limitationsJson())
                .contains("Portão D (reconciliação) incompleto")
                .doesNotContain("Portão B");
        JsonNode snapshot = snapshotOf(published);
        assertThat(snapshot.get("pack").asString()).isEqualTo(C1Rule.INDICATOR_PACK);
        // C1-LIM-03 is closed (ADR 0033): gate B passes; the result stays BLOCKED only for gate D
        assertThat(snapshot.get("gates").get("B").get("status").asString()).isEqualTo("PASSED");
    }

    @Test
    void withEveryGatePassedARuleWithoutStandingLimitationsPublishesItsValue() throws Exception {
        fixture.gateRegistry = GateFixtures.registryPassing(new PracticeTestRule().descriptor());

        PublishedResult published = replay("ext-gates-all");

        assertThat(published.status()).isEqualTo("COMPUTED");
        assertThat(published.valueText()).isEqualTo("50.0000");
        JsonNode gates = snapshotOf(published).get("gates");
        for (String gate : List.of("A", "B", "C", "D")) {
            assertThat(gates.get(gate).get("status").asString()).as(gate).isEqualTo("PASSED");
        }
        assertThat(gates.get("A").get("check").asString()).isEqualTo("conferencia-fichas@1");
    }
}
