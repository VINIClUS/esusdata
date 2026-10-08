package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Quadrimestre;
import java.time.YearMonth;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Which published SIAPS quadrimestre may serve as the reference of a pack: the most recent one
 * whose last day is strictly after the latest signature date among the pack's ficha and the NT
 * 8/2026 (the editions in force revoke the earlier ones, and the SIAPS computed a quadrimestre that
 * ended before them with the old rules).
 */
public final class Eligibility {

    private Eligibility() {}

    /** The first quadrimestre whose last day is after the pack's floor date. */
    public static Quadrimestre firstEligible(GatePack pack) {
        Quadrimestre candidate = Quadrimestre.of(YearMonth.from(pack.floor()));
        while (!candidate.cutoff().isAfter(pack.floor())) {
            candidate = SiapsFormats.next(candidate);
        }
        return candidate;
    }

    /** The most recent eligible quadrimestre among {@code published} (SIAPS spellings), if any. */
    public static Optional<Quadrimestre> reference(GatePack pack, Collection<String> published) {
        Quadrimestre first = firstEligible(pack);
        return published.stream()
                .map(SiapsFormats::quadrimestre)
                .filter(quadrimestre -> quadrimestre.compareTo(first) >= 0)
                .max(Comparator.naturalOrder());
    }

    /**
     * True only when {@code compared} is exactly the pack's reference: the most recent eligible
     * published quadrimestre. Any other quadrimestre, even an eligible one, is diagnostic: picking
     * the one that happens to pass is what the rule forbids.
     */
    public static boolean isReference(GatePack pack, Quadrimestre compared, Collection<String> published) {
        return reference(pack, published).filter(compared::equals).isPresent();
    }

    /** Why there is no reference: the first eligible quadrimestre is not published yet. */
    public static String waitingFor(GatePack pack) {
        return "aguardando " + SiapsFormats.quadrimestre(firstEligible(pack)) + " no SIAPS";
    }
}
