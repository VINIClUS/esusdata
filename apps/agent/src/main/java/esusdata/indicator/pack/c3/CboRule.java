package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CboGroups;
import java.util.List;

/**
 * Which professionals a practice accepts: the {@code certain} groups count for sure; the {@code
 * fallbacks} count only under one reading of the ficha, each with its ambiguity.
 */
record CboRule(CboGroups certain, List<Fallback> fallbacks) {

    /** Quadro 03 (C): the ACS (5151-05) is AMB-C3-14 (i); a non-TSB 3224 is AMB-C3-20. */
    static final CboRule BLOOD_PRESSURE = new CboRule(
            C3Codes.BLOOD_PRESSURE_CBO,
            List.of(
                    new Fallback(C3Codes.ACS_CBO, Ambiguity.AMB_C3_14),
                    new Fallback(C3Codes.ORAL_HEALTH_FAMILY, Ambiguity.AMB_C3_20)));

    /** Quadro 05 (E, J): another occupation of the family 3222 is AMB-C3-16 (iii). */
    static final CboRule VISIT =
            new CboRule(C3Codes.VISIT_CBO, List.of(new Fallback(C3Codes.VISIT_CBO_FAMILY, Ambiguity.AMB_C3_16)));

    /** Quadro 08 (K): another occupation of the family 3224 than the TSB is AMB-C3-20. */
    static final CboRule DENTAL =
            new CboRule(C3Codes.DENTAL_CBO, List.of(new Fallback(C3Codes.ORAL_HEALTH_FAMILY, Ambiguity.AMB_C3_20)));

    /** Groups that count only under one reading, with that reading's ambiguity. */
    record Fallback(CboGroups groups, Ambiguity ambiguity) {}

    CboRule {
        fallbacks = List.copyOf(fallbacks);
    }

    boolean accepts(String cbo) {
        return certain.matches(cbo)
                || fallbacks.stream().anyMatch(f -> f.groups().matches(cbo));
    }

    /** {@code null} for a certain CBO, else the ambiguity of the first group that accepts it. */
    Ambiguity ambiguityOf(String cbo) {
        if (certain.matches(cbo)) {
            return null;
        }
        return fallbacks.stream()
                .filter(f -> f.groups().matches(cbo))
                .map(Fallback::ambiguity)
                .findFirst()
                .orElse(null);
    }
}
