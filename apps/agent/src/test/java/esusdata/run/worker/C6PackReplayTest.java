package esusdata.run.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.pack.c6.C6Pack;
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
 * ENG-19 for C6: a synthetic v2 extract of the ficha's case goes through {@code
 * RunExecutor.runFromExtract} and publishes {@code BLOCKED} (gates incomplete) with the exact counts,
 * practices, team and evidence; replaying the same extract gives the same input fingerprint.
 */
class C6PackReplayTest {

    private static final YearMonth COMPETENCIA = YearMonth.of(2026, 3);
    private static final String IBGE = CanonicalFixtures.IBGE;
    private static final String CNES = "2750325";
    private static final String INE = "0000346268";
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
    void eng19_replaysTheFichasCaseAsBlockedWithExactCountsTeamsAndEvidence() throws Exception {
        ExtractionManifest extract = extract("ext-c6-2026-03");

        RunExecutor.RunOutcome run = fixture.replay(extract, new C6Pack(), COMPETENCIA, "gestor");

        PublishedResult published = fixture.published(run.resultId(), IBGE);
        assertThat(published.status()).isEqualTo("BLOCKED");
        assertThat(published.valueText()).isNull();
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
                        tuple("A", "25", "1", "2", "COMPUTED"),
                        tuple("B", "25", "1", "2", "COMPUTED"),
                        tuple("C", "25", "1", "2", "COMPUTED"),
                        tuple("D", "25", "1", "2", "COMPUTED"));
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
                        EvidenceRecord::reasonCode,
                        EvidenceRecord::points)
                .containsExactly(
                        tuple("p1", null, "ELIGIBLE", "ELEGIVEL_60_ANOS_VINCULADO", "50"),
                        tuple("p1", "A", "PRACTICE_MET", "A_CONSULTA_MEDICA_ENFERMAGEM", "25"),
                        tuple("p1", "A", "SUPPORTING_EVENT", "A_CONSULTA_MEDICA_ENFERMAGEM", null),
                        tuple("p1", "B", "PRACTICE_NOT_MET", "B_SEM_PESO_ALTURA_MESMO_DIA", "0"),
                        tuple("p1", "C", "PRACTICE_NOT_MET", "C_SEM_DUAS_VISITAS_30_DIAS", "0"),
                        tuple("p1", "D", "PRACTICE_MET", "D_DOSE_INFLUENZA", "25"),
                        tuple("p1", "D", "SUPPORTING_EVENT", "D_DOSE_INFLUENZA", null),
                        tuple("p2", null, "ELIGIBLE", "ELEGIVEL_60_ANOS_VINCULADO", "50"),
                        tuple("p2", "A", "PRACTICE_NOT_MET", "A_SEM_CONSULTA", "0"),
                        tuple("p2", "B", "PRACTICE_MET", "B_PESO_ALTURA_MESMO_DIA", "25"),
                        tuple("p2", "B", "SUPPORTING_EVENT", "B_PESO_ALTURA_MESMO_DIA", null),
                        tuple("p2", "C", "PRACTICE_MET", "C_DUAS_VISITAS_30_DIAS", "25"),
                        tuple("p2", "C", "SUPPORTING_EVENT", "C_DUAS_VISITAS_30_DIAS", null),
                        tuple("p2", "C", "SUPPORTING_EVENT", "C_DUAS_VISITAS_30_DIAS", null),
                        tuple("p2", "D", "PRACTICE_NOT_MET", "D_SEM_DOSE_INFLUENZA_NO_PEC_LOCAL", "0"),
                        tuple("p3", null, "EXCLUDED", "INTERROMPIDO_MUDANCA_TERRITORIO", null));
        assertThat(evidence).allSatisfy(row -> assertThat(row.subjectKind()).isEqualTo("PERSON"));
    }

    @Test
    void eng19_theSameExtractReplayedTwiceHasTheSameInputFingerprint() throws Exception {
        ExtractionManifest extract = extract("ext-c6-twice");

        String first = fixture.published(
                        fixture.replay(extract, new C6Pack(), COMPETENCIA, "gestor")
                                .resultId(),
                        IBGE)
                .inputFingerprint();
        String second = fixture.published(
                        fixture.replay(extract, new C6Pack(), COMPETENCIA, "gestor")
                                .resultId(),
                        IBGE)
                .inputFingerprint();

        assertThat(second).isEqualTo(first).startsWith("sha256:");
    }

    /**
     * p1: consultation (A) and one influenza dose recorded twice (D, MET-32) — 50. p2: two ACS visits
     * 106 days apart, the second with weight and height (B and C) — 50. p3: change of territory.
     */
    private ExtractionManifest extract(String extractId) throws Exception {
        LocalDate linked = LocalDate.of(2025, 9, 1);
        LocalDate dose = LocalDate.of(2025, 5, 10);
        return ExtractFixturesV2.forRule(new C6Pack(), COMPETENCIA)
                .add(CanonicalFixtures.team(INE, CNES, "70"))
                .add(CanonicalFixtures.person("p1", LocalDate.of(1956, 3, 10), "FEMININO"))
                .add(CanonicalFixtures.registration("p1", linked, CNES, INE))
                .add(CanonicalFixtures.encounter("p1", LocalDate.of(2026, 2, 10), "225142", false))
                .add(CanonicalFixtures.dose("p1", dose, "33", "1"))
                .add(CanonicalFixtures.dose("p1", dose, "33", "1"))
                .add(CanonicalFixtures.person("p2", LocalDate.of(1950, 1, 1), "MASCULINO"))
                .add(CanonicalFixtures.registration("p2", linked, CNES, INE))
                .add(visit("p2", LocalDate.of(2025, 10, 1), null, null))
                .add(visit("p2", LocalDate.of(2026, 1, 15), "70.5", "165.0"))
                .add(CanonicalFixtures.person("p3", LocalDate.of(1940, 6, 1), "FEMININO"))
                .add(new CanonicalRegistration(
                        CanonicalFixtures.ref("tb_fat_cad_individual"),
                        IBGE,
                        "p3",
                        linked.toString(),
                        CNES,
                        INE,
                        false,
                        false,
                        false,
                        "136",
                        null,
                        null,
                        null))
                .write(fixture.extractsDir, extractId, "src-1");
    }

    private static CanonicalHomeVisit visit(String key, LocalDate date, String weightKg, String heightCm) {
        return new CanonicalHomeVisit(
                CanonicalFixtures.ref("tb_fat_visita_domiciliar"),
                IBGE,
                key,
                date.toString(),
                ACS,
                CNES,
                INE,
                "1",
                List.of("ACOMP_PESSOA_IDOSA"),
                weightKg,
                heightCm);
    }
}
