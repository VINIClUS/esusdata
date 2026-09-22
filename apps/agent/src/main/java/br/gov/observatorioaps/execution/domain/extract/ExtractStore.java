package br.gov.observatorioaps.execution.domain.extract;

import br.gov.observatorioaps.indicators.domain.CanonicalEncounter;

import java.io.IOException;
import java.util.List;

/**
 * Reads a finalized extract back, bound to one base directory — the port
 * {@code execution.application.IndicatorRunExecutor} calls instead of instantiating {@code
 * ExtractReader} and threading a base path through every call itself.
 */
public interface ExtractStore {

    ExtractionManifest readManifest(String extractionId) throws IOException;

    List<CanonicalEncounter> readEncounters(ExtractionManifest manifest) throws IOException;
}
