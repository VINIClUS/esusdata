package esusdata.indicator.pack.c2;

import esusdata.indicator.model.AgeAt;
import esusdata.indicator.model.AgeAt.AnniversaryRule;
import java.time.LocalDate;
import java.util.Set;

/**
 * A child's calendar under one combination of readings: the ficha's "30º dia de vida" and
 * "até os N meses" become dates here and nowhere else, so every practice counts the same way.
 */
record ChildClock(LocalDate birth) {

    /** The last day inside "até o 30º dia de vida" when the birth day is the 1º dia (AMB-C2-01). */
    static final int LAST_DAY_BOTH_READINGS = 29;

    /** Day 0 is the day of birth ({@link AgeAt#daysSinceBirth}). */
    long day(LocalDate date) {
        return AgeAt.daysSinceBirth(birth, date);
    }

    boolean bornBy(LocalDate date) {
        return day(date) >= 0;
    }

    /** "Até o 30º dia de vida" / "até os primeiros 30 (trinta) dias de vida". */
    boolean withinFirst30Days(LocalDate date, Set<Reading> readings) {
        long day = day(date);
        int last = readings.contains(Reading.DAY_30_INSIDE) ? LAST_DAY_BOTH_READINGS + 1 : LAST_DAY_BOTH_READINGS;
        return day >= 0 && day <= last;
    }

    /** "Até os N meses de vida": before the anniversary, or on it when that reading holds. */
    boolean upToMonths(LocalDate date, long months, Set<Reading> readings) {
        LocalDate anniversary = anniversary(months, readings);
        boolean onAnniversary = date.isEqual(anniversary) && readings.contains(Reading.ANNIVERSARY_DAY_INSIDE);
        return bornBy(date) && (date.isBefore(anniversary) || onAnniversary);
    }

    /** "Não … antes dos N meses": on or after the anniversary. */
    boolean fromMonths(LocalDate date, long months, Set<Reading> readings) {
        return !date.isBefore(anniversary(months, readings));
    }

    LocalDate anniversary(long months, Set<Reading> readings) {
        AnniversaryRule rule = readings.contains(Reading.ANNIVERSARY_NEXT_DAY)
                ? AnniversaryRule.NEXT_DAY
                : AnniversaryRule.CLAMP_TO_MONTH_END;
        return AgeAt.anniversaryMonths(birth, months, rule);
    }
}
