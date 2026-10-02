package esusdata.indicator.pack.componente3;

import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.Quadrimestre;
import java.math.BigInteger;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;

/**
 * The consolidated quadrimestre (NT 8/2026). A unit's {@code score} is present only when every
 * component indicator has a quadrimestral value — a missing component never becomes zero and its
 * weight is never redistributed (§2.1, MET-17).
 */
public record ComponentIIIResult(Quadrimestre quadrimestre, List<UnitResult> units, List<String> limitations) {
    public ComponentIIIResult {
        Objects.requireNonNull(quadrimestre, "quadrimestre");
        units = List.copyOf(units);
        limitations = List.copyOf(limitations);
    }

    /**
     * The consolidation of one unit (a team, or the municipality when {@code ine} is null).
     *
     * @param status {@code COMPUTED} when the seven indicators are, otherwise the worst indicator
     *     status ({@code BLOCKED} > {@code UNSUPPORTED_SOURCE} > {@code RULE_AMBIGUITY} > {@code
     *     NO_DENOMINATOR}); the release gates rest on the monthly results of C1–C7
     * @param financialTransferClassification the classification the transfer uses in the
     *     transition of Portaria 10.994/2026, kept apart from the methodological one
     */
    public record UnitResult(
            String ine,
            String cnes,
            List<IndicatorQuadrimestral> indicators,
            IndicatorStatus status,
            ExactRatio score,
            Classification methodologicalClassification,
            Classification financialTransferClassification,
            List<String> limitations) {
        public UnitResult {
            indicators = List.copyOf(indicators);
            limitations = List.copyOf(limitations);
        }
    }

    /**
     * One indicator of a unit: the months that entered the mean, the exact mean, its band and the
     * factor of the band (0,25 · 0,50 · 0,75 · 1,00).
     */
    public record IndicatorQuadrimestral(
            String indicatorPack,
            BigInteger weight,
            List<YearMonth> monthsUsed,
            List<String> resultIds,
            IndicatorStatus status,
            ExactRatio mean,
            Classification classification,
            ExactRatio factor) {
        public IndicatorQuadrimestral {
            monthsUsed = List.copyOf(monthsUsed);
            resultIds = List.copyOf(resultIds);
        }
    }
}
