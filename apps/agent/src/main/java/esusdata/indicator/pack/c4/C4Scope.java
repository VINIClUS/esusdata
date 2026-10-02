package esusdata.indicator.pack.c4;

import esusdata.indicator.model.CanonicalDataset;
import java.util.List;
import java.util.function.Function;

/** The municipal scope check (§1.12): a record of another municipality is refused, never counted. */
final class C4Scope {

    private C4Scope() {}

    static void requireMunicipality(CanonicalDataset data, String municipalityIbge) {
        check(data.persons(), r -> r.municipalityIbge(), municipalityIbge);
        check(data.registrations(), r -> r.municipalityIbge(), municipalityIbge);
        check(data.teams(), r -> r.municipalityIbge(), municipalityIbge);
        check(data.careEvents(), r -> r.municipalityIbge(), municipalityIbge);
        check(data.procedureEvents(), r -> r.municipalityIbge(), municipalityIbge);
        check(data.homeVisits(), r -> r.municipalityIbge(), municipalityIbge);
        check(data.conditions(), r -> r.municipalityIbge(), municipalityIbge);
        check(data.measurements(), r -> r.municipalityIbge(), municipalityIbge);
    }

    private static <T extends Record> void check(
            List<T> records, Function<T, String> municipality, String municipalityIbge) {
        for (T r : records) {
            if (!municipalityIbge.equals(municipality.apply(r))) {
                throw new IllegalArgumentException("a " + r.getClass().getSimpleName() + " of municipality "
                        + municipality.apply(r) + " is outside the authorized municipality " + municipalityIbge);
            }
        }
    }
}
