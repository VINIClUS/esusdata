package esusdata.indicator.pack.c3;

import java.time.LocalDate;

/** The cohort decision of one subject: eligible or excluded, with its reason code and reference date. */
record Verdict(boolean eligible, String reasonCode, LocalDate eventDate) {

    static Verdict eligible(String reasonCode, LocalDate eventDate) {
        return new Verdict(true, reasonCode, eventDate);
    }

    static Verdict excluded(String reasonCode, LocalDate eventDate) {
        return new Verdict(false, reasonCode, eventDate);
    }
}
