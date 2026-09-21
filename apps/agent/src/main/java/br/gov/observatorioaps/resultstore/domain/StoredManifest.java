package br.gov.observatorioaps.resultstore.domain;

import br.gov.observatorioaps.extractionstore.domain.ExtractionManifest;
import java.nio.file.Path;

/** A persisted {@code extraction_manifests} row: the manifest plus its storage location. */
public record StoredManifest(ExtractionManifest manifest, Path filePath) {
}
