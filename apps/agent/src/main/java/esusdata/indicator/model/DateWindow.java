package esusdata.indicator.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Objects;

/**
 * A half-open date interval {@code [start, endExclusive)} (§1.7.2). Fichas count months as civil
 * months, never as 30/180/365 days, so the factories here move by {@link YearMonth}.
 */
public record DateWindow(LocalDate start, LocalDate endExclusive) {
    public DateWindow {
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(endExclusive, "endExclusive");
        if (endExclusive.isBefore(start)) {
            throw new IllegalArgumentException("window [" + start + ", " + endExclusive + ") ends before it starts");
        }
    }

    /** The {@code months} civil months that end with {@code competencia}, inclusive. */
    public static DateWindow lastCivilMonths(YearMonth competencia, int months) {
        if (months <= 0) {
            throw new IllegalArgumentException("months must be positive: " + months);
        }
        return new DateWindow(
                competencia.minusMonths(months - 1L).atDay(1),
                competencia.plusMonths(1).atDay(1));
    }

    /** {@code [from, through]} with an inclusive last day. */
    public static DateWindow inclusive(LocalDate from, LocalDate through) {
        return new DateWindow(from, through.plusDays(1));
    }

    public boolean contains(LocalDate date) {
        return !date.isBefore(start) && date.isBefore(endExclusive);
    }

    /** The smallest window covering both. */
    public DateWindow span(DateWindow other) {
        LocalDate first = start.isBefore(other.start) ? start : other.start;
        LocalDate last = endExclusive.isAfter(other.endExclusive) ? endExclusive : other.endExclusive;
        return new DateWindow(first, last);
    }
}
