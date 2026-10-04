package esusdata.indicator.model;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Everything one run of a rule needs read from the source (ADR 0030). {@code
 * canonicalSchemaVersion} 1 is C1's original single-part extract (unchanged); 2 is the multi-part
 * extract, one part per capability, read in one consistent transaction.
 */
public record DataRequirements(int canonicalSchemaVersion, List<PartRequirement> parts) {

    public static final int V1 = 1;
    public static final int V2 = 2;

    public DataRequirements {
        if (canonicalSchemaVersion != V1 && canonicalSchemaVersion != V2) {
            throw new IllegalArgumentException("unknown canonical schema version " + canonicalSchemaVersion);
        }
        parts = List.copyOf(parts);
        if (parts.isEmpty()) {
            throw new IllegalArgumentException("a run reads at least one capability");
        }
        if (canonicalSchemaVersion == V1 && parts.size() != 1) {
            throw new IllegalArgumentException("a canonical v1 extract has exactly one part");
        }
        Set<String> seen = new HashSet<>();
        for (PartRequirement part : parts) {
            if (!seen.add(part.capability())) {
                throw new IllegalArgumentException("capability " + part.capability() + " is listed twice");
            }
        }
    }
}
