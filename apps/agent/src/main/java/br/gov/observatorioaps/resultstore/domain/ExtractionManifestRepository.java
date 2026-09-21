package br.gov.observatorioaps.resultstore.domain;

import br.gov.observatorioaps.extractionstore.domain.ExtractionManifest;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Persists {@link ExtractionManifest} rows once a manifest has been finalized on disk. Never
 * writes a manifest whose data file has not already been verified — callers finalize/verify the
 * extract first (§1.9.5: "finalizar o extrato antes do commit de referência").
 */
public interface ExtractionManifestRepository {
    boolean existsById(String extractionId);

    void save(ExtractionManifest manifest, Path filePath);

    Optional<StoredManifest> findById(String extractionId);
}
