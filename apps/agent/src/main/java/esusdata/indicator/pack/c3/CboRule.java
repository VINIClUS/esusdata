package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CboGroups;

/**
 * Which professionals a practice accepts: the {@code certain} groups count for sure; the {@code
 * maybe} groups count only under one reading of the ficha ({@code ambiguity}).
 */
record CboRule(CboGroups certain, CboGroups maybe, Ambiguity ambiguity) {

    /** Quadro 03 (C): the ACS (5151-05) is AMB-C3-14 (i). */
    static final CboRule BLOOD_PRESSURE = new CboRule(C3Codes.BLOOD_PRESSURE_CBO, C3Codes.ACS_CBO, Ambiguity.AMB_C3_14);

    /** Quadro 05 (E, J): another occupation of the family 3222 is AMB-C3-16 (iii). */
    static final CboRule VISIT = new CboRule(C3Codes.VISIT_CBO, C3Codes.VISIT_CBO_FAMILY, Ambiguity.AMB_C3_16);

    boolean accepts(String cbo) {
        return certain.matches(cbo) || maybe.matches(cbo);
    }

    /** {@code null} for a certain CBO, else this rule's ambiguity. */
    Ambiguity ambiguityOf(String cbo) {
        return certain.matches(cbo) ? null : ambiguity;
    }
}
