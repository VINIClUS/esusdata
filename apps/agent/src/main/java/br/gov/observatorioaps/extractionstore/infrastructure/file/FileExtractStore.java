package br.gov.observatorioaps.extractionstore.infrastructure.file;

import br.gov.observatorioaps.extractionstore.domain.CanonicalEncounter;
import br.gov.observatorioaps.extractionstore.domain.ExtractStore;
import br.gov.observatorioaps.extractionstore.domain.ExtractionManifest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** {@link ExtractStore} bound to one extracts directory, delegating to {@link ExtractReader}. */
public final class FileExtractStore implements ExtractStore {

    private final Path baseDir;
    private final ExtractReader reader = new ExtractReader();

    public FileExtractStore(Path baseDir) {
        this.baseDir = baseDir;
    }

    @Override
    public ExtractionManifest readManifest(String extractionId) throws IOException {
        return reader.readManifest(baseDir, extractionId);
    }

    @Override
    public List<CanonicalEncounter> readEncounters(ExtractionManifest manifest) throws IOException {
        return reader.readEncounters(baseDir, manifest);
    }
}
