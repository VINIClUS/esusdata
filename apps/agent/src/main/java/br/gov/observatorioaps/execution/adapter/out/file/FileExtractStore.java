package br.gov.observatorioaps.execution.adapter.out.file;

import br.gov.observatorioaps.indicators.domain.CanonicalEncounter;
import br.gov.observatorioaps.execution.domain.extract.ExtractStore;
import br.gov.observatorioaps.execution.domain.extract.ExtractionManifest;

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
