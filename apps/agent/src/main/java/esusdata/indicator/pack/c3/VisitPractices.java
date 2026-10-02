package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalHomeVisit;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Practices E and J: home visits by ACS/TACS (Quadro 05, p.7), any reason and any outcome
 * (CT-C3-34). E counts visits after the 1ª consultation of the prenatal care; J, visits in the
 * puerperium.
 */
final class VisitPractices {

    private static final int THREE = 3;

    private VisitPractices() {}

    /**
     * E: at least 3 visits on distinct days strictly after the first certain consultation and
     * during the pregnancy. A visit on the day of that consultation, between the first undecided
     * and the first certain consultation, on the day D or in the puerperium counts only as
     * "talvez" (AMB-C3-16 (i), (ii)); a visit before any consultation does not count.
     */
    static PracticeOutcome afterFirstConsultation(
            PersonRecords person, List<Consultation> consultations, GestationWindow window) {
        FirstConsultation first = FirstConsultation.of(consultations, window);
        if (first.any() == null) {
            return PracticeOutcome.NOT_MET;
        }
        List<DayTally.DayItem> items = new ArrayList<>();
        for (CanonicalHomeVisit visit : person.visits()) {
            EventRef event = EventRef.of(visit);
            if (event.date() != null && !event.date().isBefore(first.any()) && CboRule.VISIT.accepts(visit.cbo())) {
                GestationWindow.Phase phase = window.phaseOf(event.date());
                if (phase == GestationWindow.Phase.PREGNANCY) {
                    items.add(DayTally.DayItem.of(event, first.ambiguityOf(event, visit.cbo())));
                } else if (phase.puerperal()) {
                    items.add(DayTally.DayItem.of(event, Ambiguity.AMB_C3_16));
                }
            }
        }
        return Tally.decide(THREE, DayTally.marks(items, Ambiguity.AMB_C3_16));
    }

    /**
     * The first consultation of the pregnancy, any (certain or not) and certain; {@code null} when
     * there is none.
     */
    private record FirstConsultation(LocalDate any, LocalDate certain) {

        static FirstConsultation of(List<Consultation> consultations, GestationWindow window) {
            LocalDate any = null;
            LocalDate certain = null;
            for (Consultation consultation : consultations) {
                GestationWindow.Phase phase = window.phaseOf(consultation.date());
                if (phase.pregnant() && any == null) {
                    any = consultation.date();
                }
                boolean sure = phase == GestationWindow.Phase.PREGNANCY && consultation.pregnancyAmbiguity() == null;
                if (sure && certain == null) {
                    certain = consultation.date();
                }
            }
            return new FirstConsultation(any, certain);
        }

        /** A pregnancy visit counts for sure only strictly after the first certain consultation. */
        Ambiguity ambiguityOf(EventRef visit, String cbo) {
            boolean after = certain != null && visit.date().isAfter(certain);
            return after ? CboRule.VISIT.ambiguityOf(cbo) : Ambiguity.AMB_C3_16;
        }
    }

    /** J: at least 1 visit during the puerperium. */
    static PracticeOutcome puerperal(PersonRecords person, GestationWindow window) {
        List<TallyMark> marks = new ArrayList<>();
        for (CanonicalHomeVisit visit : person.visits()) {
            EventRef event = EventRef.of(visit);
            if (event.date() != null && CboRule.VISIT.accepts(visit.cbo())) {
                GestationWindow.Phase phase = window.phaseOf(event.date());
                if (phase.puerperal()) {
                    marks.add(TallyMark.of(event, phase.inPuerperium(CboRule.VISIT.ambiguityOf(visit.cbo()))));
                }
            }
        }
        return Tally.decide(1, marks);
    }
}
