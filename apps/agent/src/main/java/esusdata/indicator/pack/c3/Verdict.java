package esusdata.indicator.pack.c3;

import java.time.LocalDate;
import java.util.List;

/**
 * The cohort decision of one subject: eligible or excluded, with its reason code and reference
 * date. An excluded subject with an {@code ambiguity} is one the ficha does not decide; it makes
 * the result {@code RULE_AMBIGUITY}.
 */
record Verdict(boolean eligible, String reasonCode, LocalDate eventDate, Ambiguity ambiguity) {

    static Verdict eligible(String reasonCode, LocalDate eventDate) {
        return new Verdict(true, reasonCode, eventDate, null);
    }

    static Verdict excluded(String reasonCode, LocalDate eventDate) {
        return new Verdict(false, reasonCode, eventDate, null);
    }

    static Verdict ambiguous(Ambiguity ambiguity, LocalDate eventDate) {
        return new Verdict(false, C3Reasons.ambiguity(ambiguity), eventDate, ambiguity);
    }

    /**
     * The verdict every reading of the dates agrees on ({@code null} = no objection), or AMB-C3-03
     * when the readings disagree.
     */
    static Verdict agreed(List<Verdict> perReading, LocalDate eventDate) {
        Verdict first = perReading.get(0);
        for (Verdict verdict : perReading) {
            boolean same = first == null ? verdict == null : first.equals(verdict);
            if (!same) {
                return ambiguous(Ambiguity.AMB_C3_03, eventDate);
            }
        }
        return first;
    }

    boolean ambiguousSubject() {
        return ambiguity != null;
    }
}
