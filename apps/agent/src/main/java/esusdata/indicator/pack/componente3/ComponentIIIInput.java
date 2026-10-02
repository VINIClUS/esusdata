package esusdata.indicator.pack.componente3;

import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.Quadrimestre;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * The published monthly results one consolidation reads (NT 8/2026), grouped by unit. A unit is a
 * team (INE) or, with {@code ine == null}, the municipality as a whole.
 */
public record ComponentIIIInput(String municipalityIbge, Quadrimestre quadrimestre, List<Unit> units) {
    public ComponentIIIInput {
        Objects.requireNonNull(quadrimestre, "quadrimestre");
        units = List.copyOf(units);
    }

    public record Unit(String ine, String cnes, List<Monthly> monthly) {
        public Unit {
            monthly = List.copyOf(monthly);
        }
    }

    /**
     * One published monthly result of one pack for one unit.
     *
     * @param value the exact value on the pack's own scale, or {@code null} unless {@code COMPUTED}
     * @param consolidationEligible whether the month enters the mean (C2/C3: only months with a
     *     cohort event)
     */
    public record Monthly(
            String indicatorPack,
            YearMonth month,
            String resultId,
            IndicatorStatus status,
            ExactRatio value,
            boolean consolidationEligible) {}
}
