package esusdata.indicator.model;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A quadrimestre of the federal calendar (§1.7.2, §2.1, MET-05): Q1 is January–April, Q2 May–August,
 * Q3 September–December — never a civil quarter. Written {@code 2026-Q2}.
 */
public record Quadrimestre(int year, int index) implements Comparable<Quadrimestre> {

    private static final Pattern TEXT = Pattern.compile("(\\d{4})-Q([1-3])");
    private static final int MONTHS = 4;

    public Quadrimestre {
        if (index < 1 || index > 3) {
            throw new IllegalArgumentException("quadrimestre index must be 1, 2 or 3: " + index);
        }
    }

    public static Quadrimestre parse(String text) {
        Matcher matcher = TEXT.matcher(text == null ? "" : text);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("quadrimestre must look like 2026-Q2: " + text);
        }
        return new Quadrimestre(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
    }

    /** The quadrimestre a competência belongs to. */
    public static Quadrimestre of(YearMonth month) {
        return new Quadrimestre(month.getYear(), (month.getMonthValue() - 1) / MONTHS + 1);
    }

    public YearMonth firstMonth() {
        return YearMonth.of(year, (index - 1) * MONTHS + 1);
    }

    public YearMonth lastMonth() {
        return firstMonth().plusMonths(MONTHS - 1);
    }

    /** The four competências, in order. */
    public List<YearMonth> months() {
        List<YearMonth> months = new ArrayList<>(MONTHS);
        for (int i = 0; i < MONTHS; i++) {
            months.add(firstMonth().plusMonths(i));
        }
        return List.copyOf(months);
    }

    /** The cutoff of the quadrimestre: 30/04, 31/08 or 31/12 (MET-05). */
    public LocalDate cutoff() {
        return lastMonth().atEndOfMonth();
    }

    @Override
    public int compareTo(Quadrimestre other) {
        return year == other.year ? Integer.compare(index, other.index) : Integer.compare(year, other.year);
    }

    @Override
    public String toString() {
        return year + "-Q" + index;
    }
}
