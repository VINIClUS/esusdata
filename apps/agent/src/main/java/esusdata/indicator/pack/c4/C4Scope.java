package esusdata.indicator.pack.c4;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.CanonicalTeam;
import java.util.List;
import java.util.function.Function;

/** The municipal scope check (§1.12): a record of another municipality is refused, never counted. */
final class C4Scope {

    private C4Scope() {}

    static void requireMunicipality(CanonicalDataset data, String municipalityIbge) {
        check(data.persons(), CanonicalPerson::municipalityIbge, municipalityIbge);
        check(data.registrations(), CanonicalRegistration::municipalityIbge, municipalityIbge);
        check(data.teams(), CanonicalTeam::municipalityIbge, municipalityIbge);
        check(data.careEvents(), CanonicalCareEvent::municipalityIbge, municipalityIbge);
        check(data.procedureEvents(), CanonicalProcedureEvent::municipalityIbge, municipalityIbge);
        check(data.homeVisits(), CanonicalHomeVisit::municipalityIbge, municipalityIbge);
        check(data.conditions(), CanonicalCondition::municipalityIbge, municipalityIbge);
        check(data.measurements(), CanonicalMeasurement::municipalityIbge, municipalityIbge);
    }

    private static <T extends Record> void check(
            List<T> records, Function<T, String> municipality, String municipalityIbge) {
        for (T r : records) {
            String found = municipality.apply(r);
            if (!municipalityIbge.equals(found)) {
                throw new IllegalArgumentException("a " + r.getClass().getSimpleName() + " of municipality " + found
                        + " is outside the authorized municipality " + municipalityIbge);
            }
        }
    }
}
