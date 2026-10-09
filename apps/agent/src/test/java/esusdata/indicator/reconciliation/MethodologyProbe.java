package esusdata.indicator.reconciliation;

import java.util.Set;

/**
 * A detector of one known methodological difference (spec §9.4): given the accepted canonical
 * dataset of one reference, it says whether reading the ficha the other way would change the
 * result, and for how many subjects. The profile of a rule names the probe of each of its
 * dimensions by {@code probe_id}; a dimension whose probe does not exist cannot be proved, and a
 * reference that depends on it is inconclusive.
 *
 * <p>A probe is a pure function of its {@link ProbeContext}: no JDBC, no raw table, no file, no
 * clock, no network. What the dataset does not hold it reports as {@link Observability#PARTIAL}
 * or {@link Observability#NONE}; it never fills the gap with a zero.
 *
 * <p>The interface is public so that the probes of C2 to C7 can live in the test tree of the
 * package of their pack ({@code esusdata.indicator.pack.cN}) and reach its package-private hooks.
 */
public interface MethodologyProbe {

    /** The {@code probe_id} the profiles use for this probe, e.g. {@code c2.cohort.second-birthday}. */
    String id();

    /** The ids of the packs whose profiles list this probe; a probe may serve several. */
    Set<String> packs();

    /**
     * Evaluates the probe on one reference. The result carries {@link #id()}.
     *
     * @param context the dataset of the pack (or of the Nota Final) and the reference it is
     *     reconciled with
     */
    ProbeResult evaluate(ProbeContext context);
}
