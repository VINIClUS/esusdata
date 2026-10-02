package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalPregnancyOutcome;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.SourceRef;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * The dataset by person: refuses any record of another municipality (the run is scoped to one,
 * §1.12), drops repeated source records (MET-32) and keeps the people who have an individual care
 * record — the only place a pregnancy is identified (4.1).
 */
final class RecordIndex {

    private static final String INDIVIDUAL = "INDIVIDUAL";
    private static final String DENTAL = "DENTAL";

    private RecordIndex() {}

    static SortedMap<String, PersonRecords> of(CanonicalDataset data, String municipalityIbge) {
        requireMunicipality(data, municipalityIbge);
        Map<String, List<CanonicalCareEvent>> care =
                group(data.careEvents(), CanonicalCareEvent::personKey, CanonicalCareEvent::sourceRef);
        Map<String, List<CanonicalProcedureEvent>> procedures =
                group(data.procedureEvents(), CanonicalProcedureEvent::personKey, CanonicalProcedureEvent::sourceRef);
        Map<String, List<CanonicalHomeVisit>> visits =
                group(data.homeVisits(), CanonicalHomeVisit::personKey, CanonicalHomeVisit::sourceRef);
        Map<String, List<CanonicalMeasurement>> measurements =
                group(data.measurements(), CanonicalMeasurement::personKey, CanonicalMeasurement::sourceRef);
        Map<String, List<CanonicalImmunization>> immunizations =
                group(data.immunizations(), CanonicalImmunization::personKey, CanonicalImmunization::sourceRef);
        Map<String, List<CanonicalPregnancyOutcome>> outcomes = group(
                data.pregnancyOutcomes(), CanonicalPregnancyOutcome::personKey, CanonicalPregnancyOutcome::sourceRef);
        Map<String, List<CanonicalRegistration>> registrations =
                group(data.registrations(), CanonicalRegistration::personKey, CanonicalRegistration::sourceRef);
        Map<String, List<CanonicalPerson>> persons =
                group(data.persons(), CanonicalPerson::personKey, CanonicalPerson::sourceRef);

        SortedMap<String, PersonRecords> index = new TreeMap<>();
        for (Map.Entry<String, List<CanonicalCareEvent>> entry : care.entrySet()) {
            List<CanonicalCareEvent> individual = ofForm(entry.getValue(), INDIVIDUAL);
            if (individual.isEmpty()) {
                continue;
            }
            String key = entry.getKey();
            index.put(
                    key,
                    new PersonRecords(
                            key,
                            individual,
                            ofForm(entry.getValue(), DENTAL),
                            procedures.getOrDefault(key, List.of()),
                            visits.getOrDefault(key, List.of()),
                            measurements.getOrDefault(key, List.of()),
                            immunizations.getOrDefault(key, List.of()),
                            outcomes.getOrDefault(key, List.of()),
                            registrations.getOrDefault(key, List.of()),
                            persons.getOrDefault(key, List.of())));
        }
        return index;
    }

    private static void requireMunicipality(CanonicalDataset data, String expected) {
        check(data.encounters(), CanonicalEncounter::municipalityIbge, expected);
        check(data.persons(), CanonicalPerson::municipalityIbge, expected);
        check(data.registrations(), CanonicalRegistration::municipalityIbge, expected);
        check(data.teams(), CanonicalTeam::municipalityIbge, expected);
        check(data.careEvents(), CanonicalCareEvent::municipalityIbge, expected);
        check(data.procedureEvents(), CanonicalProcedureEvent::municipalityIbge, expected);
        check(data.homeVisits(), CanonicalHomeVisit::municipalityIbge, expected);
        check(data.immunizations(), CanonicalImmunization::municipalityIbge, expected);
        check(data.conditions(), CanonicalCondition::municipalityIbge, expected);
        check(data.measurements(), CanonicalMeasurement::municipalityIbge, expected);
        check(data.pregnancyOutcomes(), CanonicalPregnancyOutcome::municipalityIbge, expected);
    }

    private static <T> void check(List<T> records, Function<T, String> municipality, String expected) {
        for (T canonicalRecord : records) {
            String actual = municipality.apply(canonicalRecord);
            if (!expected.equals(actual)) {
                throw new IllegalArgumentException(
                        "record municipality " + actual + " does not match the evaluated municipality " + expected);
            }
        }
    }

    private static <T> Map<String, List<T>> group(
            List<T> records, Function<T, String> person, Function<T, SourceRef> ref) {
        Map<String, List<T>> byPerson = new HashMap<>();
        Set<SourceRef> seen = new HashSet<>();
        for (T canonicalRecord : records) {
            SourceRef sourceRef = ref.apply(canonicalRecord);
            String key = person.apply(canonicalRecord);
            boolean repeated = sourceRef != null && !seen.add(sourceRef);
            if (key != null && !repeated) {
                byPerson.computeIfAbsent(key, k -> new ArrayList<>()).add(canonicalRecord);
            }
        }
        return byPerson;
    }

    private static List<CanonicalCareEvent> ofForm(List<CanonicalCareEvent> events, String form) {
        return events.stream()
                .filter(e -> e.form() != null && form.equals(e.form().strip().toUpperCase(Locale.ROOT)))
                .toList();
    }
}
