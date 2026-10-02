package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalProcedureEvent;
import java.util.Locale;

/**
 * What the Quadros accept of a procedure event: performed or evaluated, never only requested
 * (CT-C3-47), and never the consolidated procedure record (Quadros 03, 04, 07 e 08).
 */
final class Procedures {

    private static final String PERFORMED = "PERFORMED";
    private static final String EVALUATED = "EVALUATED";

    private Procedures() {}

    /** Performed or evaluated, and not consolidated. */
    static boolean counts(CanonicalProcedureEvent event) {
        String stage = upper(event.stage());
        return (PERFORMED.equals(stage) || EVALUATED.equals(stage)) && !consolidated(event);
    }

    /** Counts, and came from the MIP. */
    static boolean fromMip(CanonicalProcedureEvent event) {
        return counts(event) && "MIP".equals(upper(event.origin()));
    }

    /** Came from a MIAI (AMB-C3-18 (iv) for CBO 2234/3222). */
    static boolean fromMiai(CanonicalProcedureEvent event) {
        return "MIAI".equals(upper(event.origin()));
    }

    /** The SIGTAP code with digits only. */
    static String sigtap(CanonicalProcedureEvent event) {
        return digits(event.sigtapCode());
    }

    static String digits(String code) {
        return code == null ? "" : code.replaceAll("\\D", "");
    }

    private static boolean consolidated(CanonicalProcedureEvent event) {
        return upper(event.origin()).contains("CONSOLIDADO");
    }

    private static String upper(String text) {
        return text == null ? "" : text.strip().toUpperCase(Locale.ROOT);
    }
}
