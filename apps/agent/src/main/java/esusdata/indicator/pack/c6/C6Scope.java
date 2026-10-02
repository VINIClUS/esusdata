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
import java.util.List;
import java.util.function.Function;

/** Refuses a dataset with any record outside the authorized municipality (§1.12, C1's own check). */
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

    private static <T> void check(List<T> records, Function<T, String> municipality, String expected) {
        for (T r : records) {
            if (!expected.equals(municipality.apply(r))) {
                throw new IllegalArgumentException(
                        "record municipality does not match requested municipality: " + municipality.apply(r));
            }
        }
    }
}
