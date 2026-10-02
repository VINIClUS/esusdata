package esusdata.indicator.pack.c3;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Practices A, B and I: consultations by médica(o) or enfermeira(o) (Quadro 02, p.5–6). */
final class ConsultationPractices {

    /** "até a 12ª semana": certainly up to IG 11s6d (DUM + 83) under every reading (AMB-C3-01). */
    private static final long EARLY_LAST_DAY = 83;

    /** IG 12s6d (DUM + 90): the last day the ordinal and the completed-weeks readings diverge. */
    private static final long ORDINAL_LAST_DAY = 90;

    private static final int SEVEN = 7;

    private ConsultationPractices() {}

    /** A: the 1ª consultation up to the 12ª week (10 points). */
    static PracticeOutcome early(List<Consultation> consultations, GestationWindow window) {
        List<TallyMark> marks = new ArrayList<>();
        for (Consultation consultation : consultations) {
            LocalDate date = consultation.date();
            if (window.inPregnancy(date) && window.day(date) <= ORDINAL_LAST_DAY) {
                marks.add(TallyMark.of(consultation.event(), earlyAmbiguity(consultation, window)));
            }
        }
        return Tally.decide(1, marks);
    }

    /** B: at least 7 consultations on distinct days during the pregnancy. */
    static PracticeOutcome seven(List<Consultation> consultations, GestationWindow window) {
        List<DayTally.DayItem> items = new ArrayList<>();
        for (Consultation consultation : consultations) {
            GestationWindow.Phase phase = window.phaseOf(consultation.date());
            if (phase.pregnant()) {
                items.add(DayTally.DayItem.of(
                        consultation.event(), phase.inPregnancy(consultation.pregnancyAmbiguity())));
            }
        }
        return Tally.decide(SEVEN, DayTally.marks(items, Ambiguity.AMB_C3_12));
    }

    /** I: at least 1 consultation during the puerperium. */
    static PracticeOutcome puerperal(List<Consultation> consultations, GestationWindow window) {
        List<TallyMark> marks = new ArrayList<>();
        for (Consultation consultation : consultations) {
            GestationWindow.Phase phase = window.phaseOf(consultation.date());
            if (phase.puerperal()) {
                marks.add(TallyMark.of(consultation.event(), phase.inPuerperium(consultation.puerperiumAmbiguity())));
            }
        }
        return Tally.decide(1, marks);
    }

    private static Ambiguity earlyAmbiguity(Consultation consultation, GestationWindow window) {
        Ambiguity own = consultation.pregnancyAmbiguity();
        if (own != null) {
            return own;
        }
        if (consultation.date().isEqual(window.end())) {
            return Ambiguity.AMB_C3_04;
        }
        return window.day(consultation.date()) <= EARLY_LAST_DAY ? null : Ambiguity.AMB_C3_01;
    }
}
