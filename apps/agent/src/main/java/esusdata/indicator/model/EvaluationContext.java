package esusdata.indicator.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * The scope of one evaluation: the authorized municipality, the competência and the care cutoff
 * (§1.7.2: the calculation never depends on the day someone opens a screen).
 */
public record EvaluationContext(String municipalityIbge, YearMonth competencia, LocalDate dataCutoff) {
    public EvaluationContext {
        Objects.requireNonNull(competencia, "competencia");
        Objects.requireNonNull(dataCutoff, "dataCutoff");
        if (municipalityIbge == null || !municipalityIbge.matches("\\d{7}")) {
            throw new IllegalArgumentException("municipality must be a 7-digit IBGE code: " + municipalityIbge);
        }
    }

    /** The usual context: the cutoff is the last day of the competência. */
    public static EvaluationContext endOfMonth(String municipalityIbge, YearMonth competencia) {
        return new EvaluationContext(municipalityIbge, competencia, competencia.atEndOfMonth());
    }

    public String referencePeriod() {
        return competencia.toString();
    }
}
