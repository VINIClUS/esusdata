package esusdata.run.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import esusdata.indicator.GateFixtures;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.ExactRatio;
import esusdata.result.ResultJson;
import esusdata.result.model.EvidenceRecord;
import esusdata.result.model.PublishedResult;
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

/**
 * ENG-19 with a rule that computes ({@link PracticeTestRule}, every gate complete): the replay of a
 * synthetic v2 extract publishes the exact value, the practices, the teams and the evidence that
 * rebuilds the population (ENG-36), all read back as the API reads them.
 */
class ComputingRuleReplayTest {

    static final YearMonth COMPETENCIA = YearMonth.of(2026, 3);
    private static final String TEAM_ONE = PracticeTestRule.TEAM_ONE;
    private static final String TEAM_TWO = PracticeTestRule.TEAM_TWO;
    private static final String IBGE = CanonicalFixtures.IBGE;

    @TempDir
    Path dataDir;

    private JobRunnerTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new JobRunnerTestFixture(dataDir, Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC));
        fixture.gateRegistry = GateFixtures.registryPassing(new PracticeTestRule().descriptor());
        fixture.replayMatrix = CompatibilityMatrices.validated(
                List.of("5.4.37"),
                List.of(Capabilities.CITIZEN, Capabilities.INDIVIDUAL_REGISTRATION, Capabilities.CARE_ENCOUNTER));
        fixture.registerSource("src-1", IBGE);
        fixture.registerPrincipal("gestor", IBGE);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void eng19_aRuleThatComputesPublishesItsValuePracticesTeamsAndEvidence() throws Exception {
        ExtractionManifest extract = PracticeTestRule.population(
                        ExtractFixturesV2.forRule(new PracticeTestRule(), COMPETENCIA))
                .write(fixture.extractsDir, "ext-praticas-2026-03", "src-1");

        RunExecutor.RunOutcome run = fixture.replay(extract, new PracticeTestRule(), COMPETENCIA, "gestor");

        PublishedResult published = fixture.published(run.resultId(), IBGE);
        assertThat(published.status()).isEqualTo("COMPUTED");
        assertThat(published.valueText()).isEqualTo("50.0000");
        assertThat(published.valueKind()).isEqualTo("SCORE");
        // 150/3 stored in lowest terms.
        assertThat(published.valueExact()).isEqualTo(ExactRatio.of(50, 1));
        assertThat(published.numeratorText()).isEqualTo("150");
        assertThat(published.denominatorText()).isEqualTo("3");
        assertThat(published.classification()).isEqualTo("SUFICIENTE");
        assertThat(published.consolidationEligible()).isTrue();
        assertThat(ResultJson.readComponents(published.componentsJson()))
                .extracting(
                        ResultJson.StoredComponent::code,
                        ResultJson.StoredComponent::kind,
                        ResultJson.StoredComponent::weight,
                        ResultJson.StoredComponent::numerator,
                        ResultJson.StoredComponent::denominator,
                        ResultJson.StoredComponent::status)
                .containsExactly(
                        tuple("A", "PRACTICE", "50", "1", "3", "COMPUTED"),
                        tuple("B", "PRACTICE", "50", "2", "3", "COMPUTED"));
        assertThat(ResultJson.readComponents(published.componentsJson()).get(1).value())
                .isEqualTo(ExactRatio.of(2, 3));
        assertThat(ResultJson.readTeams(published.teamResultsJson()))
                .extracting(
                        ResultJson.StoredTeam::ine,
                        ResultJson.StoredTeam::cnes,
                        ResultJson.StoredTeam::status,
                        ResultJson.StoredTeam::valueText,
                        ResultJson.StoredTeam::classification)
                .containsExactly(
                        tuple(TEAM_ONE, "2750325", "COMPUTED", "75.0000", "BOM"),
                        tuple(TEAM_TWO, "2750333", "COMPUTED", "0.0000", "REGULAR"));

        List<EvidenceRecord> evidence = fixture.evidence(run.resultId(), IBGE);
        assertThat(evidence)
                .extracting(
                        EvidenceRecord::subjectKey,
                        EvidenceRecord::component,
                        EvidenceRecord::decision,
                        EvidenceRecord::points,
                        EvidenceRecord::ine)
                .containsExactly(
                        tuple("p1", null, "ELIGIBLE", "100", TEAM_ONE),
                        tuple("p1", "A", "PRACTICE_MET", "50", TEAM_ONE),
                        tuple("p1", "A", "SUPPORTING_EVENT", null, TEAM_ONE),
                        tuple("p1", "B", "PRACTICE_MET", "50", TEAM_ONE),
                        tuple("p1", "B", "SUPPORTING_EVENT", null, TEAM_ONE),
                        tuple("p2", null, "ELIGIBLE", "50", TEAM_ONE),
                        tuple("p2", "A", "PRACTICE_NOT_MET", "0", TEAM_ONE),
                        tuple("p2", "B", "PRACTICE_MET", "50", TEAM_ONE),
                        tuple("p2", "B", "SUPPORTING_EVENT", null, TEAM_ONE),
                        tuple("p3", null, "ELIGIBLE", "0", TEAM_TWO),
                        tuple("p3", "A", "PRACTICE_NOT_MET", "0", TEAM_TWO),
                        tuple("p3", "B", "PRACTICE_NOT_MET", "0", TEAM_TWO),
                        tuple("p4", null, "EXCLUDED", null, null));
        assertThat(evidence).allSatisfy(row -> {
            assertThat(row.subjectKind()).isEqualTo("PERSON");
            assertThat(row.criterionVersion()).isEqualTo(PracticeTestRule.RULE_VERSION);
        });
        EvidenceRecord supporting = evidence.get(2);
        // The supporting event names the source record, namespaced by the extract's source.
        assertThat(supporting.sourceEntityType()).isEqualTo("tb_fat_atendimento_individual");
        assertThat(supporting.sourceRecordId()).isNotBlank();
        assertThat(supporting.careDate()).isEqualTo("2026-02-10");
        assertThat(supporting.cbo()).isEqualTo("225142");
        assertThat(evidence.getLast().reasonCode()).isEqualTo("SEM_CADASTRO");
    }
}
