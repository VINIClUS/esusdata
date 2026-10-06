package esusdata.indicator.pack.c2;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.DateWindow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * One eligible child's source records ({@code teamTypes}: the CNES type of each team INE known on the cutoff). Practices read only those {@link #inScope} — inside the
 * child's life and not after the evaluation cutoff, which is not known on the cutoff (§1.7.2).
 */
record ChildRecords(
        ChildClock clock,
        LocalDate cutoff,
        List<CanonicalCareEvent> encounters,
        List<CanonicalProcedureEvent> procedures,
        List<CanonicalHomeVisit> visits,
        List<CanonicalMeasurement> measurements,
        List<CanonicalImmunization> doses,
        Map<String, String> teamTypes) {

    ChildRecords {
        encounters = List.copyOf(encounters);
        procedures = List.copyOf(procedures);
        visits = List.copyOf(visits);
        measurements = List.copyOf(measurements);
        doses = List.copyOf(doses);
        teamTypes = Map.copyOf(teamTypes);
    }

    /** Inside the child's life and not after the cutoff. */
    boolean inScope(LocalDate date) {
        return DateWindow.inclusive(clock.birth(), cutoff).contains(date);
    }

    /** A measured value: a number above zero. "0", blanks and text never prove a measurement. */
    static boolean present(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            return new BigDecimal(value.trim()).signum() > 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }
}
