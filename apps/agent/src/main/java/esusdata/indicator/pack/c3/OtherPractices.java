package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalMeasurement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Practices F and K: one dTpa from the 20ª week (24 i, Quadro 06) and one oral-health activity
 * during the pregnancy (Quadro 08). Both are binary: one record is enough, several count once.
 */
final class OtherPractices {

    /** "a partir da 20ª semana": from IG 20s0d (DUM + 140), completed weeks (AMB-C3-01). */
    private static final long DTPA_FROM = 140;

    private OtherPractices() {}

    /**
     * F: a dose of "57", by any CBO that sends the record (AMB-C3-13), applied in {@code [DUM +
     * 140, D + 42]} (AMB-C3-17). The application date is the capability's scope column, never
     * missing (EMENDA 1).
     */
    static PracticeOutcome dtpa(PersonRecords person, GestationWindow window) {
        SortedMap<LocalDate, Tally.Unit> byDate = new TreeMap<>();
        for (CanonicalImmunization dose : person.immunizations()) {
            if (C3Codes.DTPA_ADULT.equals(C3Codes.token(dose.immunobiologicalCode())) && inWindow(dose, window)) {
                EventRef event = EventRef.of(dose);
                byDate.merge(event.date(), Tally.Unit.of(event), OtherPractices::sameDose);
            }
        }
        return Tally.decide(1, List.copyOf(byDate.values()));
    }

    private static boolean inWindow(CanonicalImmunization dose, GestationWindow window) {
        LocalDate date = C3Dates.parse(dose.applicationDate());
        return date != null && window.day(date) >= DTPA_FROM && !date.isAfter(window.lastDay());
    }

    /**
     * The same dose (person, "57", application date) from the MIV and from a transcription is one
     * dose (MET-32): the first in evidence order.
     */
    private static Tally.Unit sameDose(Tally.Unit a, Tally.Unit b) {
        return EventRef.ORDER.compare(a.events().get(0), b.events().get(0)) <= 0 ? a : b;
    }

    /**
     * K: an individual dental encounter (MIAOI), or a collective activity (MIAC) with activity
     * 05/06 and practice flúor/escovação (LEDI 2, 9) — both conditions, AMB-C3-19 — by a dentist
     * or TSB (3224-05, 3224-25; AMB-C3-20) during the pregnancy.
     */
    static PracticeOutcome dental(PersonRecords person, GestationWindow window) {
        List<Tally.Unit> units = new ArrayList<>();
        for (CanonicalCareEvent event : person.dentalCare()) {
            if (C3Codes.DENTAL_CBO.matches(event.cbo())) {
                addPregnancy(units, EventRef.of(event), window);
            }
        }
        for (CanonicalMeasurement activity : person.measurements()) {
            if (MiacMatch.counts(activity, C3Codes.MIAC_PRACTICES_ORAL_HEALTH)
                    && C3Codes.DENTAL_CBO.matches(activity.cbo())) {
                addPregnancy(units, EventRef.of(activity), window);
            }
        }
        return Tally.decide(1, units);
    }

    private static void addPregnancy(List<Tally.Unit> units, EventRef event, GestationWindow window) {
        if (window.inPregnancy(event.date())) {
            units.add(Tally.Unit.of(event));
        }
    }
}
