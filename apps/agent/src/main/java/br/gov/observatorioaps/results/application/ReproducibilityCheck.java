package br.gov.observatorioaps.results.application;

import br.gov.observatorioaps.execution.domain.extract.ExtractStore;
import java.io.IOException;

/**
 * Verifies that a referenced extract is still present and intact before it is cited by a
 * publication. §1.9.5: "resultado apontando para arquivo perdido fica indisponível/limitado,
 * nunca silenciosamente reproduzível." A missing or corrupted extract never blocks publication of
 * the already-computed result — it only downgrades {@code reproducibility_level}, since the result
 * itself was computed from evidence already captured in {@code result_staging}/{@code evidence}.
 *
 * <p>Reads through {@link ExtractStore} — the same port {@code execution.application} uses to
 * read a finalized extract back — rather than instantiating {@code ExtractReader} directly, so
 * {@code results} depends on execution's port, never its file adapter.
 */
public final class ReproducibilityCheck {

    public record Outcome(boolean reproducible, String reason) {
    }

    private final ExtractStore extractStore;

    public ReproducibilityCheck(ExtractStore extractStore) {
        this.extractStore = extractStore;
    }

    public Outcome verify(String extractionId) {
        try {
            var manifest = extractStore.readManifest(extractionId);
            extractStore.readEncounters(manifest); // re-verifies checksum & row count
            return new Outcome(true, null);
        } catch (IOException | RuntimeException e) {
            return new Outcome(false, e.getMessage());
        }
    }
}
