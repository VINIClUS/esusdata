package esusdata.indicator.sensitivity;

import java.math.BigInteger;

/**
 * One line of the sensitivity report: what a candidate reading of an ambiguity yields for one unit
 * (the municipality or one INE). Aggregates only — never a subject key, a date or a source row.
 *
 * @param pack the pack id, e.g. {@code c2-desenvolvimento-infantil}
 * @param code the ambiguity code(s) the reading is about, e.g. {@code AMB-C2-03}
 * @param reading the reading's name, in Portuguese
 * @param unit {@link ReadingRow#MUNICIPALITY} or the INE
 * @param component the practice or subgroup the numbers are about, or {@code null} for the whole score
 * @param affected subjects that depend on the code in this unit, or {@code null} when it is not a count of subjects
 * @param numerator points (C2, C3) or people who met the practice (C7 subgroups), or {@code null}
 * @param denominator subjects (C2, C3) or people of the subgroup (C7), or {@code null}
 * @param value the score or ratio with four decimals, or {@code null} when it is undefined
 * @param remaining subjects still ambiguous under the reading (a {@code value} is then a lower bound)
 */
public record ReadingRow(
        String pack,
        String code,
        String reading,
        String unit,
        String component,
        Long affected,
        BigInteger numerator,
        BigInteger denominator,
        String value,
        Long remaining) {

    /** The unit of the whole municipality. */
    public static final String MUNICIPALITY = "MUNICIPIO";

    /** The reading label of the plain frequency rows. */
    public static final String FREQUENCY = "frequência";

    /** The reading label of a code that only appears in the result's limitations. */
    public static final String LIMITATION = "citado nas limitações do resultado";
}
