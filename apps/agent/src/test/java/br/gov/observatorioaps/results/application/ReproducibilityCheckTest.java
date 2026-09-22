package br.gov.observatorioaps.results.application;

import br.gov.observatorioaps.execution.adapter.out.file.ExtractFixtures;
import br.gov.observatorioaps.execution.domain.extract.ExtractionManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §1.9.5: "resultado apontando para arquivo perdido fica indisponível/limitado, nunca
 * silenciosamente reproduzível."
 */
class ReproducibilityCheckTest {

    @TempDir
    Path extractsDir;

    @Test
    void healthyExtractIsReproducible() throws Exception {
        ExtractionManifest manifest = ExtractFixtures.write(
                extractsDir, "ext-ok", "src-1", "3541307", "2026-03", 3, 2, 0);

        var outcome = new ReproducibilityCheck(extractsDir).verify(manifest.extractionId());
        assertThat(outcome.reproducible()).isTrue();
    }

    @Test
    void missingDataFileIsNotReproducible() throws Exception {
        ExtractionManifest manifest = ExtractFixtures.write(
                extractsDir, "ext-missing", "src-1", "3541307", "2026-03", 3, 2, 0);
        Files.delete(extractsDir.resolve(manifest.extractionId() + ".jsonl.gz"));

        var outcome = new ReproducibilityCheck(extractsDir).verify(manifest.extractionId());
        assertThat(outcome.reproducible()).isFalse();
        assertThat(outcome.reason()).isNotBlank();
    }

    @Test
    void corruptedDataFileIsNotReproducible() throws Exception {
        ExtractionManifest manifest = ExtractFixtures.write(
                extractsDir, "ext-corrupt", "src-1", "3541307", "2026-03", 3, 2, 0);
        Path dataFile = extractsDir.resolve(manifest.extractionId() + ".jsonl.gz");
        Files.write(dataFile, new byte[]{0, 1, 2, 3});

        var outcome = new ReproducibilityCheck(extractsDir).verify(manifest.extractionId());
        assertThat(outcome.reproducible()).isFalse();
    }

    @Test
    void unknownExtractionIdIsNotReproducible() {
        var outcome = new ReproducibilityCheck(extractsDir).verify("never-existed");
        assertThat(outcome.reproducible()).isFalse();
    }
}
