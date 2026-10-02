package esusdata.run.acquisition;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * One capability of a canonical v2 acquisition (ADR 0030): which frozen query, the version and
 * checksum the Java side expects for it, the record kind it writes, its window and the values of its
 * declared binds. Built by the run pipeline from a rule's {@code PartRequirement} and the packaged
 * {@code CapabilityContract}.
 */
public record AcquisitionPart(
        String capability,
        String adapterVersion,
        String queryChecksum,
        String recordKind,
        LocalDate periodStart,
        LocalDate periodEndExclusive,
        SortedMap<String, List<String>> arrayParams,
        SortedMap<String, LocalDate> dateParams) {
    public AcquisitionPart {
        Objects.requireNonNull(capability, "capability");
        Objects.requireNonNull(adapterVersion, "adapterVersion");
        Objects.requireNonNull(queryChecksum, "queryChecksum");
        Objects.requireNonNull(recordKind, "recordKind");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(periodEndExclusive, "periodEndExclusive");
        SortedMap<String, List<String>> arrays = new TreeMap<>();
        arrayParams.forEach((name, values) -> arrays.put(name, List.copyOf(values)));
        arrayParams = Collections.unmodifiableSortedMap(arrays);
        dateParams = Collections.unmodifiableSortedMap(new TreeMap<>(dateParams));
    }
}
