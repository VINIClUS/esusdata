package br.gov.observatorioaps.resultstore;

import java.nio.file.Path;

/**
 * The extract data-file naming convention, confirmed against {@code ExtractWriter}/{@code
 * ExtractReader} (not inferred): {@code baseDir/<extractionId>.jsonl.gz}, manifest at {@code
 * baseDir/<extractionId>.manifest.json}. This is what {@code extraction_manifests.file_path}
 * stores and what {@link ReproducibilityCheck} looks for.
 */
final class ExtractionFilePaths {

    private ExtractionFilePaths() {
    }

    static Path dataFile(Path baseDir, String extractionId) {
        return baseDir.resolve(extractionId + ".jsonl.gz");
    }
}
