package esusdata.source;

import esusdata.source.model.LastDiagnostic;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecSourceIdentity;
import java.util.List;
import java.util.Optional;

/**
 * The requirements a registered source meets, read from its registration, its last diagnostic and
 * the packaged compatibility matrix (issue #22). Each one claims only what it checks: a CONNECTED
 * diagnostic proves the read session opened, not that the account is SELECT-only; a listed PEC
 * version is not the exact capability/PostgreSQL entry acquisition still demands.
 */
public final class SourceRequirementsService {

    private final PecCompatibilityMatrix matrix;

    public enum Code {
        READ_CONNECTION,
        PEC_POSTGRESQL_FAMILY,
        PEC_VERSION_IN_MATRIX,
        MUNICIPAL_SCOPE
    }

    public record Requirement(Code code, boolean ok) {}

    public SourceRequirementsService(PecCompatibilityMatrix matrix) {
        this.matrix = matrix;
    }

    /** A diagnostic of another configuration version than {@code source}'s confirms nothing. */
    public List<Requirement> requirements(SourceRecord source, Optional<LastDiagnostic> lastDiagnostic) {
        return List.of(
                new Requirement(
                        Code.READ_CONNECTION,
                        lastDiagnostic
                                .filter(d -> d.appliesTo(source))
                                .map(d -> "CONNECTED".equals(d.outcome()))
                                .orElse(false)),
                new Requirement(Code.PEC_POSTGRESQL_FAMILY, "PEC_POSTGRESQL".equals(source.sourceFamily())),
                new Requirement(Code.PEC_VERSION_IN_MATRIX, listedInMatrix(source)),
                new Requirement(
                        Code.MUNICIPAL_SCOPE,
                        source.municipalityIbge() != null
                                && source.municipalityIbge().matches("\\d{7}")));
    }

    private boolean listedInMatrix(SourceRecord source) {
        PecSourceIdentity identity;
        try {
            identity = new PecSourceIdentity(
                    source.id(), source.pecVersion(), source.readModel(), source.pecInstallationRole());
        } catch (IllegalArgumentException e) {
            // An incomplete or malformed registration simply fails this requirement.
            return false;
        }
        return matrix.lists(identity);
    }
}
