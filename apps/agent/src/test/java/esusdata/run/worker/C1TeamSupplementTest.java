package esusdata.run.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.pack.c1.C1Pack;
import esusdata.result.model.EvidenceRecord;
import esusdata.run.extract.ExtractFixtures;
import esusdata.run.extract.ExtractionManifest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneOffset;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * C1 reads the team states as an extract of their own beside its v1 one (ADR 0033), and a run is
 * replayed from the pair: the INE filter acts on what that extract says, and a run whose pair is
 * incomplete or is not the plan's fails instead of publishing a result without the filter.
 */
class C1TeamSupplementTest {

    private static final String IBGE = "3541307";
    private static final YearMonth MARCH = YearMonth.of(2026, 3);
    private static final String INE = "0000346268";

    @TempDir
    Path dataDir;

    private JobRunnerTestFixture fixture;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);
        fixture = new JobRunnerTestFixture(dataDir, clock);
        fixture.registerSource("src-1", IBGE);
        fixture.registerPrincipal("gestor", IBGE);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private ExtractionManifest encounters(String id) throws IOException {
        return ExtractFixtures.write(fixture.extractsDir, id, "src-1", IBGE, "2026-03", 7, 3, 2);
    }

    @Test
    void aTeamOfType76KeepsEveryEncounterAndTheCountsStand() throws Exception {
        ExtractFixtures.writeTeamsOfType(fixture.extractsDir, "ext-76-team", "src-1", IBGE, MARCH, "76", INE);
        ExtractionManifest extract = encounters("ext-76");

        IndicatorResult result =
                fixture.replay(extract, new C1Pack(), MARCH, "gestor").result();

        assertThat(result.numerator().intValue()).isEqualTo(7);
        assertThat(result.denominator().intValue()).isEqualTo(10);
        assertThat(result.limitations()).noneMatch(l -> l.startsWith("C1-LIM-10/contagem"));
    }

    @Test
    void aTeamOfAnotherTypeLeavesItsEncountersOutWithTheReasonAndTheCount() throws Exception {
        ExtractFixtures.writeTeamsOfType(fixture.extractsDir, "ext-72-team", "src-1", IBGE, MARCH, "72", INE);
        ExtractionManifest extract = encounters("ext-72");

        var run = fixture.replay(extract, new C1Pack(), MARCH, "gestor");

        IndicatorResult result = run.result();
        // a team type outside 70/76 leaves all 12 encounters (7 + 3 + 2 unmapped) out of both counts
        assertThat(result.numerator().intValue()).isZero();
        assertThat(result.denominator().intValue()).isZero();
        assertThat(result.limitations())
                .anyMatch(l -> l.startsWith("C1-LIM-10/contagem: 12 atendimento(s)") && l.contains("12 de outro tipo"));
        assertThat(fixture.evidence(run.resultId(), IBGE))
                .hasSize(12)
                .extracting(EvidenceRecord::reasonCode)
                .containsOnly("EXCLUIDO_EQUIPE_FORA_DO_ESCOPO");
    }

    @Test
    void aRunWhoseTeamExtractIsMissingFailsInsteadOfPublishingWithoutTheFilter() throws Exception {
        ExtractionManifest extract = encounters("ext-lonely");
        Files.delete(fixture.extractsDir.resolve("ext-lonely-team.manifest.json"));

        assertThatThrownBy(() -> fixture.replay(extract, new C1Pack(), MARCH, "gestor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ext-lonely-team");
        assertThat(fixture.jdbc.queryForObject("select count(*) from results", Integer.class))
                .isZero();
    }

    @Test
    void aTeamExtractOfAnotherPeriodIsNotThePlans() throws Exception {
        ExtractFixtures.writeTeams(fixture.extractsDir, "ext-feb-team", "src-1", IBGE, YearMonth.of(2026, 2), INE);
        ExtractionManifest extract = encounters("ext-feb");

        assertThatThrownBy(() -> fixture.replay(extract, new C1Pack(), MARCH, "gestor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not the supplementary extract");
        assertThat(fixture.jdbc.queryForObject("select count(*) from results", Integer.class))
                .isZero();
    }
}
