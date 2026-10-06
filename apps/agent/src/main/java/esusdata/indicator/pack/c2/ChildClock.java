package esusdata.indicator.pack.c2;

import esusdata.indicator.model.AgeAt;
import esusdata.indicator.model.AgeAt.AnniversaryRule;
import java.time.LocalDate;

/**
 * A child's calendar: the ficha's "30º dia de vida" and "até os N meses" become dates here and
 * nowhere else, so every practice counts the same way. The readings are decided (record of
 * decisions C2, AMB-C2-01 and AMB-C2-02): the day of birth is day 0 and day 30 is inside; the
 * anniversary date is inside "até"; a missing anniversary day moves to the next day.
 */
record ChildClock(LocalDate birth) {

    /** The last day inside "até o 30º dia de vida": the day of birth is day 0 (AMB-C2-01). */
    static final int LAST_DAY_OF_FIRST_30 = 30;

    /** "Até (os) dois anos de vida" in civil months. */
    static final long TWO_YEARS_IN_MONTHS = 24;

    /** Day 0 is the day of birth ({@link AgeAt#daysSinceBirth}). */
    long day(LocalDate date) {
        return AgeAt.daysSinceBirth(birth, date);
    }

    boolean bornBy(LocalDate date) {
        return day(date) >= 0;
    }

    /** "Até o 30º dia de vida" / "até os primeiros 30 (trinta) dias de vida". */
    boolean withinFirst30Days(LocalDate date) {
        long day = day(date);
        return day >= 0 && day <= LAST_DAY_OF_FIRST_30;
    }

    /** "Até os N meses de vida": up to and including the anniversary (AMB-C2-02). */
    boolean upToMonths(LocalDate date, long months) {
        return bornBy(date) && !date.isAfter(anniversary(months));
    }

    /** "Não … antes dos N meses": on or after the anniversary. */
    boolean fromMonths(LocalDate date, long months) {
        return !date.isBefore(anniversary(months));
    }

    /** The anniversary after {@code months}; a day the month lacks moves to the next day (AMB-C2-02). */
    LocalDate anniversary(long months) {
        return AgeAt.anniversaryMonths(birth, months, AnniversaryRule.NEXT_DAY);
    }
}
