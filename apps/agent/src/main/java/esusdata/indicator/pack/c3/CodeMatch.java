package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import java.util.List;

/**
 * How a record's CIAP-2/CID-10 codes meet a list of the ficha: exactly, only by the CID-10
 * category prefix (AMB-C3-08, e.g. {@code O26.8} against {@code O26}), or not at all. CIAP-2 codes
 * match only exactly. Declared from best to worst.
 */
enum CodeMatch {
    EXACT,
    PREFIX,
    NONE;

    /** The best match of any of the event's codes against the two lists. */
    static CodeMatch of(CanonicalCareEvent event, List<String> ciapList, List<String> cidList) {
        CodeMatch best = NONE;
        for (String code : event.ciapCodes()) {
            if (ciapList.contains(C3Codes.normalized(code))) {
                return EXACT;
            }
        }
        for (String code : event.cidCodes()) {
            best = better(best, cid(code, cidList));
        }
        return best;
    }

    /** One CID-10 code (with or without the dot) against a list. */
    static CodeMatch cid(String code, List<String> cidList) {
        String normalized = C3Codes.normalized(code);
        if (normalized.isEmpty()) {
            return NONE;
        }
        if (cidList.contains(normalized)) {
            return EXACT;
        }
        for (String entry : cidList) {
            if (normalized.startsWith(entry)) {
                return PREFIX;
            }
        }
        return NONE;
    }

    /** One code of unknown system (an outcome record) against both lists. */
    static CodeMatch any(String code, List<String> ciapList, List<String> cidList) {
        if (ciapList.contains(C3Codes.normalized(code))) {
            return EXACT;
        }
        return cid(code, cidList);
    }

    /** True when the event carries at least one CIAP-2 or CID-10 code (Quadro 02). */
    static boolean hasAnyCode(CanonicalCareEvent event) {
        return event.ciapCodes().stream().anyMatch(c -> !C3Codes.normalized(c).isEmpty())
                || event.cidCodes().stream()
                        .anyMatch(c -> !C3Codes.normalized(c).isEmpty());
    }

    boolean found() {
        return this != NONE;
    }

    /** The better of two matches (declared from best to worst). */
    static CodeMatch better(CodeMatch a, CodeMatch b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
