package esusdata.indicator.reconciliation;

import esusdata.indicator.pack.c1.C1MethodologyProbes;
import esusdata.indicator.pack.c2.C2MethodologyProbes;
import esusdata.indicator.pack.c3.C3MethodologyProbes;
import esusdata.indicator.pack.c4.C4MethodologyProbes;
import esusdata.indicator.pack.c5.C5MethodologyProbes;
import esusdata.indicator.pack.c6.C6MethodologyProbes;
import esusdata.indicator.pack.c7.C7MethodologyProbes;
import java.util.ArrayList;
import java.util.List;

/**
 * Every methodology probe the profiles may name (spec §9.4), in one explicit list: a probe takes
 * part in a dossier or a diagnostic only once it is listed here. The probes of C1 to C7 live in the
 * test tree of their pack's package, to reach its package-private hooks; those of the Nota Final
 * and those every pack shares ({@link CommonMethodologyProbes}) live in this package.
 */
public final class MethodologyProbeCatalog {

    private MethodologyProbeCatalog() {}

    /** Every probe, in catalog order. */
    public static List<MethodologyProbe> all() {
        List<MethodologyProbe> probes = new ArrayList<>();
        probes.addAll(C1MethodologyProbes.all());
        probes.addAll(C2MethodologyProbes.all());
        probes.addAll(C3MethodologyProbes.all());
        probes.addAll(C4MethodologyProbes.all());
        probes.addAll(C5MethodologyProbes.all());
        probes.addAll(C6MethodologyProbes.all());
        probes.addAll(C7MethodologyProbes.all());
        probes.addAll(CommonMethodologyProbes.all());
        probes.addAll(PlaceholderProbes.all());
        return List.copyOf(probes);
    }

    /** The probes that serve {@code packId}, in catalog order; empty when it has none. */
    public static List<MethodologyProbe> forPack(String packId) {
        return all().stream().filter(probe -> probe.packs().contains(packId)).toList();
    }
}
