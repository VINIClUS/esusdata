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

    /** "a partir da 20ª semana": certainly from IG 20s0d (DUM + 140) under every reading. */
    private static final long DTPA_CERTAIN_FROM = 140;

    /** IG 19s0d (DUM + 133): the first day the ordinal reading already accepts (AMB-C3-01). */
    private static final long DTPA_ORDINAL_FROM = 133;

    private OtherPractices() {}

    /**
     * F: a dose of "57" is certain in {@code [DUM + 140, D)} by a listed CBO; "talvez" in {@code
     * DUM + 133..139} (AMB-C3-01), in {@code [D, D + 42]} from DUM + 133 on (AMB-C3-17 (i)) or by
     * a CBO outside the lists (AMB-C3-13). Before DUM + 133 it never counts, even after an early D.
     * The application date is the capability's scope column, never missing (EMENDA 1).
     */
    static PracticeOutcome dtpa(PersonRecords person, GestationWindow window) {
        SortedMap<LocalDate, TallyMark> byDate = new TreeMap<>();
        for (CanonicalImmunization dose : person.immunizations()) {
            TallyMark mark = C3Codes.DTPA_ADULT.equals(C3Codes.token(dose.immunobiologicalCode()))
                    ? dtpaMark(EventRef.of(dose), window)
                    : null;
            if (mark == null) {
                continue;
            }
            byDate.merge(mark.events().get(0).date(), mark, OtherPractices::sameDose);
        }
        return Tally.decide(1, List.copyOf(byDate.values()));
    }

    /**
     * The same dose (person, "57", application date) from the MIV and from a transcription is one
     * dose (MET-32): the certain record, else the first in evidence order.
     */
    private static TallyMark sameDose(TallyMark a, TallyMark b) {
        if (a.certain() != b.certain()) {
            return a.certain() ? a : b;
        }
        return EventRef.ORDER.compare(a.events().get(0), b.events().get(0)) <= 0 ? a : b;
    }

    /**
     * K: an individual dental encounter (MIAOI), or a collective activity (MIAC) with activity
     * 05/06 and practice flúor/escovação (LEDI 2, 9) — only one of them being AMB-C3-19 — by a
     * dentist or TSB during the pregnancy; another occupation of the family 3224 is AMB-C3-20.
     */
    static PracticeOutcome dental(PersonRecords person, GestationWindow window) {
        List<TallyMark> marks = new ArrayList<>();
        for (CanonicalCareEvent event : person.dentalCare()) {
            if (CboRule.DENTAL.accepts(event.cbo())) {
                addPregnancy(marks, EventRef.of(event), CboRule.DENTAL.ambiguityOf(event.cbo()), window);
            }
        }
        for (CanonicalMeasurement activity : person.measurements()) {
            MiacMatch miac = MiacMatch.of(activity, C3Codes.MIAC_PRACTICES_ORAL_HEALTH);
            if (miac.counts() && CboRule.DENTAL.accepts(activity.cbo())) {
                Ambiguity byCbo = CboRule.DENTAL.ambiguityOf(activity.cbo());
                addPregnancy(marks, EventRef.of(activity), miac.ambiguity(byCbo), window);
            }
        }
        return Tally.decide(1, marks);
    }

    private static void addPregnancy(
            List<TallyMark> marks, EventRef event, Ambiguity ambiguity, GestationWindow window) {
        if (window.inPregnancy(event.date())) {
            marks.add(TallyMark.of(event, window.phaseOf(event.date()).inPregnancy(ambiguity)));
        }
    }

    /** The dose's mark, or {@code null} when it falls outside every reading of the window. */
    private static TallyMark dtpaMark(EventRef dose, GestationWindow window) {
        LocalDate date = dose.date();
        if (date == null) {
            return null; // guard only: the capability never returns a dose without its date
        }
        long day = window.day(date);
        if (day < DTPA_ORDINAL_FROM) {
            return null;
        }
        if (!date.isBefore(window.end())) {
            return date.isAfter(window.boundaryDay()) ? null : TallyMark.of(dose, Ambiguity.AMB_C3_17);
        }
        if (day >= DTPA_CERTAIN_FROM) {
            return TallyMark.of(dose, C3Codes.LISTED_CBO.matches(dose.cbo()) ? null : Ambiguity.AMB_C3_13);
        }
        return TallyMark.of(dose, Ambiguity.AMB_C3_01);
    }
}
