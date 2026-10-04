package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalMeasurement;
import java.util.List;

/**
 * How a collective activity (MIAC) meets the ficha's two code conditions — "Atividade código 05
 * e 06" and the quadro's "Práticas em Saúde" (24 e; Quadros 04 e 08): both, only one (AMB-C3-19)
 * or neither.
 */
enum MiacMatch {
    BOTH,
    ONE,
    NEITHER;

    /** {@code NEITHER} also for a record that is not a MIAC. */
    static MiacMatch of(CanonicalMeasurement measurement, List<String> practices) {
        if (!isMiac(measurement)) {
            return NEITHER;
        }
        boolean activity = C3Codes.ledi(measurement.activityTypeCode(), C3Codes.MIAC_ACTIVITY_TYPES);
        boolean practice = C3Codes.anyLedi(measurement.healthPracticeCodes(), practices);
        if (activity && practice) {
            return BOTH;
        }
        return activity || practice ? ONE : NEITHER;
    }

    static boolean isMiac(CanonicalMeasurement measurement) {
        return C3Codes.ORIGIN_MIAC.equals(C3Codes.token(measurement.origin()));
    }

    /** Whether the record counts at all. */
    boolean counts() {
        return this != NEITHER;
    }

    /** {@code own} when both conditions hold; AMB-C3-19 when only one does. */
    Ambiguity ambiguity(Ambiguity own) {
        return this == BOTH ? own : Ambiguity.AMB_C3_19;
    }
}
