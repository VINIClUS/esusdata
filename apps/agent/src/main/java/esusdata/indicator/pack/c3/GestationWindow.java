package esusdata.indicator.pack.c3;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * The dates of one pregnancy (item 17, 4.1): the DUM and the end D used — the recorded outcome,
 * else the resolution of the pregnancy condition in the LPC (gap L2), else DUM + 294 days
 * (MET-21). The pregnancy is {@code [DUM, D]}, D inclusive; the puerperium is {@code (D, D + 42]},
 * D + 42 inclusive (AMB-C3-04).
 */
record GestationWindow(LocalDate dum, LocalDate end, EndSource endSource) {

    /** "42 semanas máximas de gestação (total de 294 dias)" (4.1, p.5). */
    static final int MAX_PREGNANCY_DAYS = 294;

    /** "o total de 42 dias após o término da gestação" (item 17, p.2). */
    static final int PUERPERIUM_DAYS = 42;

    /** Where the end D comes from, in the order of precedence. */
    enum EndSource {
        /** A recorded "Data de desfecho da gestação" (item 17). */
        RECORDED_OUTCOME,
        /** The resolution date of the pregnancy condition in the LPC (PEC manual; gap L2). */
        LPC_RESOLUTION,
        /** DUM + 294 days, without any outcome (item 17, 4.1). */
        SUBSTITUTE_294
    }

    static GestationWindow substitute(LocalDate dum) {
        return new GestationWindow(dum, dum.plusDays(MAX_PREGNANCY_DAYS), EndSource.SUBSTITUTE_294);
    }

    /** Days since the DUM: 0 on the DUM itself. */
    long day(LocalDate date) {
        return ChronoUnit.DAYS.between(dum, date);
    }

    /** {@code DUM <= x <= D}; a {@code null} date is in no window. */
    boolean inPregnancy(LocalDate date) {
        return C3Dates.within(date, dum, end);
    }

    /** {@code D < x <= D + 42}; a {@code null} date is in no window. */
    boolean inPuerperium(LocalDate date) {
        return C3Dates.within(date, end.plusDays(1), lastDay());
    }

    /** The last day of the puerperium, D + 42 (the 42º dia de puerpério, NT 8/2026). */
    LocalDate lastDay() {
        return end.plusDays(PUERPERIUM_DAYS);
    }
}
