package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalProcedureEvent;

/**
 * What the Quadros accept of a procedure event: performed or evaluated, never only requested
 * (CT-C3-47). The consolidated procedure record (Quadros 03, 04, 07 e 08) never reaches the rule.
 */
final class Procedures {

    private static final String PERFORMED = "PERFORMED";
    private static final String EVALUATED = "EVALUATED";

    private Procedures() {}

    /** Performed or evaluated (the consolidated record never reaches the rule). */
    static boolean counts(CanonicalProcedureEvent event) {
        String stage = C3Codes.token(event.stage());
        return PERFORMED.equals(stage) || EVALUATED.equals(stage);
    }

    /** Counts, and came from the MIP. */
    static boolean fromMip(CanonicalProcedureEvent event) {
        return counts(event) && C3Codes.ORIGIN_MIP.equals(C3Codes.token(event.origin()));
    }

    /** Came from a MIAI (AMB-C3-18 (iv) for CBO 2234/3222). */
    static boolean fromMiai(CanonicalProcedureEvent event) {
        return C3Codes.ORIGIN_MIAI.equals(C3Codes.token(event.origin()));
    }

    /** The SIGTAP code with digits only. */
    static String sigtap(CanonicalProcedureEvent event) {
        return digits(event.sigtapCode());
    }

    static String digits(String code) {
        return code == null ? "" : code.replaceAll("\\D", "");
    }
}
