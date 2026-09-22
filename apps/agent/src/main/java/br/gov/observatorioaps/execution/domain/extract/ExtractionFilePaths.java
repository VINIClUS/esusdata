package br.gov.observatorioaps.execution.domain.extract;

import java.nio.file.Path;

/**
 * The extract data-file naming convention, confirmed against {@code ExtractWriter}/{@code
 * ExtractReader} (not inferred): {@code baseDir/<extractionId>.jsonl.gz}, manifest at {@code
 * baseDir/<extractionId>.manifest.json}. This is what {@code extraction_manifests.file_path}
 * stores and what {@code ReproducibilityCheck} looks for.
 */
public final class ExtractionFilePaths {

    private ExtractionFilePaths() {
    }

    public static Path dataFile(Path baseDir, String extractionId) {
        return baseDir.resolve(extractionId + ".jsonl.gz");
    }
}
