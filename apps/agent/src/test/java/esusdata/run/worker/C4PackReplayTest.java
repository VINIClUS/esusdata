package esusdata.run.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.pack.c4.C4Pack;
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
 * ENG-19 for C4 (ADR 0030): a synthetic canonical v2 extract of the ficha's case goes through
 * {@code RunExecutor.runFromExtract} and publishes {@code BLOCKED} (gates incomplete) with the exact
 * counts, practices, team and evidence — including the excluded candidate (ENG-36) — and replaying
 * the same extract reproduces the same input fingerprint.
 */
class C4PackReplayTest {

    private static final YearMonth COMPETENCIA = YearMonth.of(2026, 3);
    private static final String IBGE = CanonicalFixtures.IBGE;
    private static final String CNES = "2750325";
    private static final String INE = "0000346268";
    private static final String MEDICO = "225142";
    private static final String ENFERMEIRO = "223505";
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

    /**
     * p1 meets every practice (100 points), p2 none (0), p3 only self-reports diabetes and is left
     * out (T-C4-27): score 100/2, every practice 1/2, one team.
     */
    private ExtractionManifest extract(String id) throws Exception {
        return ExtractFixturesV2.forRule(new C4Pack(), COMPETENCIA)
                .add(CanonicalFixtures.team(INE, CNES, "70"))
                .add(CanonicalFixtures.registration("p1", LocalDate.of(2025, 1, 10), CNES, INE))
                .add(CanonicalFixtures.conditionEvaluatedBy(
                        "p1", "CID10", "E11", LocalDate.of(2020, 5, 10), "0", MEDICO))
                .add(Capabilities.CARE_ENCOUNTER, consultWithMeasures("p1", LocalDate.of(2026, 2, 10)))
                .add(visit("p1", LocalDate.of(2025, 10, 1)))
                .add(visit("p1", LocalDate.of(2026, 1, 10)))
                .add(
                        Capabilities.EXAM_REQUEST_EVALUATION,
                        CanonicalFixtures.procedure("p1", LocalDate.of(2025, 11, 5), "0202010503", "REQUESTED", MEDICO))
                .add(
                        Capabilities.PROCEDURE_PERFORMED,
                        CanonicalFixtures.procedure(
                                "p1", LocalDate.of(2025, 12, 1), "0301040095", "PERFORMED", ENFERMEIRO))
                .add(CanonicalFixtures.registration("p2", LocalDate.of(2025, 2, 3), CNES, INE))
                .add(CanonicalFixtures.conditionEvaluatedBy(
                        "p2", "CIAP2", "T90", LocalDate.of(2018, 3, 2), "0", ENFERMEIRO))
                .add(CanonicalFixtures.registration("p3", LocalDate.of(2025, 3, 4), CNES, INE))
                .add(selfReported("p3", LocalDate.of(2024, 6, 1)))
                .write(fixture.extractsDir, id, "src-1");
    }

    @Test
    void eng19_c4ReplaysASyntheticExtractAndPublishesBlockedWithExactCounts() throws Exception {
        RunExecutor.RunOutcome run = fixture.replay(extract("ext-c4-2026-03"), new C4Pack(), COMPETENCIA, "gestor");

        PublishedResult published = fixture.published(run.resultId(), IBGE);
        assertThat(published.status()).isEqualTo("BLOCKED");
        assertThat(published.valueText()).isNull();
        assertThat(published.valueExact()).isNull();
        assertThat(published.classification()).isNull();
        assertThat(published.valueKind()).isEqualTo("SCORE");
        assertThat(published.numeratorText()).isEqualTo("100");
        assertThat(published.denominatorText()).isEqualTo("2");
        assertThat(ResultJson.readComponents(published.componentsJson()))
                .extracting(
                        ResultJson.StoredComponent::code,
                        ResultJson.StoredComponent::weight,
                        ResultJson.StoredComponent::numerator,
                        ResultJson.StoredComponent::denominator,
                        ResultJson.StoredComponent::status)
                .containsExactly(
                        tuple("A", "20", "1", "2", "COMPUTED"),
                        tuple("B", "15", "1", "2", "COMPUTED"),
                        tuple("C", "15", "1", "2", "COMPUTED"),
                        tuple("D", "20", "1", "2", "COMPUTED"),
                        tuple("E", "15", "1", "2", "COMPUTED"),
                        tuple("F", "15", "1", "2", "COMPUTED"));
        assertThat(ResultJson.readTeams(published.teamResultsJson()))
                .extracting(
                        ResultJson.StoredTeam::ine,
                        ResultJson.StoredTeam::cnes,
                        ResultJson.StoredTeam::status,
                        ResultJson.StoredTeam::valueText)
                .containsExactly(tuple(INE, CNES, "BLOCKED", null));

        List<EvidenceRecord> evidence = fixture.evidence(run.resultId(), IBGE);
        assertThat(evidence)
                .extracting(
                        EvidenceRecord::subjectKey,
                        EvidenceRecord::component,
                        EvidenceRecord::decision,
                        EvidenceRecord::points,
                        EvidenceRecord::reasonCode)
                .containsExactly(
                        tuple("p1", null, "ELIGIBLE", "100", "ELEGIVEL"),
                        tuple("p1", "A", "PRACTICE_MET", "20", "PRATICA_CUMPRIDA"),
                        tuple("p1", "B", "PRACTICE_MET", "15", "PRATICA_CUMPRIDA"),
                        tuple("p1", "C", "PRACTICE_MET", "15", "PRATICA_CUMPRIDA"),
                        tuple("p1", "D", "PRACTICE_MET", "20", "PRATICA_CUMPRIDA"),
                        tuple("p1", "E", "PRACTICE_MET", "15", "PRATICA_CUMPRIDA"),
                        tuple("p1", "F", "PRACTICE_MET", "15", "PRATICA_CUMPRIDA"),
                        tuple("p1", "A", "SUPPORTING_EVENT", null, null),
                        tuple("p1", "B", "SUPPORTING_EVENT", null, null),
                        tuple("p1", "C", "SUPPORTING_EVENT", null, null),
                        tuple("p1", "D", "SUPPORTING_EVENT", null, null),
                        tuple("p1", "D", "SUPPORTING_EVENT", null, null),
                        tuple("p1", "E", "SUPPORTING_EVENT", null, null),
                        tuple("p1", "F", "SUPPORTING_EVENT", null, null),
                        tuple("p2", null, "ELIGIBLE", "0", "ELEGIVEL"),
                        tuple("p2", "A", "PRACTICE_NOT_MET", "0", "PRATICA_NAO_CUMPRIDA"),
                        tuple("p2", "B", "PRACTICE_NOT_MET", "0", "PRATICA_NAO_CUMPRIDA"),
                        tuple("p2", "C", "PRACTICE_NOT_MET", "0", "PRATICA_NAO_CUMPRIDA"),
                        tuple("p2", "D", "PRACTICE_NOT_MET", "0", "PRATICA_NAO_CUMPRIDA"),
                        tuple("p2", "E", "PRACTICE_NOT_MET", "0", "PRATICA_NAO_CUMPRIDA"),
                        tuple("p2", "F", "PRACTICE_NOT_MET", "0", "PRATICA_NAO_CUMPRIDA"),
                        tuple("p3", null, "EXCLUDED", null, "SEM_CONDICAO_AVALIADA"));
        assertThat(evidence).allSatisfy(row -> assertThat(row.subjectKind()).isEqualTo("PERSON"));
        assertThat(evidence)
                .filteredOn(row -> "SUPPORTING_EVENT".equals(row.decision()))
                .extracting(EvidenceRecord::careDate, EvidenceRecord::cbo)
                .containsExactly(
                        tuple("2026-02-10", MEDICO),
                        tuple("2026-02-10", MEDICO),
                        tuple("2026-02-10", MEDICO),
                        tuple("2025-10-01", ACS),
                        tuple("2026-01-10", ACS),
                        tuple("2025-11-05", MEDICO),
                        tuple("2025-12-01", ENFERMEIRO));
    }

    @Test
    void eng19_replayingTheSameC4ExtractGivesTheSameInputFingerprint() throws Exception {
        ExtractionManifest extract = extract("ext-c4-twice");

        String first = fixture.published(
                        fixture.replay(extract, new C4Pack(), COMPETENCIA, "gestor")
                                .resultId(),
                        IBGE)
                .inputFingerprint();
        String second = fixture.published(
                        fixture.replay(extract, new C4Pack(), COMPETENCIA, "gestor")
                                .resultId(),
                        IBGE)
                .inputFingerprint();

        assertThat(second).isEqualTo(first).startsWith("sha256:");
    }

    /** A MIAI by a physician with T90 evaluated, blood pressure, weight and height. */
    private static CanonicalCareEvent consultWithMeasures(String key, LocalDate date) {
        return new CanonicalCareEvent(
                CanonicalFixtures.ref("tb_fat_atendimento_individual"),
                IBGE,
                key,
                date.toString(),
                "INDIVIDUAL",
                MEDICO,
                CNES,
                INE,
                null,
                null,
                false,
                List.of("T90"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "82.5",
                "168",
                "130",
                "80",
                null,
                null,
                null,
                null);
    }

    /** A home visit by an ACS with a «motivo da visita». */
    private static CanonicalHomeVisit visit(String key, LocalDate date) {
        return new CanonicalHomeVisit(
                CanonicalFixtures.ref("tb_fat_visita_domiciliar"),
                IBGE,
                key,
                date.toString(),
                ACS,
                CNES,
                INE,
                "1",
                List.of("ACOMP_CONDICAO"),
                null,
                null);
    }

    private static CanonicalCondition selfReported(String key, LocalDate date) {
        return new CanonicalCondition(
                CanonicalFixtures.ref("tb_fat_cad_individual"),
                IBGE,
                key,
                "CID10",
                "E11",
                date.toString(),
                null,
                null,
                "SELF_REPORTED",
                null);
    }
}
