package br.gov.observatorioaps.results.domain;

import br.gov.observatorioaps.execution.domain.extract.ExtractionManifest;
import java.nio.file.Path;

/** A persisted {@code extraction_manifests} row: the manifest plus its storage location. */
public record StoredManifest(ExtractionManifest manifest, Path filePath) {
}
