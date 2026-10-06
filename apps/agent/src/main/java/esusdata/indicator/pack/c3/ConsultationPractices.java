package esusdata.indicator.pack.c3;

import java.util.ArrayList;
import java.util.List;

/** Practices A, B and I: consultations by médica(o) or enfermeira(o) (Quadro 02, p.5–6). */
final class ConsultationPractices {

    /** "até a 12ª semana": up to IG 12s6d, DUM + 90, completed weeks (AMB-C3-01). */
    private static final long EARLY_LAST_DAY = 90;

    private static final int SEVEN = 7;

    private ConsultationPractices() {}

    /** A: the 1ª consultation up to the 12ª week (10 points). */
    static PracticeOutcome early(List<Consultation> consultations, GestationWindow window) {
        List<Tally.Unit> units = new ArrayList<>();
        for (Consultation consultation : consultations) {
            if (consultation.pregnancy()
                    && window.inPregnancy(consultation.date())
                    && window.day(consultation.date()) <= EARLY_LAST_DAY) {
                units.add(Tally.Unit.of(consultation.event()));
            }
        }
        return Tally.decide(1, units);
    }

    /** B: at least 7 consultations on distinct days during the pregnancy. */
    static PracticeOutcome seven(List<Consultation> consultations, GestationWindow window) {
        List<Tally.Unit> units = new ArrayList<>();
        for (Consultation consultation : consultations) {
            if (consultation.pregnancy() && window.inPregnancy(consultation.date())) {
                units.add(Tally.Unit.of(consultation.event()));
            }
        }
        return Tally.decide(SEVEN, Tally.distinctDays(units));
    }

    /** I: at least 1 consultation during the puerperium. */
    static PracticeOutcome puerperal(List<Consultation> consultations, GestationWindow window) {
        List<Tally.Unit> units = new ArrayList<>();
        for (Consultation consultation : consultations) {
            if (consultation.puerperium() && window.inPuerperium(consultation.date())) {
                units.add(Tally.Unit.of(consultation.event()));
            }
        }
        return Tally.decide(1, units);
    }
}
