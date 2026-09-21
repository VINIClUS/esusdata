package br.gov.observatorioaps.resultstore.infrastructure.file;

import java.nio.file.Path;
import br.gov.observatorioaps.resultstore.application.ReproducibilityCheck;
/**
 * The extract data-file naming convention, confirmed against {@code ExtractWriter}/{@code
 * ExtractReader} (not inferred): {@code baseDir/<extractionId>.jsonl.gz}, manifest at {@code
 * baseDir/<extractionId>.manifest.json}. This is what {@code extraction_manifests.file_path}
 * stores and what {@link ReproducibilityCheck} looks for.
 */
public final class ExtractionFilePaths {

    private ExtractionFilePaths() {
    }

    public static Path dataFile(Path baseDir, String extractionId) {
        return baseDir.resolve(extractionId + ".jsonl.gz");
    }
}
