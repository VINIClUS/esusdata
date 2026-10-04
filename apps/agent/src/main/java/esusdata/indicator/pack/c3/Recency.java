package esusdata.indicator.pack.c3;

import esusdata.indicator.model.SourceRef;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.function.Function;

/**
 * "The most recent version" of a versioned record (registration, team): by its date, a missing date
 * oldest, then by source record id — numerically when both ids are digits, so that {@code 10}
 * comes after {@code 9}.
 */
final class Recency {

    private static final Comparator<String> RECORD_ID = Recency::compareIds;

    private Recency() {}

    static <T> Comparator<T> of(Function<T, String> date, Function<T, SourceRef> ref) {
        return Comparator.comparing(
                        (T t) -> C3Dates.parse(date.apply(t)),
                        Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()))
                .thenComparing(t -> recordId(ref.apply(t)), RECORD_ID);
    }

    static int compareIds(String a, String b) {
        boolean numeric = a.chars().allMatch(Character::isDigit) && b.chars().allMatch(Character::isDigit);
        if (numeric && !a.isEmpty() && !b.isEmpty()) {
            String x = a.replaceFirst("^0+(?=.)", "");
            String y = b.replaceFirst("^0+(?=.)", "");
            int byLength = Integer.compare(x.length(), y.length());
            return byLength == 0 ? x.compareTo(y) : byLength;
        }
        return a.compareTo(b);
    }

    private static String recordId(SourceRef ref) {
        return ref == null || ref.recordId() == null ? "" : ref.recordId();
    }
}
