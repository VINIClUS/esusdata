package esusdata.run.extract;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.DateWindow;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;

/**
 * Reads a finalized extract back, bound to one base directory — the port
 * {@code jobrunner.application.RunExecutor} calls instead of instantiating {@code
 * ExtractReader} and threading a base path through every call itself.
 */
public interface ExtractStore {

    ExtractionManifest readManifest(String extractionId) throws IOException;

    List<CanonicalEncounter> readEncounters(ExtractionManifest manifest) throws IOException;

    /**
     * Every canonical record of a finalized extract, by kind (ADR 0030). A v1 extract (C1) is its
     * encounters under the one capability it read; a v2 extract is read part by part.
     */
    default CanonicalDataset readDataset(ExtractionManifest manifest, String v1Capability) throws IOException {
        if (manifest.isCanonicalV2()) {
            throw new UnsupportedOperationException(
                    "canonical v2 extracts are read by the extract store of ADR 0030 (fase 1c)");
        }
        DateWindow window =
                new DateWindow(LocalDate.parse(manifest.periodStart()), LocalDate.parse(manifest.periodEndExclusive()));
        return CanonicalDataset.ofEncounters(v1Capability, window, readEncounters(manifest));
    }
}
