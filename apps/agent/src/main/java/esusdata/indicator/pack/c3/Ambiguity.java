package esusdata.indicator.pack.c3;

/**
 * The ambiguities of the C3 ficha a decision can depend on ({@code docs/metodologia/
 * c3-gestacao-puerperio.md}, "Ambiguidades"). The name is the reason-code suffix; {@link #id()}
 * is the id the transcription uses.
 */
enum Ambiguity {
    /** Ordinal week: DUM+84..90 for A, DUM+133..139 for F. */
    AMB_C3_01,
    /** Trimesters are not defined (G, H). */
    AMB_C3_02,
    /** Identification of the pregnancy: diverging DUM readings, no 24 f code, code without DUM. */
    AMB_C3_03,
    /** The day D, the day D+42 and the 294/42-day boundaries. */
    AMB_C3_04,
    /** Outcome recorded after DUM+294. */
    AMB_C3_05,
    /**
     * Abortion (24 g) whose effect the ficha leaves open: a code in the puerperium, or an LPC
     * condition that is not active or latent ("ativos").
     */
    AMB_C3_07,
    /** CID-10 that matches a list only by category prefix. */
    AMB_C3_08,
    /** Consultation with a code outside the matching 24 f list. */
    AMB_C3_11,
    /** Consultation only in the MIP, or more than one consultation on the same day. */
    AMB_C3_12,
    /** dTpa by a CBO outside the 24 c/d lists. */
    AMB_C3_13,
    /** Blood pressure by ACS, from the MIAC, or more than one per day. */
    AMB_C3_14,
    /** Anthropometry without values, from the MIAC, or more than one pair per day. */
    AMB_C3_15,
    /** Visits: first consultation, puerperium, CBO 3222 and same-day duplicates. */
    AMB_C3_16,
    /** dTpa after the end of the pregnancy or without an application date. */
    AMB_C3_17,
    /** Tests: HTLV and MIAI records by CBO 2234/3222. */
    AMB_C3_18,
    /** MIAC with only one of the two code conditions (activity type, health practice). */
    AMB_C3_19,
    /** CBO granularity: another occupation of the family 3224 than the TSB (C, K). */
    AMB_C3_20;

    /** The transcription's id, e.g. {@code AMB-C3-01}. */
    String id() {
        return name().replace('_', '-');
    }
}
