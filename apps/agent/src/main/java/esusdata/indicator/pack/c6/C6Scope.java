package esusdata.indicator.pack.c6;

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
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.PartRequirement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * What the extract must be before C6 counts anything: inside the authorized municipality (§1.12) and
 * read for every part and window the pack asked for — a missing capability is never a zero.
 */
final class C6Scope {

    private C6Scope() {}

    static void requireMunicipality(CanonicalDataset data, String municipalityIbge) {
        check(data.encounters(), CanonicalEncounter::municipalityIbge, municipalityIbge);
        check(data.persons(), CanonicalPerson::municipalityIbge, municipalityIbge);
        check(data.registrations(), CanonicalRegistration::municipalityIbge, municipalityIbge);
        check(data.teams(), CanonicalTeam::municipalityIbge, municipalityIbge);
        check(data.careEvents(), CanonicalCareEvent::municipalityIbge, municipalityIbge);
        check(data.procedureEvents(), CanonicalProcedureEvent::municipalityIbge, municipalityIbge);
        check(data.homeVisits(), CanonicalHomeVisit::municipalityIbge, municipalityIbge);
        check(data.immunizations(), CanonicalImmunization::municipalityIbge, municipalityIbge);
        check(data.conditions(), CanonicalCondition::municipalityIbge, municipalityIbge);
        check(data.measurements(), CanonicalMeasurement::municipalityIbge, municipalityIbge);
        check(data.pregnancyOutcomes(), CanonicalPregnancyOutcome::municipalityIbge, municipalityIbge);
    }

    /**
     * Parts of {@code required} the extract did not read, or read for a shorter window. An extract
     * that declares no windows (a synthetic one) is taken as complete.
     */
    static List<String> uncoveredCapabilities(CanonicalDataset data, DataRequirements required) {
        if (data.windows().isEmpty()) {
            return List.of();
        }
        List<String> missing = new ArrayList<>();
        for (PartRequirement part : required.parts()) {
            Optional<DateWindow> read = data.windowOf(part.capability());
            boolean covered = read.isPresent()
                    && !read.get().start().isAfter(part.periodStart())
                    && !read.get().endExclusive().isBefore(part.periodEndExclusive());
            if (!covered) {
                missing.add(part.capability());
            }
        }
        return missing;
    }

    private static <T> void check(List<T> records, Function<T, String> municipality, String expected) {
        for (T r : records) {
            String found = municipality.apply(r);
            if (!expected.equals(found)) {
                throw new IllegalArgumentException(
                        "record municipality does not match requested municipality: " + found);
            }
        }
    }
}
