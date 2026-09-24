package esusdata.result;

import esusdata.run.extract.ExtractReader;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Verifies that a referenced extract is still present and intact before it is cited by a
 * publication. §1.9.5: "resultado apontando para arquivo perdido fica indisponível/limitado,
 * nunca silenciosamente reproduzível." A missing or corrupted extract never blocks publication of
 * the already-computed result — it only downgrades {@code reproducibility_level}, since the result
 * itself was computed from evidence already captured in {@code result_staging}/{@code evidence}.
 */
public final class ReproducibilityCheck {

    private final Path extractsBaseDir;
    private final ExtractReader reader = new ExtractReader();

    public record Outcome(boolean reproducible, String reason) {}

    public ReproducibilityCheck(Path extractsBaseDir) {
        this.extractsBaseDir = extractsBaseDir;
    }

    public Outcome verify(String extractionId) {
        try {
            var manifest = reader.readManifest(extractsBaseDir, extractionId);
            reader.readEncounters(extractsBaseDir, manifest); // re-verifies checksum & row count
            return new Outcome(true, null);
        } catch (IOException
                | RuntimeException e) { // NOPMD - any failure re-reading the extract is a failed check, reported
            return new Outcome(false, e.getMessage());
        }
    }
}
