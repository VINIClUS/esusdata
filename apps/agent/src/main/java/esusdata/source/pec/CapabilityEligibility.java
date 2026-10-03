package esusdata.source.pec;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Whether a source can serve a pack's capabilities (ADR 0030): every capability needs a {@code
 * VALIDATED} entry of the compatibility matrix for the source's PEC version, read model and
 * installation role. Checked in Java before any acquisition — the execution plane still checks the
 * exact entry (PostgreSQL version, query checksum, object fingerprints) once connected.
 *
 * <p>Until the live validation of the canonical v2 capabilities only C1's capability is {@code
 * VALIDATED}, so only C1 is eligible; C2–C7 come back with their missing capabilities, never as a
 * zero.
 */
public final class CapabilityEligibility {

    private final PecCompatibilityMatrix matrix;

    public CapabilityEligibility(PecCompatibilityMatrix matrix) {
        this.matrix = matrix;
    }

    /**
     * The identity a registered source declares, or empty when it is incomplete or malformed — such
     * a source validates no capability.
     */
    public static Optional<PecSourceIdentity> identityOf(
            String sourceId, String pecVersion, String readModel, String installationRole) {
        try {
            PecSourceIdentity identity = new PecSourceIdentity(sourceId, pecVersion, readModel, installationRole);
            return identity.isComplete() ? Optional.of(identity) : Optional.empty();
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
    }

    /**
     * The capabilities of {@code required} without a {@code VALIDATED} entry for {@code identity},
     * in the order given and without repetition — all of them when {@code identity} is null or
     * incomplete.
     */
    public List<String> missing(Collection<String> required, PecSourceIdentity identity) {
        Set<String> validated = identity == null ? Set.of() : matrix.validatedCapabilities(identity);
        List<String> missing = new ArrayList<>();
        for (String capability : new LinkedHashSet<>(required)) {
            if (!validated.contains(capability)) {
                missing.add(capability);
            }
        }
        return List.copyOf(missing);
    }

    /**
     * Refuses a pack the source cannot serve.
     *
     * @throws UnsupportedSourceException listing the capabilities without a {@code VALIDATED} entry
     */
    public void require(String indicatorPack, Collection<String> required, PecSourceIdentity identity) {
        List<String> missing = missing(required, identity);
        if (!missing.isEmpty()) {
            throw new UnsupportedSourceException(indicatorPack, identity == null ? null : identity.sourceId(), missing);
        }
    }
}
