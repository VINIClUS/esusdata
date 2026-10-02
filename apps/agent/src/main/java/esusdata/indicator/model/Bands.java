package esusdata.indicator.model;

import java.util.List;
import java.util.Optional;

/**
 * A ficha's classification table as exact intervals {@code (lower, upper]} (ADR 0005, ENG-25):
 * decided by integer cross-multiplication, never by a rounded decimal.
 */
public final class Bands {

    /**
     * C2–C7 (fichas Q02–Q07, item 30 "Parâmetro"): {@code Ótimo > 75 e ≤ 100 · Bom > 50 e ≤ 75 ·
     * Suficiente > 25 e ≤ 50 · Regular ≤ 25}. A value above 100 is outside the ficha's scale.
     */
    public static final Bands QUALIDADE_C2_C7 = new Bands(List.of(
            new Band(ExactRatio.of(75, 1), ExactRatio.of(100, 1), Classification.OTIMO),
            new Band(ExactRatio.of(50, 1), ExactRatio.of(75, 1), Classification.BOM),
            new Band(ExactRatio.of(25, 1), ExactRatio.of(50, 1), Classification.SUFICIENTE),
            new Band(null, ExactRatio.of(25, 1), Classification.REGULAR)));

    private final List<Band> table;

    public Bands(List<Band> table) {
        this.table = List.copyOf(table);
    }

    /** The first band whose {@code (lower, upper]} contains {@code value}, or empty. */
    public Optional<Classification> classify(ExactRatio value) {
        for (Band band : table) {
            if (band.contains(value)) {
                return Optional.of(band.classification());
            }
        }
        return Optional.empty();
    }

    /**
     * One interval {@code (lower, upper]}; a {@code null} bound is open. Both ends are exact
     * ratios on the band's own scale.
     */
    public record Band(ExactRatio lowerExclusive, ExactRatio upperInclusive, Classification classification) {
        public boolean contains(ExactRatio value) {
            boolean aboveLower = lowerExclusive == null || value.compareTo(lowerExclusive) > 0;
            boolean belowUpper = upperInclusive == null || value.compareTo(upperInclusive) <= 0;
            return aboveLower && belowUpper;
        }
    }
}
