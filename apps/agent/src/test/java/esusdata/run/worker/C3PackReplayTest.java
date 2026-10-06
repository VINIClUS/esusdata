package esusdata.run.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.pack.c3.C3Pack;
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
 * ENG-19 for C3: a synthetic canonical v2 extract of the ficha's case replays through {@code
 * RunExecutor.runFromExtract} and publishes {@code BLOCKED} (gates incomplete) with the exact counts,
 * practices, team and evidence, and replaying the same extract gives the same input fingerprint.
 *
 * <p>Case (competência 2025-11): {@code p1}, linked to team {@value #INE}, DUM 2025-01-01 and no
 * recorded outcome, so the pregnancy ends on the substitute DUM+294 = 2025-10-22 (MET-21). She has
 * A (first consultation in week 7), F (dTpa in week 21), K (dental encounter), I (puerperal
 * consultation on D+19) and J (ACS visit on D+21): 10 + 4 × 9 = 46 points. {@code p2} has a
 * pregnancy but no registration: excluded as {@code EXCLUIDO_SEM_VINCULO}, still in the evidence (ENG-36).
 */
class C3PackReplayTest {

    private static final YearMonth COMPETENCIA = YearMonth.of(2025, 11);
    private static final String IBGE = CanonicalFixtures.IBGE;
    private static final String INE = "0000346268";
    private static final String CNES = "2750325";
    private static final String DOCTOR = "225142";

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
    void eng19_theFichaCaseReplaysToBlockedWithExactCountsPracticesTeamAndEvidence() throws Exception {
        ExtractionManifest extract = extract("ext-c3-2025-11");

        RunExecutor.RunOutcome run = fixture.replay(extract, new C3Pack(), COMPETENCIA, "gestor");

        PublishedResult published = fixture.published(run.resultId(), IBGE);
        assertThat(published.status()).isEqualTo("BLOCKED");
        assertThat(published.valueText()).isNull();
        assertThat(published.classification()).isNull();
        assertThat(published.numeratorText()).isEqualTo("46");
        assertThat(published.denominatorText()).isEqualTo("1");
        assertThat(published.valueKind()).isEqualTo("SCORE");
        // D+42 = 2025-12-03: the 42nd day of puerperium is not in November (NT 8/2026).
        assertThat(published.consolidationEligible()).isFalse();
        assertThat(ResultJson.readComponents(published.componentsJson()))
                .extracting(
                        ResultJson.StoredComponent::code,
                        ResultJson.StoredComponent::weight,
                        ResultJson.StoredComponent::numerator,
                        ResultJson.StoredComponent::denominator,
                        ResultJson.StoredComponent::status)
                .containsExactly(
                        tuple("A", "10", "1", "1", "COMPUTED"),
                        tuple("B", "9", "0", "1", "COMPUTED"),
                        tuple("C", "9", "0", "1", "COMPUTED"),
                        tuple("D", "9", "0", "1", "COMPUTED"),
                        tuple("E", "9", "0", "1", "COMPUTED"),
                        tuple("F", "9", "1", "1", "COMPUTED"),
                        tuple("G", "9", "0", "1", "COMPUTED"),
                        tuple("H", "9", "0", "1", "COMPUTED"),
                        tuple("I", "9", "1", "1", "COMPUTED"),
                        tuple("J", "9", "1", "1", "COMPUTED"),
                        tuple("K", "9", "1", "1", "COMPUTED"));
        assertThat(ResultJson.readTeams(published.teamResultsJson()))
                .extracting(
                        ResultJson.StoredTeam::ine,
                        ResultJson.StoredTeam::cnes,
                        ResultJson.StoredTeam::status,
                        ResultJson.StoredTeam::valueText)
                .containsExactly(tuple(INE, CNES, "BLOCKED", null));

        List<EvidenceRecord> evidence = fixture.evidence(run.resultId(), IBGE);
        assertThat(evidence)
                .filteredOn(row -> row.component() == null)
                .extracting(
                        EvidenceRecord::subjectKey,
                        EvidenceRecord::decision,
                        EvidenceRecord::reasonCode,
                        EvidenceRecord::points,
                        EvidenceRecord::careDate)
                .containsExactly(
                        tuple("p1#2025-01-01", "ELIGIBLE", "ELEGIVEL_DATA_SUBSTITUTIVA_294D", "46", "2025-10-22"),
                        tuple("p1#2025-01-01", "SUPPORTING_EVENT", "MARCO_DUM", null, "2025-01-01"),
                        tuple("p2#2025-01-01", "EXCLUDED", "EXCLUIDO_SEM_VINCULO", null, "2025-10-22"));
        assertThat(evidence)
                .filteredOn(row -> row.component() != null && !"SUPPORTING_EVENT".equals(row.decision()))
                .extracting(EvidenceRecord::component, EvidenceRecord::decision, EvidenceRecord::points)
                .containsExactly(
                        tuple("A", "PRACTICE_MET", "10"),
                        tuple("B", "PRACTICE_NOT_MET", "0"),
                        tuple("C", "PRACTICE_NOT_MET", "0"),
                        tuple("D", "PRACTICE_NOT_MET", "0"),
                        tuple("E", "PRACTICE_NOT_MET", "0"),
                        tuple("F", "PRACTICE_MET", "9"),
                        tuple("G", "PRACTICE_NOT_MET", "0"),
                        tuple("H", "PRACTICE_NOT_MET", "0"),
                        tuple("I", "PRACTICE_MET", "9"),
                        tuple("J", "PRACTICE_MET", "9"),
                        tuple("K", "PRACTICE_MET", "9"));
        assertThat(evidence)
                .filteredOn(row -> row.component() != null && "SUPPORTING_EVENT".equals(row.decision()))
                .extracting(EvidenceRecord::component, EvidenceRecord::careDate, EvidenceRecord::cbo)
                .containsExactly(
                        tuple("A", "2025-02-20", DOCTOR),
                        tuple("F", "2025-05-30", DOCTOR),
                        tuple("I", "2025-11-10", DOCTOR),
                        tuple("J", "2025-11-12", "515105"),
                        tuple("K", "2025-04-10", "223293"));
        assertThat(evidence).allSatisfy(row -> assertThat(row.subjectKind()).isEqualTo("EPISODE"));
    }

    @Test
    void eng19_theSameExtractReplayedTwiceHasTheSameInputFingerprint() throws Exception {
        ExtractionManifest extract = extract("ext-c3-twice");

        String first = fixture.published(
                        fixture.replay(extract, new C3Pack(), COMPETENCIA, "gestor")
                                .resultId(),
                        IBGE)
                .inputFingerprint();
        String second = fixture.published(
                        fixture.replay(extract, new C3Pack(), COMPETENCIA, "gestor")
                                .resultId(),
                        IBGE)
                .inputFingerprint();

        assertThat(second).isEqualTo(first).startsWith("sha256:");
    }

    private ExtractionManifest extract(String extractionId) throws Exception {
        return ExtractFixturesV2.forRule(new C3Pack(), COMPETENCIA)
                .add(CanonicalFixtures.team(INE, CNES, "70"))
                .add(CanonicalFixtures.person("p1", LocalDate.of(1995, 3, 10), "FEMININO"))
                .add(CanonicalFixtures.person("p2", LocalDate.of(1998, 7, 2), "FEMININO"))
                .add(CanonicalFixtures.registration("p1", LocalDate.of(2025, 1, 15), CNES, INE))
                .add(Capabilities.CARE_ENCOUNTER, care("p1", "2025-02-20", "INDIVIDUAL", DOCTOR, "W78", "2025-01-01"))
                .add(Capabilities.CARE_ENCOUNTER, care("p2", "2025-02-21", "INDIVIDUAL", DOCTOR, "W78", "2025-01-01"))
                .add(Capabilities.CARE_ENCOUNTER, care("p1", "2025-11-10", "INDIVIDUAL", DOCTOR, "W90", null))
                .add(Capabilities.DENTAL_ENCOUNTER, care("p1", "2025-04-10", "DENTAL", "223293", null, null))
                .add(dtpa("p1", "2025-05-30"))
                .add(acsVisit("p1", "2025-11-12"))
                .write(fixture.extractsDir, extractionId, "src-1");
    }

    private static CanonicalCareEvent care(String key, String date, String form, String cbo, String ciap, String lmp) {
        return new CanonicalCareEvent(
                CanonicalFixtures.ref("tb_fat_atendimento_individual"),
                IBGE,
                key,
                date,
                form,
                cbo,
                CNES,
                INE,
                null,
                null,
                false,
                ciap == null ? List.of() : List.of(ciap),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                lmp,
                null,
                null,
                null);
    }

    private static CanonicalImmunization dtpa(String key, String date) {
        return new CanonicalImmunization(
                CanonicalFixtures.ref("tb_fat_vacinacao_vacina"),
                IBGE,
                key,
                date,
                "57",
                "1",
                null,
                false,
                DOCTOR,
                CNES,
                INE,
                date);
    }

    private static CanonicalHomeVisit acsVisit(String key, String date) {
        return new CanonicalHomeVisit(
                CanonicalFixtures.ref("tb_fat_visita_domiciliar"),
                IBGE,
                key,
                date,
                "515105",
                CNES,
                INE,
                "1",
                List.of("ACOMP_PUERPERA"),
                null,
                null);
    }
}
