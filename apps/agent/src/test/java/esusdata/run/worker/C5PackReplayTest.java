package esusdata.run.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.pack.c5.C5Pack;
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
 * ENG-19 for C5 (ADR 0030): a synthetic canonical v2 extract of competência 2026-03 replayed
 * through {@code RunExecutor.runFromExtract} publishes {@code BLOCKED} — the release gates are
 * incomplete — with the exact counts, practices, teams and evidence of the ficha's rule, and the
 * same extract replayed again has the same input fingerprint and the same result.
 *
 * <p>Population: p1 (eSF one) meets A, B, C and D (100); p2 (eSF one) only A (25); p3 (eSF two)
 * has every eligible condition resolved (interrupted, item 15); p4 (eSF two) meets nothing (0).
 * Supporting events carry the team of their own record (none on the encounters and procedures
 * here; the ACS visits carry eSF one).
 * Value 125/3, components A 2/3, B 1/3, C 1/3, D 1/3.
 */
class C5PackReplayTest {

    private static final YearMonth COMPETENCIA = YearMonth.of(2026, 3);
    private static final String IBGE = CanonicalFixtures.IBGE;
    private static final String TEAM_ONE = "0000346268";
    private static final String TEAM_TWO = "0000346276";
    private static final String CNES_ONE = "2750325";
    private static final String CNES_TWO = "2750333";
    private static final String DOCTOR = "225142";
    private static final String NURSING_TECHNICIAN = "322205";
    private static final String NUTRITIONIST = "223710";
    private static final String ACS = "515105";

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

    @Test
    void eng19_replayPublishesBlockedWithExactCountsPracticesTeamsAndEvidence() throws Exception {
        ExtractionManifest extract = extract("ext-c5-2026-03");

        RunExecutor.RunOutcome run = fixture.replay(extract, new C5Pack(), COMPETENCIA, "gestor");

        PublishedResult published = fixture.published(run.resultId(), IBGE);
        assertThat(published.status()).isEqualTo("BLOCKED");
        assertThat(published.valueText()).isNull();
        assertThat(published.valueExact()).isNull();
        assertThat(published.classification()).isNull();
        assertThat(published.valueKind()).isEqualTo("SCORE");
        assertThat(published.numeratorText()).isEqualTo("125");
        assertThat(published.denominatorText()).isEqualTo("3");
        assertThat(published.denominatorKind()).isEqualTo("PESSOAS_COM_HIPERTENSAO_VINCULADAS");
        assertThat(published.canonicalSchemaVersion()).isEqualTo("2");
        assertThat(published.limitationsJson()).contains("Portão A", "Portão D", "C5-LIM-25");
        assertThat(ResultJson.readComponents(published.componentsJson()))
                .extracting(
                        ResultJson.StoredComponent::code,
                        ResultJson.StoredComponent::weight,
                        ResultJson.StoredComponent::numerator,
                        ResultJson.StoredComponent::denominator,
                        ResultJson.StoredComponent::status)
                .containsExactly(
                        tuple("A", "25", "2", "3", "COMPUTED"),
                        tuple("B", "25", "1", "3", "COMPUTED"),
                        tuple("C", "25", "1", "3", "COMPUTED"),
                        tuple("D", "25", "1", "3", "COMPUTED"));
        assertThat(ResultJson.readTeams(published.teamResultsJson()))
                .extracting(
                        ResultJson.StoredTeam::ine,
                        ResultJson.StoredTeam::cnes,
                        ResultJson.StoredTeam::status,
                        ResultJson.StoredTeam::valueText,
                        ResultJson.StoredTeam::classification)
                .containsExactly(
                        tuple(TEAM_ONE, CNES_ONE, "BLOCKED", null, null),
                        tuple(TEAM_TWO, CNES_TWO, "BLOCKED", null, null));

        List<EvidenceRecord> evidence = fixture.evidence(run.resultId(), IBGE);
        assertThat(evidence)
                .extracting(
                        EvidenceRecord::subjectKey,
                        EvidenceRecord::component,
                        EvidenceRecord::decision,
                        EvidenceRecord::points,
                        EvidenceRecord::reasonCode,
                        EvidenceRecord::ine)
                .containsExactly(
                        tuple("p1", null, "ELIGIBLE", "100", "ELEGIVEL", TEAM_ONE),
                        tuple("p1", "A", "PRACTICE_MET", "25", "CUMPRIDA", TEAM_ONE),
                        tuple("p1", "A", "SUPPORTING_EVENT", null, "MIAI", null),
                        tuple("p1", "B", "PRACTICE_MET", "25", "CUMPRIDA", TEAM_ONE),
                        tuple("p1", "B", "SUPPORTING_EVENT", null, "MIP", null),
                        tuple("p1", "C", "PRACTICE_MET", "25", "CUMPRIDA", TEAM_ONE),
                        tuple("p1", "C", "SUPPORTING_EVENT", null, "MIP", null),
                        tuple("p1", "D", "PRACTICE_MET", "25", "CUMPRIDA", TEAM_ONE),
                        tuple("p1", "D", "SUPPORTING_EVENT", null, "MIVDT", TEAM_ONE),
                        tuple("p1", "D", "SUPPORTING_EVENT", null, "MIVDT", TEAM_ONE),
                        tuple("p2", null, "ELIGIBLE", "25", "ELEGIVEL", TEAM_ONE),
                        tuple("p2", "A", "PRACTICE_MET", "25", "CUMPRIDA", TEAM_ONE),
                        tuple("p2", "A", "SUPPORTING_EVENT", null, "MIAI", null),
                        tuple("p2", "B", "PRACTICE_NOT_MET", "0", "SEM_REGISTRO_NA_JANELA", TEAM_ONE),
                        tuple("p2", "C", "PRACTICE_NOT_MET", "0", "SEM_REGISTRO_NA_JANELA", TEAM_ONE),
                        tuple("p2", "D", "PRACTICE_NOT_MET", "0", "SEM_REGISTRO_NA_JANELA", TEAM_ONE),
                        tuple("p3", null, "EXCLUDED", null, "EXCLUIDO_CONDICOES_RESOLVIDAS", TEAM_TWO),
                        tuple("p4", null, "ELIGIBLE", "0", "ELEGIVEL", TEAM_TWO),
                        tuple("p4", "A", "PRACTICE_NOT_MET", "0", "SEM_REGISTRO_NA_JANELA", TEAM_TWO),
                        tuple("p4", "B", "PRACTICE_NOT_MET", "0", "SEM_REGISTRO_NA_JANELA", TEAM_TWO),
                        tuple("p4", "C", "PRACTICE_NOT_MET", "0", "SEM_REGISTRO_NA_JANELA", TEAM_TWO),
                        tuple("p4", "D", "PRACTICE_NOT_MET", "0", "SEM_REGISTRO_NA_JANELA", TEAM_TWO));
        assertThat(evidence).allSatisfy(row -> {
            assertThat(row.subjectKind()).isEqualTo("PERSON");
            assertThat(row.criterionVersion()).isEqualTo(C5Pack.RULE_VERSION);
        });
        EvidenceRecord consultation = evidence.get(2);
        assertThat(consultation.sourceEntityType()).isEqualTo("tb_fat_atendimento_individual");
        assertThat(consultation.sourceRecordId()).isNotBlank();
        assertThat(consultation.careDate()).isEqualTo("2026-02-10");
        assertThat(consultation.cbo()).isEqualTo(DOCTOR);
    }

    @Test
    void eng19_theSameExtractReplayedTwiceReproducesFingerprintAndResult() throws Exception {
        ExtractionManifest extract = extract("ext-c5-twice");

        PublishedResult first = fixture.published(
                fixture.replay(extract, new C5Pack(), COMPETENCIA, "gestor").resultId(), IBGE);
        PublishedResult second = fixture.published(
                fixture.replay(extract, new C5Pack(), COMPETENCIA, "gestor").resultId(), IBGE);

        assertThat(second.inputFingerprint())
                .isEqualTo(first.inputFingerprint())
                .startsWith("sha256:");
        assertThat(second.status()).isEqualTo(first.status());
        assertThat(second.numeratorText()).isEqualTo(first.numeratorText());
        assertThat(second.denominatorText()).isEqualTo(first.denominatorText());
        assertThat(second.componentsJson()).isEqualTo(first.componentsJson());
        assertThat(second.teamResultsJson()).isEqualTo(first.teamResultsJson());
        assertThat(second.limitationsJson()).isEqualTo(first.limitationsJson());
    }

    private ExtractionManifest extract(String extractionId) throws Exception {
        ExtractFixturesV2.Builder builder = ExtractFixturesV2.forRule(new C5Pack(), COMPETENCIA);
        builder.add(CanonicalFixtures.team(TEAM_ONE, CNES_ONE, "70"));
        builder.add(CanonicalFixtures.team(TEAM_TWO, CNES_TWO, "70"));
        for (String key : List.of("p1", "p2", "p3", "p4")) {
            builder.add(CanonicalFixtures.person(key, LocalDate.of(1960, 5, 10), "FEMININO"));
        }
        builder.add(CanonicalFixtures.registration("p1", LocalDate.of(2025, 9, 1), CNES_ONE, TEAM_ONE))
                .add(CanonicalFixtures.registration("p2", LocalDate.of(2025, 9, 1), CNES_ONE, TEAM_ONE))
                .add(CanonicalFixtures.registration("p3", LocalDate.of(2025, 9, 1), CNES_TWO, TEAM_TWO))
                .add(CanonicalFixtures.registration("p4", LocalDate.of(2025, 9, 1), CNES_TWO, TEAM_TWO))
                .add(condition("p1", "CID10", "I10", LocalDate.of(2019, 5, 10), "0"))
                .add(condition("p2", "CIAP2", "K86", LocalDate.of(2020, 3, 2), "0"))
                .add(condition("p3", "CID10", "I11.0", LocalDate.of(2018, 1, 15), "0"))
                .add(condition("p3", "CID10", "I11.0", LocalDate.of(2024, 6, 1), "2"))
                .add(condition("p4", "CID10", "O10", LocalDate.of(2021, 8, 20), "0"))
                .add(
                        Capabilities.CARE_ENCOUNTER,
                        CanonicalFixtures.encounterWithProblems(
                                "p1", LocalDate.of(2026, 2, 10), DOCTOR, List.of("K86"), List.of()))
                .add(
                        Capabilities.CARE_ENCOUNTER,
                        CanonicalFixtures.encounterWithProblems(
                                "p2", LocalDate.of(2026, 1, 15), DOCTOR, List.of("K86"), List.of()))
                .add(
                        Capabilities.PROCEDURE_PERFORMED,
                        CanonicalFixtures.procedure(
                                "p1", LocalDate.of(2026, 3, 5), "0301100039", "PERFORMED", NURSING_TECHNICIAN))
                .add(
                        Capabilities.PROCEDURE_PERFORMED,
                        CanonicalFixtures.procedure(
                                "p1", LocalDate.of(2025, 11, 10), "0101040024", "PERFORMED", NUTRITIONIST))
                .add(visit("p1", LocalDate.of(2025, 12, 1)))
                .add(visit("p1", LocalDate.of(2026, 2, 1)));
        return builder.write(fixture.extractsDir, extractionId, "src-1");
    }

    private static CanonicalCondition condition(String key, String system, String code, LocalDate date, String status) {
        return CanonicalFixtures.conditionEvaluatedBy(key, system, code, date, status, DOCTOR);
    }

    /** An ACS home visit with a reason filled in (item 24 e: «preenchimento do ‘‘motivo da visita’’»). */
    private static CanonicalHomeVisit visit(String key, LocalDate date) {
        return new CanonicalHomeVisit(
                CanonicalFixtures.ref("tb_fat_visita_domiciliar"),
                IBGE,
                key,
                date.toString(),
                ACS,
                CNES_ONE,
                TEAM_ONE,
                "1",
                List.of("ACOMP_PESSOA_HIPERTENSAO"),
                null,
                null);
    }
}
