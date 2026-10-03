package esusdata.source.pec;

import java.io.Serial;
import java.util.List;

/**
 * A pack asked for a capability the source's PEC version has no {@code VALIDATED} compatibility
 * entry for (ADR 0030, §1.6). Raised in Java before the execution plane is spawned: a probe that
 * diverged inside the child would put the whole source on cooldown, while this refusal is about
 * one pack and is definitive — retrying cannot validate a capability.
 */
public final class UnsupportedSourceException extends RuntimeException {

    /** The failure code jobs and screens show for this refusal. */
    public static final String CODE = "UNSUPPORTED_SOURCE";

    @Serial
    private static final long serialVersionUID = 1L;

    @SuppressWarnings("serial") // an immutable List.copyOf, serializable in practice
    private final List<String> missingCapabilities;

    public UnsupportedSourceException(String indicatorPack, String sourceId, List<String> missingCapabilities) {
        super(CODE + ": " + indicatorPack + " reads capabilities with no VALIDATED compatibility entry for source "
                + sourceId + ": " + String.join(", ", missingCapabilities));
        this.missingCapabilities = List.copyOf(missingCapabilities);
    }

    /** The capabilities without a {@code VALIDATED} entry, in the pack's order. */
    public List<String> missingCapabilities() {
        return missingCapabilities;
    }
}
