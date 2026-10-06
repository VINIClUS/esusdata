package esusdata.indicator.pack.componente3;

import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.GateStatus;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.Quadrimestre;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * The published monthly results one consolidation reads (NT 8/2026), grouped by unit. A unit is a
 * team (INE) or, with {@code ine == null}, the municipality as a whole. {@code gates} is where the
 * Nota Final's release gates stand, handed in by the service: a pack never reads the registry.
 */
public record ComponentIIIInput(
        String municipalityIbge, Quadrimestre quadrimestre, List<Unit> units, GateStatus gates) {
    public ComponentIIIInput {
        Objects.requireNonNull(quadrimestre, "quadrimestre");
        Objects.requireNonNull(gates, "gates");
        units = List.copyOf(units);
    }

    /** An input whose release gates are all still pending (ADR 0032). */
    public ComponentIIIInput(String municipalityIbge, Quadrimestre quadrimestre, List<Unit> units) {
        this(municipalityIbge, quadrimestre, units, GateStatus.pending(ComponentIII.DESCRIPTOR));
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
     * @param ruleVersion the rule version that computed the month, or {@code null} when unknown
     *     (accepted with a limitation); a month of another version blocks the indicator
     */
    public record Monthly(
            String indicatorPack,
            YearMonth month,
            String resultId,
            IndicatorStatus status,
            ExactRatio value,
            boolean consolidationEligible,
            String ruleVersion) {
        public Monthly {
            Objects.requireNonNull(indicatorPack, "indicatorPack");
            Objects.requireNonNull(month, "month");
            Objects.requireNonNull(resultId, "resultId");
            Objects.requireNonNull(status, "status");
        }

        /** A month whose rule version is unknown. */
        public Monthly(
                String indicatorPack,
                YearMonth month,
                String resultId,
                IndicatorStatus status,
                ExactRatio value,
                boolean consolidationEligible) {
            this(indicatorPack, month, resultId, status, value, consolidationEligible, null);
        }
    }
}
