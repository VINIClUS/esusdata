package esusdata.run.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.pack.c7.C7Pack;
import esusdata.result.ResultJson;
import esusdata.result.model.EvidenceRecord;
import esusdata.result.model.PublishedResult;
import esusdata.run.extract.ExtractFixturesV2;
import esusdata.run.extract.ExtractionManifest;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ENG-19 for C7: the MET-24 population of the ficha transcription (CT01: A=1/2, B=1/4, C=3/4,
 * D=0/2 ⇒ 40) as a synthetic v2 extract, replayed through {@code RunExecutor.runFromExtract}. The
 * gates are incomplete, so the municipality publishes {@code BLOCKED} with its exact subgroups;
 * team two (girls of 9 to 14 only) has no denominator in A, C and D and stays {@code RULE_AMBIGUITY}
 * (P10/AMB-C7-01). Every part carries its declared window, so the rule's coverage check passes.
 */
class C7PackReplayTest {

    private static final YearMonth COMPETENCIA = YearMonth.of(2026, 6);
    private static final String IBGE = CanonicalFixtures.IBGE;
    private static final String TEAM_ONE = "0000346268";
    private static final String TEAM_TWO = "0000346276";
    private static final String MEDICO = "225125";
    private static final LocalDate LINK = LocalDate.of(2025, 9, 1);

    @TempDir
    Path dataDir;

    private JobRunnerTestFixture fixture;

    @BeforeEach
    void setUp() {
        fixture = new JobRunnerTestFixture(dataDir, Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC));
        fixture.registerSource("src-1", IBGE);
        fixture.registerPrincipal("gestor", IBGE);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private ExtractionManifest extract(String id) throws Exception {
        ExtractFixturesV2.Builder builder = ExtractFixturesV2.forRule(new C7Pack(), COMPETENCIA);
        builder.add(CanonicalFixtures.team(TEAM_ONE, "2750325", "70"));
        builder.add(CanonicalFixtures.team(TEAM_TWO, "2750333", "70"));
        woman(builder, "p1", "1971-01-15", "2750325", TEAM_ONE); // 55: A, C, D
        woman(builder, "p2", "1966-01-15", "2750325", TEAM_ONE); // 60: A, C, D
        woman(builder, "p3", "2012-01-15", "2750325", TEAM_ONE); // 14: B, C
        woman(builder, "p4", "2006-01-15", "2750325", TEAM_ONE); // 20: C
        woman(builder, "p5", "2017-01-15", "2750333", TEAM_TWO); // 9: B
        woman(builder, "p6", "2015-01-15", "2750333", TEAM_TWO); // 11: B
        woman(builder, "p7", "2013-01-15", "2750333", TEAM_TWO); // 13: B
        builder.add(CanonicalFixtures.person("p8", LocalDate.of(1996, 1, 15), "MASCULINO"))
                .add(CanonicalFixtures.registration("p8", LINK, "2750325", TEAM_ONE));
        return builder.add(
                        Capabilities.PROCEDURE_PERFORMED,
                        CanonicalFixtures.procedure(
                                "p1", LocalDate.of(2024, 10, 15), "0203010086", "PERFORMED", MEDICO))
                .add(CanonicalFixtures.encounterWithProblems(
                        "p1", LocalDate.of(2026, 3, 15), MEDICO, List.of("W11"), List.of()))
                .add(CanonicalFixtures.encounterWithProblems(
                        "p3", LocalDate.of(2026, 4, 15), MEDICO, List.of(), List.of("Z300")))
                .add(CanonicalFixtures.encounterWithProblems(
                        "p4", LocalDate.of(2026, 1, 15), MEDICO, List.of("X01"), List.of()))
                .add(CanonicalFixtures.dose("p5", LocalDate.of(2026, 2, 1), "67", "1"))
                .write(fixture.extractsDir, id, "src-1");
    }

    private static void woman(ExtractFixturesV2.Builder builder, String key, String birth, String cnes, String ine) {
        builder.add(CanonicalFixtures.person(key, LocalDate.parse(birth), "FEMININO"))
                .add(CanonicalFixtures.registration(key, LINK, cnes, ine));
    }

    @Test
    void eng19_met24ExtractPublishesBlockedWithExactSubgroupsTeamsAndEvidence() throws Exception {
        RunExecutor.RunOutcome run = fixture.replay(extract("ext-c7-2026-06"), new C7Pack(), COMPETENCIA, "gestor");

        PublishedResult published = fixture.published(run.resultId(), IBGE);
        assertThat(published.status()).isEqualTo("BLOCKED");
        assertThat(published.valueText()).isNull();
        assertThat(published.valueExact()).isNull();
        assertThat(published.classification()).isNull();
        assertThat(published.valueKind()).isEqualTo("COMPOSITE_SCORE");
        assertThat(published.numeratorText()).isNull();
        assertThat(published.denominatorText()).isNull();
        assertThat(ResultJson.readComponents(published.componentsJson()))
                .extracting(
                        ResultJson.StoredComponent::code,
                        ResultJson.StoredComponent::kind,
                        ResultJson.StoredComponent::weight,
                        ResultJson.StoredComponent::numerator,
                        ResultJson.StoredComponent::denominator,
                        ResultJson.StoredComponent::status)
                .containsExactly(
                        tuple("A", "SUBGROUP", "20", "1", "2", "COMPUTED"),
                        tuple("B", "SUBGROUP", "30", "1", "4", "COMPUTED"),
                        tuple("C", "SUBGROUP", "30", "3", "4", "COMPUTED"),
                        tuple("D", "SUBGROUP", "20", "0", "2", "COMPUTED"));
        assertThat(ResultJson.readTeams(published.teamResultsJson()))
                .extracting(
                        ResultJson.StoredTeam::ine,
                        ResultJson.StoredTeam::cnes,
                        ResultJson.StoredTeam::status,
                        ResultJson.StoredTeam::valueText)
                .containsExactly(
                        tuple(TEAM_ONE, "2750325", "BLOCKED", null),
                        // C7-D4: team two has only B (1/3); A, C and D leave the sum and the divisor, so the
                        // team is COMPUTED (33,3333) and the release gates hide it as BLOCKED, no longer
                        // RULE_AMBIGUITY
                        tuple(TEAM_TWO, "2750333", "BLOCKED", null));

        List<EvidenceRecord> evidence = fixture.evidence(run.resultId(), IBGE);
        assertThat(evidence)
                .extracting(EvidenceRecord::subjectKey, EvidenceRecord::component, EvidenceRecord::decision)
                .containsExactly(
                        tuple("p1", null, "ELIGIBLE"),
                        tuple("p1", "A", "PRACTICE_MET"),
                        tuple("p1", "A", "SUPPORTING_EVENT"),
                        tuple("p1", "C", "PRACTICE_MET"),
                        tuple("p1", "C", "SUPPORTING_EVENT"),
                        tuple("p1", "D", "PRACTICE_NOT_MET"),
                        tuple("p2", null, "ELIGIBLE"),
                        tuple("p2", "A", "PRACTICE_NOT_MET"),
                        tuple("p2", "C", "PRACTICE_NOT_MET"),
                        tuple("p2", "D", "PRACTICE_NOT_MET"),
                        tuple("p3", null, "ELIGIBLE"),
                        tuple("p3", "B", "PRACTICE_NOT_MET"),
                        tuple("p3", "C", "PRACTICE_MET"),
                        tuple("p3", "C", "SUPPORTING_EVENT"),
                        tuple("p4", null, "ELIGIBLE"),
                        tuple("p4", "C", "PRACTICE_MET"),
                        tuple("p4", "C", "SUPPORTING_EVENT"),
                        tuple("p5", null, "ELIGIBLE"),
                        tuple("p5", "B", "PRACTICE_MET"),
                        tuple("p5", "B", "SUPPORTING_EVENT"),
                        tuple("p6", null, "ELIGIBLE"),
                        tuple("p6", "B", "PRACTICE_NOT_MET"),
                        tuple("p7", null, "ELIGIBLE"),
                        tuple("p7", "B", "PRACTICE_NOT_MET"),
                        tuple("p8", null, "EXCLUDED"));
        assertThat(evidence.getLast().reasonCode()).isEqualTo("EXCLUIDO_SEXO_NAO_ELEGIVEL");
        EvidenceRecord exam = evidence.get(2);
        assertThat(exam.careDate()).isEqualTo("2024-10-15");
        assertThat(exam.modality()).isEqualTo("MIP:PERFORMED");
        assertThat(exam.cbo()).isEqualTo(MEDICO);
        assertThat(evidence).allSatisfy(row -> {
            assertThat(row.subjectKind()).isEqualTo("PERSON");
            assertThat(row.points()).isNull();
            assertThat(row.criterionVersion()).isEqualTo(C7Pack.RULE_VERSION);
        });
    }

    @Test
    void eng19_theSameExtractReplayedTwiceHasTheSameInputFingerprintAndCounts() throws Exception {
        ExtractionManifest extract = extract("ext-c7-twice");

        PublishedResult first = fixture.published(
                fixture.replay(extract, new C7Pack(), COMPETENCIA, "gestor").resultId(), IBGE);
        PublishedResult second = fixture.published(
                fixture.replay(extract, new C7Pack(), COMPETENCIA, "gestor").resultId(), IBGE);

        assertThat(second.inputFingerprint())
                .isEqualTo(first.inputFingerprint())
                .startsWith("sha256:");
        assertThat(second.componentsJson()).isEqualTo(first.componentsJson());
        assertThat(second.teamResultsJson()).isEqualTo(first.teamResultsJson());
    }
}
