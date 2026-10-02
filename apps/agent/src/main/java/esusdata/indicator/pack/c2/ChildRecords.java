package esusdata.indicator.pack.c2;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.DateWindow;
import java.time.LocalDate;
import java.util.List;

/**
 * One eligible child's source records. Practices read only those {@link #inScope} — inside the
 * child's life and not after the evaluation cutoff, which is not known on the cutoff (§1.7.2).
 */
record ChildRecords(
        ChildClock clock,
        LocalDate cutoff,
        List<CanonicalCareEvent> encounters,
        List<CanonicalProcedureEvent> procedures,
        List<CanonicalHomeVisit> visits,
        List<CanonicalMeasurement> measurements,
        List<CanonicalImmunization> doses) {

    ChildRecords {
        encounters = List.copyOf(encounters);
        procedures = List.copyOf(procedures);
        visits = List.copyOf(visits);
        measurements = List.copyOf(measurements);
        doses = List.copyOf(doses);
    }

    /** Inside the child's life and not after the cutoff. */
    boolean inScope(LocalDate date) {
        return DateWindow.inclusive(clock.birth(), cutoff).contains(date);
    }

    static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
