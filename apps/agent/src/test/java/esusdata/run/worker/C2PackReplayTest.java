package esusdata.run.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.pack.c2.C2Codes;
import esusdata.indicator.pack.c2.C2Pack;
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
 * ENG-19 for C2: a synthetic canonical v2 extract goes through {@code RunExecutor.runFromExtract}
 * and publishes {@code BLOCKED} (gates incomplete) with the exact counts, practices, teams and
 * evidence; replaying the same extract gives the same input fingerprint. No child completes two
 * years in 2026-03, so the month is outside AMB-C2-03 and the result is not {@code RULE_AMBIGUITY}.
 *
 * <p>p1 (team one, born 2025-12-01): first presential consultation on day 14 (A) and visits on
 * days 9 and 71 (D) — 40 points; B, C and E still open. p2 (team two, born 2025-06-10): nothing —
 * A and D closed unmet, B, C and E open. p3: no registration, excluded.
 */
class C2PackReplayTest {

    private static final YearMonth COMPETENCIA = YearMonth.of(2026, 3);
    private static final String IBGE = CanonicalFixtures.IBGE;
    private static final String TEAM_ONE = "0000346268";
    private static final String TEAM_TWO = "0000346276";
    private static final String CNES_ONE = "2750325";
    private static final String CNES_TWO = "2750333";
    private static final LocalDate P1_BIRTH = LocalDate.of(2025, 12, 1);
    private static final LocalDate P2_BIRTH = LocalDate.of(2025, 6, 10);
    private static final String OPEN = "NAO_CUMPRIDA_PRAZO_ABERTO";

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

    private static CanonicalHomeVisit visit(String key, LocalDate date) {
        return new CanonicalHomeVisit(
                CanonicalFixtures.ref("tb_fat_visita_domiciliar"),
                IBGE,
                key,
                date.toString(),
                "515105",
                CNES_ONE,
                TEAM_ONE,
                C2Codes.VISIT_OUTCOME_DONE,
                List.of(C2Codes.VISIT_REASON_NEWBORN),
                null,
                null);
    }

    private ExtractionManifest extract(String id) throws Exception {
        return ExtractFixturesV2.forRule(new C2Pack(), COMPETENCIA)
                .add(CanonicalFixtures.person("p1", P1_BIRTH, "FEMININO"))
                .add(CanonicalFixtures.registration("p1", P1_BIRTH.plusDays(3), CNES_ONE, TEAM_ONE))
                .add(
                        Capabilities.CARE_ENCOUNTER,
                        CanonicalFixtures.encounter("p1", P1_BIRTH.plusDays(14), "225142", false))
                .add(visit("p1", P1_BIRTH.plusDays(9)))
                .add(visit("p1", P1_BIRTH.plusDays(71)))
                .add(CanonicalFixtures.person("p2", P2_BIRTH, "MASCULINO"))
                .add(CanonicalFixtures.registration("p2", P2_BIRTH.plusDays(5), CNES_TWO, TEAM_TWO))
                .add(CanonicalFixtures.person("p3", LocalDate.of(2025, 1, 20), "FEMININO"))
                .write(fixture.extractsDir, id, "src-1");
    }

    @Test
    void eng19_c2PublicaBlockedComContagensPraticasEquipesEEvidenciaExatas() throws Exception {
        RunExecutor.RunOutcome run = fixture.replay(extract("ext-c2-2026-03"), new C2Pack(), COMPETENCIA, "gestor");

        PublishedResult published = fixture.published(run.resultId(), IBGE);
        assertThat(published.status()).isEqualTo("BLOCKED");
        assertThat(published.valueText()).isNull();
        assertThat(published.classification()).isNull();
        assertThat(published.valueKind()).isEqualTo("SCORE");
        assertThat(published.numeratorText()).isEqualTo("40");
        assertThat(published.denominatorText()).isEqualTo("2");
        assertThat(published.consolidationEligible()).isFalse();
        assertThat(ResultJson.readComponents(published.componentsJson()))
                .extracting(
                        ResultJson.StoredComponent::code,
                        ResultJson.StoredComponent::numerator,
                        ResultJson.StoredComponent::denominator,
                        ResultJson.StoredComponent::status)
                .containsExactly(
                        tuple("A", "1", "2", "COMPUTED"),
                        tuple("B", "0", "2", "COMPUTED"),
                        tuple("C", "0", "2", "COMPUTED"),
                        tuple("D", "1", "2", "COMPUTED"),
                        tuple("E", "0", "2", "COMPUTED"));
        assertThat(ResultJson.readTeams(published.teamResultsJson()))
                .extracting(ResultJson.StoredTeam::ine, ResultJson.StoredTeam::cnes, ResultJson.StoredTeam::status)
                .containsExactly(tuple(TEAM_ONE, CNES_ONE, "BLOCKED"), tuple(TEAM_TWO, CNES_TWO, "BLOCKED"));

        List<EvidenceRecord> evidence = fixture.evidence(run.resultId(), IBGE);
        assertThat(evidence)
                .extracting(
                        EvidenceRecord::subjectKey,
                        EvidenceRecord::component,
                        EvidenceRecord::decision,
                        EvidenceRecord::reasonCode,
                        EvidenceRecord::points)
                .containsExactly(
                        tuple("p1", null, "ELIGIBLE", "COORTE_ATE_2_ANOS", "40"),
                        tuple("p1", "A", "PRACTICE_MET", "CUMPRIDA", "20"),
                        tuple("p1", "A", "SUPPORTING_EVENT", null, null),
                        tuple("p1", "B", "PRACTICE_NOT_MET", OPEN, "0"),
                        tuple("p1", "C", "PRACTICE_NOT_MET", OPEN, "0"),
                        tuple("p1", "D", "PRACTICE_MET", "CUMPRIDA", "20"),
                        tuple("p1", "D", "SUPPORTING_EVENT", null, null),
                        tuple("p1", "D", "SUPPORTING_EVENT", null, null),
                        tuple("p1", "E", "PRACTICE_NOT_MET", OPEN, "0"),
                        tuple("p2", null, "ELIGIBLE", "COORTE_ATE_2_ANOS", "0"),
                        tuple("p2", "A", "PRACTICE_NOT_MET", "NAO_CUMPRIDA", "0"),
                        tuple("p2", "B", "PRACTICE_NOT_MET", OPEN, "0"),
                        tuple("p2", "C", "PRACTICE_NOT_MET", OPEN, "0"),
                        tuple("p2", "D", "PRACTICE_NOT_MET", "NAO_CUMPRIDA", "0"),
                        tuple("p2", "E", "PRACTICE_NOT_MET", OPEN, "0"),
                        tuple("p3", null, "EXCLUDED", "EXCLUIDO_SEM_VINCULO", null));
        assertThat(evidence.get(2).careDate()).isEqualTo("2025-12-15");
        assertThat(evidence.get(2).cbo()).isEqualTo("225142");
        assertThat(evidence).allSatisfy(row -> assertThat(row.subjectKind()).isEqualTo("PERSON"));
    }

    @Test
    void eng19_oMesmoExtratoReexecutadoTemOMesmoFingerprint() throws Exception {
        ExtractionManifest extract = extract("ext-c2-twice");
        String first = fixture.published(
                        fixture.replay(extract, new C2Pack(), COMPETENCIA, "gestor")
                                .resultId(),
                        IBGE)
                .inputFingerprint();
        String second = fixture.published(
                        fixture.replay(extract, new C2Pack(), COMPETENCIA, "gestor")
                                .resultId(),
                        IBGE)
                .inputFingerprint();
        assertThat(second).isEqualTo(first).startsWith("sha256:");
    }
}
