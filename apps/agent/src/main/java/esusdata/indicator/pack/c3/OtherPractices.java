package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalMeasurement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

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
     * DUM + 133..139} (AMB-C3-01), in {@code [D, D + 42]} (AMB-C3-17 (i)), without a date
     * (AMB-C3-17 (ii)) or by a CBO outside the lists (AMB-C3-13).
     */
    static PracticeOutcome dtpa(PersonRecords person, GestationWindow window) {
        List<TallyMark> marks = new ArrayList<>();
        for (CanonicalImmunization dose : person.immunizations()) {
            String code = dose.immunobiologicalCode() == null
                    ? ""
                    : dose.immunobiologicalCode().strip();
            if (C3Codes.DTPA_ADULT.equals(code)) {
                TallyMark mark = dtpaMark(EventRef.of(dose), window);
                if (mark != null) {
                    marks.add(mark);
                }
            }
        }
        return Tally.decide(1, marks);
    }

    /**
     * K: an individual dental encounter (MIAOI), or a collective activity (MIAC) with activity
     * 05/06 and practice 02/04 — only one of them being AMB-C3-19 — by a dentist or TSB during the
     * pregnancy.
     */
    static PracticeOutcome dental(PersonRecords person, GestationWindow window) {
        List<TallyMark> marks = new ArrayList<>();
        for (CanonicalCareEvent event : person.dentalCare()) {
            if (C3Codes.DENTAL_CBO.matches(event.cbo())) {
                addPregnancy(marks, EventRef.of(event), null, window);
            }
        }
        for (CanonicalMeasurement activity : person.measurements()) {
            MiacMatch miac = MiacMatch.of(activity, C3Codes.MIAC_PRACTICES_ORAL_HEALTH);
            if (miac.counts() && C3Codes.DENTAL_CBO.matches(activity.cbo())) {
                addPregnancy(marks, EventRef.of(activity), miac.ambiguity(null), window);
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
            return TallyMark.of(dose, Ambiguity.AMB_C3_17);
        }
        if (!date.isBefore(window.end())) {
            return date.isAfter(window.boundaryDay()) ? null : TallyMark.of(dose, Ambiguity.AMB_C3_17);
        }
        long day = window.day(date);
        if (day >= DTPA_CERTAIN_FROM) {
            return TallyMark.of(dose, C3Codes.LISTED_CBO.matches(dose.cbo()) ? null : Ambiguity.AMB_C3_13);
        }
        return day >= DTPA_ORDINAL_FROM ? TallyMark.of(dose, Ambiguity.AMB_C3_01) : null;
    }
}
