package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalCondition;
import java.util.List;

/**
 * Whether a record's CIAP-2/CID-10 codes are in a list of the ficha. CIAP-2 matches exactly; a
 * CID-10 code matches an entry when equal to it or inside its category (prefix, AMB-C3-08, e.g.
 * {@code O26.8} against {@code O26}). A code that also matches a more specific entry of the
 * {@code rival} list (e.g. {@code O15.2} against {@code O15} of the pregnancy and {@code O15.2} of
 * the puerperium) belongs to the rival's list; entries of the same length hold for both.
 */
final class CodeMatch {

    static final String CIAP2 = "CIAP2";
    private static final String CID10 = "CID10";

    private CodeMatch() {}

    /** True when any code of the event is in the lists. */
    static boolean of(CanonicalCareEvent event, List<String> ciapList, List<String> cidList, List<String> rivalCid) {
        for (String code : event.ciapCodes()) {
            if (ciapList.contains(C3Codes.normalized(code))) {
                return true;
            }
        }
        for (String code : event.cidCodes()) {
            if (cid(code, cidList, rivalCid)) {
                return true;
            }
        }
        return false;
    }

    /** A condition of the LPC by its code system ({@code CIAP2} exact, {@code CID10} as CID-10). */
    static boolean of(
            CanonicalCondition condition, List<String> ciapList, List<String> cidList, List<String> rivalCid) {
        String system = C3Codes.token(condition.codeSystem());
        if (CIAP2.equals(system)) {
            return ciapList.contains(C3Codes.normalized(condition.code()));
        }
        if (CID10.equals(system)) {
            return cid(condition.code(), cidList, rivalCid);
        }
        return any(condition.code(), ciapList, cidList, rivalCid);
    }

    /** One code of unknown system (an outcome record) against both lists. */
    static boolean any(String code, List<String> ciapList, List<String> cidList, List<String> rivalCid) {
        return ciapList.contains(C3Codes.normalized(code)) || cid(code, cidList, rivalCid);
    }

    /** One CID-10 code (with or without the dot) against a list, by category. */
    static boolean cid(String code, List<String> cidList, List<String> rivalCid) {
        String normalized = C3Codes.normalized(code);
        int own = specificity(normalized, cidList);
        return own > 0 && own >= specificity(normalized, rivalCid);
    }

    /** The length of the most specific entry the code equals or is inside; 0 when none. */
    private static int specificity(String normalized, List<String> entries) {
        int best = 0;
        for (String entry : entries) {
            if (normalized.startsWith(entry) && entry.length() > best) {
                best = entry.length();
            }
        }
        return best;
    }
}
