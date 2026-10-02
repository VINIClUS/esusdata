package esusdata.indicator.model;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Ages and anniversaries on the calendar (§1.7.2, ENG-27). When the target month has no day
 * matching the start (born 29/02, or counting a month from 31/01) the result depends on a
 * convention the fichas do not state, so every method takes it explicitly — a pack records the one
 * it uses as an ambiguity of its ficha instead of inheriting one silently.
 */
public final class AgeAt {

    private AgeAt() {}

    /** How a period that lands on a day the month does not have is completed. */
    public enum AnniversaryRule {
        /** {@code java.time} and PostgreSQL interval arithmetic: 29/02 + 1 ano = 28/02. */
        CLAMP_TO_MONTH_END,
        /**
         * Lei nº 810/1949, art. 3º: "quando no ano ou mês do vencimento não houver o dia
         * correspondente ao do início do prazo, este findará no primeiro dia subsequente" — 29/02 +
         * 1 ano = 01/03.
         */
        NEXT_DAY
    }

    /** The date {@code start} completes {@code years} years. */
    public static LocalDate anniversaryYears(LocalDate start, long years, AnniversaryRule rule) {
        return adjust(start, start.plusYears(years), rule);
    }

    /** The date {@code start} completes {@code months} months. */
    public static LocalDate anniversaryMonths(LocalDate start, long months, AnniversaryRule rule) {
        return adjust(start, start.plusMonths(months), rule);
    }

    /** Completed years on {@code on}: the largest n whose anniversary is not after {@code on}. */
    public static long completedYears(LocalDate birth, LocalDate on, AnniversaryRule rule) {
        long years = Math.max(0, ChronoUnit.YEARS.between(birth, on));
        while (!anniversaryYears(birth, years + 1, rule).isAfter(on)) {
            years++;
        }
        while (years > 0 && anniversaryYears(birth, years, rule).isAfter(on)) {
            years--;
        }
        return years;
    }

    /** Completed months on {@code on}: the largest n whose anniversary is not after {@code on}. */
    public static long completedMonths(LocalDate birth, LocalDate on, AnniversaryRule rule) {
        long months = Math.max(0, ChronoUnit.MONTHS.between(birth, on));
        while (!anniversaryMonths(birth, months + 1, rule).isAfter(on)) {
            months++;
        }
        while (months > 0 && anniversaryMonths(birth, months, rule).isAfter(on)) {
            months--;
        }
        return months;
    }

    /**
     * Days elapsed since birth: 0 on the day of birth. Whether that day is the "1º dia de vida" is
     * the ficha's call.
     */
    public static long daysSinceBirth(LocalDate birth, LocalDate on) {
        return ChronoUnit.DAYS.between(birth, on);
    }

    private static LocalDate adjust(LocalDate start, LocalDate target, AnniversaryRule rule) {
        boolean clamped = target.getDayOfMonth() != start.getDayOfMonth();
        return clamped && rule == AnniversaryRule.NEXT_DAY ? target.plusDays(1) : target;
    }
}
