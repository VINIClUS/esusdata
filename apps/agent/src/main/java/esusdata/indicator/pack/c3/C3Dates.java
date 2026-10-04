package esusdata.indicator.pack.c3;

import java.time.LocalDate;

/** ISO dates of the canonical records (§1.7.2); a missing date stays {@code null}, never today. */
final class C3Dates {

    private C3Dates() {}

    static LocalDate parse(String text) {
        return text == null || text.isBlank() ? null : LocalDate.parse(text.strip());
    }

    /** {@code from <= date <= through}; a {@code null} date is in no window. */
    static boolean within(LocalDate date, LocalDate from, LocalDate through) {
        return date != null && !date.isBefore(from) && !date.isAfter(through);
    }

    static String text(LocalDate date) {
        return date == null ? null : date.toString();
    }
}
