package esusdata.indicator.model;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * One capability a rule needs read, with its own window and the parameters its frozen query binds
 * (ADR 0030). Code lists are parameters owned by the pack (bound as {@code text[]}), so the query
 * text — and its checksum in the compatibility matrix — never changes when a pack's codes do, and
 * the rule is not duplicated between SQL and Java (§1.6).
 *
 * @param periodStart first day read, inclusive
 * @param periodEndExclusive first day not read
 * @param arrayParams named {@code text[]} binds (code lists), sorted by name
 * @param dateParams named {@code date} binds (e.g. a birth-date range), sorted by name
 */
public record PartRequirement(
        String capability,
        LocalDate periodStart,
        LocalDate periodEndExclusive,
        SortedMap<String, List<String>> arrayParams,
        SortedMap<String, LocalDate> dateParams) {

    /** The date binds every person-scoped capability takes: people born in a window. */
    public static final String BIRTH_DATE_FROM = "birth_date_from";

    public static final String BIRTH_DATE_TO = "birth_date_to";

    public PartRequirement {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEndExclusive, "periodEndExclusive");
        if (!periodStart.isBefore(periodEndExclusive)) {
            throw new IllegalArgumentException(
                    capability + ": period [" + periodStart + ", " + periodEndExclusive + ") is empty");
        }
        SortedMap<String, List<String>> arrays = new TreeMap<>();
        arrayParams.forEach((name, values) -> arrays.put(name, List.copyOf(values)));
        arrayParams = Collections.unmodifiableSortedMap(arrays);
        dateParams = Collections.unmodifiableSortedMap(new TreeMap<>(dateParams));
    }

    /**
     * A person-scoped part (ADR 0030): records in {@code period} of people born inside {@code
     * births} (inclusive start, exclusive end), plus the pack's code lists.
     */
    public static PartRequirement personScoped(
            String capability, DateWindow period, DateWindow births, SortedMap<String, List<String>> codes) {
        SortedMap<String, LocalDate> dates = new TreeMap<>();
        dates.put(BIRTH_DATE_FROM, births.start());
        dates.put(BIRTH_DATE_TO, births.endExclusive().minusDays(1));
        return new PartRequirement(capability, period.start(), period.endExclusive(), codes, dates);
    }

    /** A part with no parameters beyond municipality and period. */
    public static PartRequirement of(String capability, LocalDate periodStart, LocalDate periodEndExclusive) {
        return new PartRequirement(capability, periodStart, periodEndExclusive, new TreeMap<>(), new TreeMap<>());
    }
}
