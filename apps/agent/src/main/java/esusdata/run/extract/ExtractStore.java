package esusdata.run.extract;

import esusdata.indicator.model.CanonicalEncounter;

import java.io.IOException;
import java.util.List;

/**
 * Reads a finalized extract back, bound to one base directory — the port
 * {@code jobrunner.application.RunExecutor} calls instead of instantiating {@code
 * ExtractReader} and threading a base path through every call itself.
 */
public interface ExtractStore {

    ExtractionManifest readManifest(String extractionId) throws IOException;

    List<CanonicalEncounter> readEncounters(ExtractionManifest manifest) throws IOException;
}
