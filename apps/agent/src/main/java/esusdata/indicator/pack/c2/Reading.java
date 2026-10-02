package esusdata.indicator.pack.c2;

/**
 * One reading the ficha leaves open (its AMB-C2-xx). A practice is decided only when every
 * combination of the readings that touch it gives the same answer; otherwise it is ambiguous and the
 * readings that flip it are reported (Tech Spec §4.2: "Os resultados esperados de uma ambiguidade
 * devem permanecer bloqueados até esclarecimento documentado").
 */
enum Reading {
    /** "Até o 30º dia de vida": the day of birth + 30 is inside (AMB-C2-01). */
    DAY_30_INSIDE(C2Codes.AMB_C2_01),
    /** "Até os 6 meses / 2 anos": the exact anniversary date is inside (AMB-C2-02). */
    ANNIVERSARY_DAY_INSIDE(C2Codes.AMB_C2_02),
    /** A missing anniversary day (29/02, 31/08 + 6 meses) moves to the next day, not to the month end (AMB-C2-02). */
    ANNIVERSARY_NEXT_DAY(C2Codes.AMB_C2_02),
    /** A home consultation is "presencial" for A and a consultation for B (AMB-C2-04). */
    HOME_CARE_COUNTS(C2Codes.AMB_C2_04),
    /** An encounter whose source does not say presential or remote is presential (DW gap L3). */
    UNKNOWN_MODALITY_PRESENTIAL(C2Codes.LACUNA_L3),
    /** A teleconsultation or child-development code recorded only in the MIP is a consultation (AMB-C2-06). */
    PROCEDURE_ONLY_CONSULT(C2Codes.AMB_C2_06),
    /** Two different consultations on the same day count as two (AMB-C2-15). */
    SAME_DAY_CONSULTS(C2Codes.AMB_C2_15),
    /** {@code 01.01.04.002-4} or {@code 03.01.01.026-9} alone, without both values, is a pair (AMB-C2-07 i). */
    LONE_ANTHROPOMETRY_CODE(C2Codes.AMB_C2_07),
    /** Several self-contained weight-and-height records on one day count separately (AMB-C2-07 ii). */
    SAME_DAY_PAIRS(C2Codes.AMB_C2_07),
    /** The second visit may also fall in the first 30 days (AMB-C2-08 i). */
    SECOND_VISIT_EARLY(C2Codes.AMB_C2_08),
    /** Two visit records on the same day are two visits (AMB-C2-08 ii). */
    SAME_DAY_VISITS(C2Codes.AMB_C2_08),
    /** A visit whose outcome is not "realizada" still counts (AMB-C2-08 iv). */
    UNCONFIRMED_VISIT(C2Codes.AMB_C2_08),
    /** Group 1 doses are occasions with every component on the same date (AMB-C2-09 i). */
    PER_OCCASION(C2Codes.AMB_C2_09),
    /** A {@code 09} dose in the first 30 days counts toward the three hepatitis B doses (AMB-C2-09 ii). */
    BIRTH_HEPATITIS_B(C2Codes.AMB_C2_09),
    /** A dose closer than 30 days invalidates the component instead of being skipped (AMB-C2-09 iii). */
    SHORT_INTERVAL_INVALIDATES(C2Codes.AMB_C2_09),
    /**
     * The 12-month and two-year limits of a dose use the day it was recorded, not the day it was
     * applied — differs only for a transcription (AMB-C2-09 v).
     */
    DOSE_BY_REGISTRATION_DATE(C2Codes.AMB_C2_09),
    /** Doses on or after the second birthday count (AMB-C2-10 i). */
    DOSES_AFTER_TWO_YEARS(C2Codes.AMB_C2_10),
    /** A dose recorded by a CBO outside the 24 c/d groups does not count (AMB-C2-11). */
    VACCINE_CBO_RESTRICTED(C2Codes.AMB_C2_11);

    private final String code;

    Reading(String code) {
        this.code = code;
    }

    /** The ambiguity (or source gap) this reading belongs to. */
    String code() {
        return code;
    }
}
