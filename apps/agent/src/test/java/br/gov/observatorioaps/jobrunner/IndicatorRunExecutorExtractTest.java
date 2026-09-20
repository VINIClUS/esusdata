package br.gov.observatorioaps.jobrunner;

import br.gov.observatorioaps.extractionstore.ExtractFixtures;
import br.gov.observatorioaps.extractionstore.ExtractionManifest;
import br.gov.observatorioaps.indicatorengine.IndicatorResult;
import br.gov.observatorioaps.indicatorpacks.c1.C1Rule;
import br.gov.observatorioaps.resultstore.EvidencePage;
import br.gov.observatorioaps.resultstore.EvidenceRepository;
import br.gov.observatorioaps.resultstore.PublishedResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end over a synthetic extract, without any PEC involved: job → staging → publication.
 * C1's release gates are incomplete (§4.4), so the published result is honestly {@code BLOCKED}
 * with exact counts — never a fabricated value.
 */
class IndicatorRunExecutorExtractTest {

    @TempDir
    Path dataDir;

    private JobRunnerTestFixture fixture;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);
        fixture = new JobRunnerTestFixture(dataDir, clock);
        fixture.registerSource("src-1", "3541307");
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void runFromExtractPublishesABlockedResultWithExactCountsAndFullEvidence() throws Exception {
        ExtractionManifest manifest = ExtractFixtures.write(
                fixture.extractsDir, "ext-c1-2026-03", "src-1", "3541307", "2026-03",
                7, 3, 2); // 7 programado, 3 espontaneo, 2 unmapped -> numerator 7, denominator 10

        Job job = fixture.jobRepository.enqueue(new EnqueueRequest(
                "job-1", "run-1", "3541307", C1Rule.INDICATOR_PACK, C1Rule.RULE_VERSION,
                "2026-03", 3, "src-1", manifest.extractionId(),
                null, null, null, null, null, clock.instant()));
        Job acquired = fixture.jobRepository.acquireNext("proc-1", clock.instant()).orElseThrow();
        assertThat(acquired.jobId()).isEqualTo(job.jobId());

        CancellationToken token = new CancellationToken();
        var context = new IndicatorRunExecutor.RunContext(
                acquired.jobId(), acquired.runId(), acquired.sourceId(), acquired.executionGeneration(),
                acquired.processInstanceId(), acquired.extractionId(), acquired.municipalityIbge(),
                acquired.referencePeriod());
        var outcome = fixture.executor.runFromExtract(context, token);

        IndicatorResult result = outcome.result();
        assertThat(result.status()).isEqualTo(IndicatorResult.IndicatorStatus.BLOCKED);
        assertThat(result.numerator().intValue()).isEqualTo(7);
        assertThat(result.denominator().intValue()).isEqualTo(10);
        assertThat(result.valueText()).isNull();
        assertThat(result.classification()).isNull();

        Job finished = fixture.jobRepository.findById(job.jobId()).orElseThrow();
        assertThat(finished.state()).isEqualTo(JobState.SUCCEEDED);
        assertThat(finished.stagingId()).isEqualTo(outcome.stagingId());

        PublishedResult published = fixture.resultRepository
                .findByIdInScope(outcome.resultId(), "3541307").orElseThrow();
        assertThat(published.status()).isEqualTo("BLOCKED");
        assertThat(published.numeratorText()).isEqualTo("7");
        assertThat(published.denominatorText()).isEqualTo("10");
        assertThat(published.validationStatus()).isEqualTo("NOT_VALIDATED");
        assertThat(published.evidenceGrain()).isEqualTo("SOURCE_EVENT");
        assertThat(published.reproducibilityLevel()).isEqualTo("REPRODUCIBLE");

        // ENG-36: the extract lets the population be reconstructed, not just positive evidence —
        // all 12 encounters (7 numerator + 3 denominator-only + 2 excluded) are present.
        EvidenceRepository evidenceRepository = new EvidenceRepository(fixture.jdbc);
        EvidencePage page = evidenceRepository.page(outcome.resultId(), "3541307", null,
                EvidenceRepository.DEFAULT_PAGE_SIZE);
        assertThat(page.items()).hasSize(12);
        assertThat(page.items().stream().filter(e -> e.decision().equals("IN_NUMERATOR")).count())
                .isEqualTo(7);
        assertThat(page.items().stream().filter(e -> e.decision().equals("DENOMINATOR_ONLY")).count())
                .isEqualTo(3);
        assertThat(page.items().stream().filter(e -> e.decision().equals("EXCLUDED_UNMAPPED")).count())
                .isEqualTo(2);
    }
}
