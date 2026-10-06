package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalHomeVisit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Practices E and J: home visits by ACS/TACS (Quadro 05, p.7) with some "motivo de visita"
 * filled (Quadro 05; 24 e, p.3), any outcome (CT-C3-34), by CBO 5151-05 or 3222-55 (AMB-C3-16
 * (iii)). E counts visits on distinct days strictly after the first consultation of the prenatal
 * care and up to D; J, visits in the puerperium.
 */
final class VisitPractices {

    private static final int THREE = 3;

    private VisitPractices() {}

    /**
     * E: at least 3 visits on distinct days strictly after the date of the first consultation of
     * the pregnancy (AMB-C3-16 (i)) and up to D; a visit in the puerperium belongs to J (ii).
     */
    static PracticeOutcome afterFirstConsultation(
            PersonRecords person, List<Consultation> consultations, GestationWindow window) {
        LocalDate first = firstConsultation(consultations, window);
        if (first == null) {
            return PracticeOutcome.NOT_MET;
        }
        List<Tally.Unit> units = new ArrayList<>();
        for (CanonicalHomeVisit visit : person.visits()) {
            EventRef event = EventRef.of(visit);
            if (counts(visit) && event.date().isAfter(first) && window.inPregnancy(event.date())) {
                units.add(Tally.Unit.of(event));
            }
        }
        return Tally.decide(THREE, Tally.distinctDays(units));
    }

    /** The date of the first consultation of the pregnancy (AMB-C3-11), or {@code null}. */
    private static LocalDate firstConsultation(List<Consultation> consultations, GestationWindow window) {
        for (Consultation consultation : consultations) {
            if (consultation.pregnancy() && window.inPregnancy(consultation.date())) {
                return consultation.date();
            }
        }
        return null;
    }

    /** A dated visit by an accepted CBO with at least one reason recorded. */
    private static boolean counts(CanonicalHomeVisit visit) {
        return C3Dates.parse(visit.visitDate()) != null
                && C3Codes.VISIT_CBO.matches(visit.cbo())
                && visit.reasonCodes().stream().anyMatch(r -> r != null && !r.isBlank());
    }

    /** J: at least 1 visit during the puerperium. */
    static PracticeOutcome puerperal(PersonRecords person, GestationWindow window) {
        List<Tally.Unit> units = new ArrayList<>();
        for (CanonicalHomeVisit visit : person.visits()) {
            EventRef event = EventRef.of(visit);
            if (counts(visit) && window.inPuerperium(event.date())) {
                units.add(Tally.Unit.of(event));
            }
        }
        return Tally.decide(1, units);
    }
}
