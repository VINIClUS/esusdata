package esusdata.indicator.reconciliation;

import esusdata.indicator.reconciliation.ProbeContext.NotaFinalProbeContext;
import esusdata.indicator.reconciliation.ProbeContext.PackProbeContext;
import java.util.List;
import java.util.Set;

/**
 * A probe that is not implemented, for a dimension whose verdict cannot be reached anyway
 * (spec 2026-10-08 §9.4, ADR 0034 §5). The verdict of the pack is decided by its {@code blockers},
 * structural probes of the same profile that this installation cannot observe completely in any
 * quadrimestre: with one of them {@link Observability#NONE} the pack is inconclusive whatever this
 * probe would say, unless a probe that is complete and shows a divergence on a DIFFERENT dimension
 * makes it incompatible first, and a placeholder never serves a dimension that reads DIFFERENT.
 *
 * <p>It answers {@link Observability#NONE} and says «not implemented», which is not «could not
 * look»: the reason names what decides the verdict instead. For the Nota Final, which has no
 * source records, the blockers are the packs it sums up, so {@code blockers} is empty there.
 * {@code MethodologyProfileConsistencyTest} checks that each blocker is a dimension of the same
 * profile that does not read SAME everywhere, and that the Nota Final has a pack with a blocker in
 * every quadrimestre.
 */
public final class PlaceholderProbe implements MethodologyProbe {

    private final String id;
    private final String packId;
    private final List<String> blockers;

    /**
     * @param id the probe id the profile names
     * @param packId the pack it serves
     * @param blockers the probe ids of the same profile that decide the verdict; empty only for the
     *     Nota Final
     */
    public PlaceholderProbe(String id, String packId, List<String> blockers) {
        this.id = id;
        this.packId = packId;
        this.blockers = List.copyOf(blockers);
        boolean notaFinal = GatePack.NOTA_FINAL.packId().equals(packId);
        if (notaFinal == !this.blockers.isEmpty()) {
            throw new IllegalArgumentException(id + ": a placeholder has blockers unless it is the Nota Final's");
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public Set<String> packs() {
        return Set.of(packId);
    }

    /** The probes that decide the verdict of the pack, in the profile's words. */
    public List<String> blockers() {
        return blockers;
    }

    @Override
    public ProbeResult evaluate(ProbeContext context) {
        return switch (context) {
            case PackProbeContext pack -> ProbeResult.none(id, reason());
            case NotaFinalProbeContext notaFinal -> ProbeResult.none(id, reason());
        };
    }

    private String reason() {
        String decidedBy = blockers.isEmpty()
                ? "the sibling packs (ADR 0034 §7)"
                : String.join(", ", blockers) + ", which this installation cannot observe completely in any"
                        + " quadrimestre";
        return "not implemented: the verdict of " + packId + " is decided by " + decidedBy;
    }
}
