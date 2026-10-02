package esusdata.indicator.pack.c3;

/** The tri-state decision of one practice, plus the eAP tipo 76 exemption of E and J (24 b). */
enum PracticeDecision {
    MET,
    NOT_MET,
    /** Met under one reading of the ficha and not under another: {@code RULE_AMBIGUITY}. */
    AMBIGUOUS,
    EXEMPT
}
