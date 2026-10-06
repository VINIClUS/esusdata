package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalMeasurement;
import java.util.List;

/**
 * Whether a collective activity (MIAC) meets the ficha's two code conditions — "Atividade código
 * 05 e 06" <em>and</em> the quadro's "Práticas em Saúde" (24 e; Quadros 04 e 08; AMB-C3-19). A
 * record with only one of them does not count.
 */
final class MiacMatch {

    private MiacMatch() {}

    /** True for a MIAC record with activity 05/06 and a practice of {@code practices} (LEDI codes). */
    static boolean counts(CanonicalMeasurement measurement, List<String> practices) {
        return isMiac(measurement)
                && C3Codes.ledi(measurement.activityTypeCode(), C3Codes.MIAC_ACTIVITY_TYPES)
                && C3Codes.anyLedi(measurement.healthPracticeCodes(), practices);
    }

    static boolean isMiac(CanonicalMeasurement measurement) {
        return C3Codes.ORIGIN_MIAC.equals(C3Codes.token(measurement.origin()));
    }
}
