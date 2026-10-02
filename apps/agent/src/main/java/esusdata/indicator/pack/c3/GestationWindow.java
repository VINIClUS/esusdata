package esusdata.indicator.pack.c3;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * One reading of a pregnancy's dates (item 17, 4.1): the DUM and the end D used — the recorded
 * outcome, or DUM + 294 days when there is none (MET-21). The day D itself and the day D + 42 are
 * the boundaries the ficha leaves open (AMB-C3-04).
 */
record GestationWindow(LocalDate dum, LocalDate end, boolean recordedOutcome) {

    /** "42 semanas máximas de gestação (total de 294 dias)" (4.1, p.5). */
    static final int MAX_PREGNANCY_DAYS = 294;

    /** "o total de 42 dias após o término da gestação" (item 17, p.2). */
    static final int PUERPERIUM_DAYS = 42;

    /** Where a date falls in this reading. */
    enum Phase {
        BEFORE,
        /** {@code DUM <= x < D}: certainly pregnancy. */
        PREGNANCY,
        /** {@code x == D}: pregnancy or puerperium (AMB-C3-04). */
        END_DAY,
        /** {@code D < x <= D + 41}: certainly puerperium. */
        PUERPERIUM,
        /** {@code x == D + 42}: inside or outside the puerperium (AMB-C3-04). */
        PUERPERIUM_LAST_DAY,
        AFTER;

        /** Pregnancy with its boundary day: {@code DUM <= x <= D}. */
        boolean pregnant() {
            return this == PREGNANCY || this == END_DAY;
        }

        /** Puerperium with its boundary days: {@code D <= x <= D + 42}. */
        boolean puerperal() {
            return this == END_DAY || this == PUERPERIUM || this == PUERPERIUM_LAST_DAY;
        }

        /** In a pregnancy window, a record on the day D counts only under one reading (AMB-C3-04). */
        Ambiguity inPregnancy(Ambiguity own) {
            return this == PREGNANCY ? own : Ambiguity.AMB_C3_04;
        }

        /** In a puerperium window, records on D and on D + 42 count only under one reading (AMB-C3-04). */
        Ambiguity inPuerperium(Ambiguity own) {
            return this == PUERPERIUM ? own : Ambiguity.AMB_C3_04;
        }
    }

    static GestationWindow substitute(LocalDate dum) {
        return new GestationWindow(dum, dum.plusDays(MAX_PREGNANCY_DAYS), false);
    }

    static GestationWindow recorded(LocalDate dum, LocalDate outcome) {
        return new GestationWindow(dum, outcome, true);
    }

    Phase phaseOf(LocalDate date) {
        if (date.isBefore(dum)) {
            return Phase.BEFORE;
        }
        if (date.isBefore(end)) {
            return Phase.PREGNANCY;
        }
        long afterEnd = ChronoUnit.DAYS.between(end, date);
        if (afterEnd == 0) {
            return Phase.END_DAY;
        }
        if (afterEnd < PUERPERIUM_DAYS) {
            return Phase.PUERPERIUM;
        }
        return afterEnd == PUERPERIUM_DAYS ? Phase.PUERPERIUM_LAST_DAY : Phase.AFTER;
    }

    /** Days since the DUM: 0 on the DUM itself. */
    long day(LocalDate date) {
        return ChronoUnit.DAYS.between(dum, date);
    }

    /** {@code DUM <= x <= D}: the pregnancy with its boundary day. */
    boolean inPregnancy(LocalDate date) {
        return C3Dates.within(date, dum, end);
    }

    /** The last day of the certain puerperium, D + 41. */
    LocalDate lastCertainDay() {
        return end.plusDays(PUERPERIUM_DAYS - 1L);
    }

    /** The boundary day D + 42 (AMB-C3-04). */
    LocalDate boundaryDay() {
        return end.plusDays(PUERPERIUM_DAYS);
    }
}
