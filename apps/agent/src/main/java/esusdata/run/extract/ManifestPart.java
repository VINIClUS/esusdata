package esusdata.run.extract;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * One capability inside a canonical v2 extract (ADR 0030). Every line of the data file names its
 * part by {@code index} and its record kind; the reader checks both, and that the line's scope date
 * lies in {@code [periodStart, periodEndExclusive)}.
 *
 * @param params the binds the query ran with (code lists and dates as canonical strings), so a
 *     replay can prove it read the same thing; {@code paramsChecksum} is their SHA-256, both as
 *     {@link ManifestChecksums} defines them
 */
public record ManifestPart(
        int index,
        String capability,
        String adapterVersion,
        String queryChecksum,
        String recordKind,
        String periodStart,
        String periodEndExclusive,
        SortedMap<String, List<String>> params,
        String paramsChecksum,
        long rowCount) {
    public ManifestPart {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(recordKind, "recordKind");
        if (index < 0 || rowCount < 0) {
            throw new IllegalArgumentException("part index and row count must not be negative");
        }
        SortedMap<String, List<String>> copy = new TreeMap<>();
        if (params != null) {
            params.forEach((name, values) -> copy.put(name, List.copyOf(values)));
        }
        params = Collections.unmodifiableSortedMap(copy);
    }
}
