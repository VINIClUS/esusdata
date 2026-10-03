package esusdata.run.worker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.RuleOutcomes;
import esusdata.indicator.pack.c1.C1Pack;
import esusdata.indicator.pack.c2.C2Pack;
import esusdata.indicator.pack.c3.C3Pack;
import esusdata.result.ResultJson;
import esusdata.result.model.PublishedResult;
import esusdata.run.extract.ExtractFixtures;
import esusdata.run.extract.ExtractFixturesV2;
import esusdata.run.extract.ExtractWriterV2;
import esusdata.run.extract.ExtractionManifest;
import esusdata.run.extract.ManifestChecksums;
import esusdata.run.extract.ManifestPart;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * ENG-19 for a canonical v2 pack (ADR 0030), the way every pack's own replay test runs it: a
 * synthetic extract written by {@link ExtractFixturesV2} replayed through {@code
 * RunExecutor.runFromExtract}, with no PEC. A pack whose rule is still pending ({@link SkeletonRule}:
 * C2's identity and parts, no calculation) publishes {@code BLOCKED} without counts — never a zero —
 * and no evidence; and only an extract whose parts are exactly what the rule asks for is accepted.
 * The pending rule keeps this test about the pipeline, not about C2's own rule (ENG-19 of each
 * pack lives in its {@code <Pacote>ReplayTest}).
 */
class PackReplayTest {

    private static final YearMonth COMPETENCIA = YearMonth.of(2026, 3);
    private static final String IBGE = CanonicalFixtures.IBGE;

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

    private ExtractionManifest c2Extract(String extractionId) throws Exception {
        return ExtractFixturesV2.forRule(new SkeletonRule(), COMPETENCIA)
                .add(CanonicalFixtures.person("p1", LocalDate.of(2024, 5, 10), "FEMININO"))
                .add(CanonicalFixtures.registration("p1", LocalDate.of(2025, 9, 1), "2750325", "0000346268"))
                .add(
                        Capabilities.CARE_ENCOUNTER,
                        CanonicalFixtures.encounter("p1", LocalDate.of(2026, 2, 10), "225142", false))
                .write(fixture.extractsDir, extractionId, "src-1");
    }

    @Test
    void eng19_aPendingPackPublishesBlockedWithoutCountsFromASyntheticExtract() throws Exception {
        ExtractionManifest extract = c2Extract("ext-c2-2026-03");

        RunExecutor.RunOutcome run = fixture.replay(extract, new SkeletonRule(), COMPETENCIA, "gestor");

        PublishedResult published = fixture.published(run.resultId(), IBGE);
        assertThat(published.indicatorPack()).isEqualTo(C2Pack.ID);
        assertThat(published.ruleVersion()).isEqualTo(C2Pack.RULE_VERSION);
        assertThat(published.status()).isEqualTo("BLOCKED");
        assertThat(published.valueText()).isNull();
        assertThat(published.numeratorText()).isNull();
        assertThat(published.denominatorText()).isNull();
        assertThat(published.denominatorKind())
                .isEqualTo(new SkeletonRule().descriptor().denominatorKind());
        assertThat(published.valueKind()).isEqualTo("SCORE");
        assertThat(published.valueExact()).isNull();
        assertThat(published.consolidationEligible()).isFalse();
        assertThat(ResultJson.readComponents(published.componentsJson())).isEmpty();
        assertThat(ResultJson.readTeams(published.teamResultsJson())).isEmpty();
        assertThat(published.limitationsJson()).contains("Regra em implementação", "Portão A");
        assertThat(published.canonicalSchemaVersion()).isEqualTo("2");
        assertThat(published.evidenceGrain()).isEqualTo("SUBJECT_PRACTICE");
        assertThat(published.reproducibilityLevel()).isEqualTo("REPRODUCIBLE");
        assertThat(fixture.evidence(run.resultId(), IBGE)).isEmpty();
        // The manifest is stored with its parts (V10 parts_json), exactly as published on disk.
        assertThat(fixture.extractionManifestRepository
                        .findById(extract.extractionId())
                        .orElseThrow()
                        .manifest())
                .isEqualTo(extract);
    }

    @Test
    void theSameExtractReplayedTwiceHasTheSameInputFingerprint() throws Exception {
        ExtractionManifest extract = c2Extract("ext-c2-twice");

        String first = fixture.published(
                        fixture.replay(extract, new SkeletonRule(), COMPETENCIA, "gestor")
                                .resultId(),
                        IBGE)
                .inputFingerprint();
        String second = fixture.published(
                        fixture.replay(extract, new SkeletonRule(), COMPETENCIA, "gestor")
                                .resultId(),
                        IBGE)
                .inputFingerprint();

        assertThat(second).isEqualTo(first).startsWith("sha256:");
    }

    @Test
    void anExtractOfAnotherPackIsRefusedBeforeAnythingIsStaged() throws Exception {
        ExtractionManifest extract = c2Extract("ext-c2-for-c3");

        assertThatThrownBy(() -> fixture.replay(extract, new C3Pack(), COMPETENCIA, "gestor"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fixture.jdbc.queryForObject("select count(*) from result_staging", Integer.class))
                .isZero();
    }

    @Test
    void aCanonicalV1ExtractIsRefusedForAV2PackAndAV2ExtractForC1() throws Exception {
        ExtractionManifest v1 = ExtractFixtures.write(fixture.extractsDir, "ext-v1", "src-1", IBGE, "2026-03", 1, 1, 0);
        ExtractionManifest v2 = c2Extract("ext-v2");

        assertThatThrownBy(() -> fixture.replay(v1, new SkeletonRule(), COMPETENCIA, "gestor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("canonical v1");
        assertThatThrownBy(() -> fixture.replay(v2, new C1Pack(), COMPETENCIA, "gestor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("canonical v2");
    }

    @Test
    void aPartReadWithOtherBindsIsRefusedEvenWithConsistentChecksums() throws Exception {
        List<ManifestPart> parts = new ArrayList<>(ExtractFixturesV2.parts(new SkeletonRule(), COMPETENCIA));
        ManifestPart procedures = parts.stream()
                .filter(part -> Capabilities.PROCEDURE_PERFORMED.equals(part.capability()))
                .findFirst()
                .orElseThrow();
        SortedMap<String, List<String>> params = new TreeMap<>(procedures.params());
        params.put(Capabilities.PROCEDURE_CODES, List.of("0301010072"));
        parts.set(
                parts.indexOf(procedures),
                new ManifestPart(
                        procedures.index(),
                        procedures.capability(),
                        procedures.adapterVersion(),
                        procedures.queryChecksum(),
                        procedures.recordKind(),
                        procedures.periodStart(),
                        procedures.periodEndExclusive(),
                        params,
                        ManifestChecksums.paramsChecksum(params),
                        0));
        ExtractionManifest tampered;
        try (ExtractWriterV2 writer = new ExtractWriterV2(fixture.extractsDir, "ext-other-binds")) {
            tampered = writer.publish(checksum -> new ExtractionManifest(
                    "ext-other-binds",
                    "src-1",
                    IBGE,
                    "2024-02-01",
                    "2026-04-01",
                    ExtractFixturesV2.STARTED_AT,
                    ExtractFixturesV2.FINISHED_AT,
                    ExtractionManifest.CANONICAL_SCHEMA_VERSION_V2,
                    "COMPLETE",
                    "SNAPSHOT",
                    "America/Sao_Paulo",
                    0,
                    0,
                    checksum,
                    ManifestChecksums.compositeQueryChecksum(parts),
                    "0.1.0",
                    parts));
        }

        assertThatThrownBy(() -> fixture.replay(tampered, new SkeletonRule(), COMPETENCIA, "gestor"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(Capabilities.PROCEDURE_PERFORMED);
    }

    /**
     * C2's identity, parts and bands with no calculation — what every pack published before its
     * rule existed — so the real C2 can land without touching this test.
     */
    private static final class SkeletonRule implements IndicatorRule {

        private static final String PENDING = "Regra em implementação (ADR 0030): o pacote ainda não calcula.";

        private final C2Pack pack = new C2Pack();

        @Override
        public PackDescriptor descriptor() {
            return pack.descriptor();
        }

        @Override
        public DataRequirements requirements(YearMonth competencia) {
            return pack.requirements(competencia);
        }

        @Override
        public RuleOutcome evaluate(CanonicalDataset data, EvaluationContext context) {
            return RuleOutcomes.pending(pack.descriptor(), context, PENDING);
        }

        @Override
        public Optional<Classification> classify(ExactRatio value) {
            return pack.classify(value);
        }
    }
}
