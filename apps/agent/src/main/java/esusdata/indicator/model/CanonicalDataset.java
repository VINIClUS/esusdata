package esusdata.indicator.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The canonical records one run read, by kind, with the window each capability was read for (ADR
 * 0030). A v1 extract (C1) carries only {@link #encounters()}; a v2 extract carries any of the
 * {@link RecordKind}s. Rules receive it already validated: every record is inside the authorized
 * municipality and inside its capability's window.
 */
public final class CanonicalDataset {

    private final List<CanonicalEncounter> encounters;
    private final Map<RecordKind, List<Record>> records;
    private final SortedMap<String, DateWindow> windows;

    private CanonicalDataset(
            List<CanonicalEncounter> encounters,
            Map<RecordKind, List<Record>> records,
            SortedMap<String, DateWindow> windows) {
        this.encounters = List.copyOf(encounters);
        Map<RecordKind, List<Record>> copy = new EnumMap<>(RecordKind.class);
        records.forEach((kind, list) -> copy.put(kind, List.copyOf(list)));
        this.records = Collections.unmodifiableMap(copy);
        this.windows = Collections.unmodifiableSortedMap(new TreeMap<>(windows));
    }

    /** C1's canonical v1 extract: encounters of one capability and window. */
    public static CanonicalDataset ofEncounters(
            String capability, DateWindow window, List<CanonicalEncounter> encounters) {
        SortedMap<String, DateWindow> windows = new TreeMap<>();
        windows.put(capability, window);
        return new CanonicalDataset(encounters, new EnumMap<>(RecordKind.class), windows);
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<CanonicalEncounter> encounters() {
        return encounters;
    }

    public List<CanonicalPerson> persons() {
        return of(RecordKind.PERSON, CanonicalPerson.class);
    }

    public List<CanonicalRegistration> registrations() {
        return of(RecordKind.REGISTRATION, CanonicalRegistration.class);
    }

    public List<CanonicalTeam> teams() {
        return of(RecordKind.TEAM, CanonicalTeam.class);
    }

    public List<CanonicalCareEvent> careEvents() {
        return of(RecordKind.CARE_EVENT, CanonicalCareEvent.class);
    }

    public List<CanonicalProcedureEvent> procedureEvents() {
        return of(RecordKind.PROCEDURE_EVENT, CanonicalProcedureEvent.class);
    }

    public List<CanonicalHomeVisit> homeVisits() {
        return of(RecordKind.HOME_VISIT, CanonicalHomeVisit.class);
    }

    public List<CanonicalImmunization> immunizations() {
        return of(RecordKind.IMMUNIZATION, CanonicalImmunization.class);
    }

    public List<CanonicalCondition> conditions() {
        return of(RecordKind.CONDITION, CanonicalCondition.class);
    }

    public List<CanonicalMeasurement> measurements() {
        return of(RecordKind.MEASUREMENT, CanonicalMeasurement.class);
    }

    public List<CanonicalPregnancyOutcome> pregnancyOutcomes() {
        return of(RecordKind.PREGNANCY_OUTCOME, CanonicalPregnancyOutcome.class);
    }

    /** The window a capability was read for, or empty when the run did not read it. */
    public Optional<DateWindow> windowOf(String capability) {
        return Optional.ofNullable(windows.get(capability));
    }

    /** Every capability this dataset holds, with its window. */
    public SortedMap<String, DateWindow> windows() {
        return windows;
    }

    private <T extends Record> List<T> of(RecordKind kind, Class<T> type) {
        List<Record> list = records.getOrDefault(kind, List.of());
        List<T> typed = new ArrayList<>(list.size());
        for (Record r : list) {
            typed.add(type.cast(r));
        }
        return Collections.unmodifiableList(typed);
    }

    /** Collects records of any kind; the kind is checked against the record's type. */
    public static final class Builder {
        private final List<CanonicalEncounter> encounters = new ArrayList<>();
        private final Map<RecordKind, List<Record>> records = new EnumMap<>(RecordKind.class);
        private final SortedMap<String, DateWindow> windows = new TreeMap<>();

        private Builder() {}

        public Builder window(String capability, DateWindow window) {
            windows.put(capability, window);
            return this;
        }

        public Builder encounter(CanonicalEncounter encounter) {
            encounters.add(encounter);
            return this;
        }

        public Builder add(RecordKind kind, Record canonicalRecord) {
            if (!kind.recordType().isInstance(canonicalRecord)) {
                throw new IllegalArgumentException("a " + kind.wireName() + " record must be "
                        + kind.recordType().getSimpleName() + ", got "
                        + canonicalRecord.getClass().getSimpleName());
            }
            records.computeIfAbsent(kind, k -> new ArrayList<>()).add(canonicalRecord);
            return this;
        }

        /** Adds a record of whichever kind its type belongs to. */
        public Builder add(Record canonicalRecord) {
            for (RecordKind kind : RecordKind.values()) {
                if (kind.recordType().isInstance(canonicalRecord)) {
                    return add(kind, canonicalRecord);
                }
            }
            throw new IllegalArgumentException(
                    "not a canonical v2 record: " + canonicalRecord.getClass().getSimpleName());
        }

        public CanonicalDataset build() {
            return new CanonicalDataset(encounters, records, windows);
        }
    }
}
