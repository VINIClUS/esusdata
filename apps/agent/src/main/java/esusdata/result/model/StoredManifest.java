package esusdata.result.model;

import esusdata.run.extract.ExtractionManifest;
import java.nio.file.Path;

/** A persisted {@code extraction_manifests} row: the manifest plus its storage location. */
public record StoredManifest(ExtractionManifest manifest, Path filePath) {}
