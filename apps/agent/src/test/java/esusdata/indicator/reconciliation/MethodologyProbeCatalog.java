package esusdata.indicator.reconciliation;

import java.util.ArrayList;
import java.util.List;

/**
 * Every methodology probe the profiles may name (spec §9.4), in one explicit list: a probe takes
 * part in a dossier or a diagnostic only once it is listed here. The probes of C1 to C7 live in the
 * test tree of their pack's package, to reach its package-private hooks; those of the Nota Final
 * live in this package.
 */
public final class MethodologyProbeCatalog {

    private MethodologyProbeCatalog() {}

    /** Every probe, in catalog order. */
    public static List<MethodologyProbe> all() {
        List<MethodologyProbe> probes = new ArrayList<>();
        return List.copyOf(probes);
    }

    /** The probes that serve {@code packId}, in catalog order; empty when it has none. */
    public static List<MethodologyProbe> forPack(String packId) {
        return all().stream().filter(probe -> probe.packs().contains(packId)).toList();
    }
}
