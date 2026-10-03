package esusdata.indicator.pack.c2;

import esusdata.indicator.model.SourceRef;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

/** How one practice ended for one child, with the source events behind it. */
record PracticeOutcome(
        String component, Status status, String reasonCode, SortedSet<String> ambiguities, List<Support> support) {

    static final String MET = "CUMPRIDA";
    static final String NOT_MET = "NAO_CUMPRIDA";
    static final String EXEMPT_EAP = "ISENTA_EAP_76";
    static final String AMBIGUITY_PREFIX = "AMBIGUIDADE:";
    /** Undecided only because the source lacks a datum (a DW gap), not because the ficha is ambiguous. */
    static final String UNAVAILABLE_PREFIX = "DADO_INDISPONIVEL:";
    /** Not met while the practice's window is still open on the cutoff (Tech Spec §2.4 C2). */
    static final String NOT_MET_WINDOW_OPEN = "NAO_CUMPRIDA_PRAZO_ABERTO";

    enum Status {
        MET,
        NOT_MET,
        /** The ficha scores it without evidence (D for eAP tipo 76). */
        EXEMPT,
        /** The readings of the ficha disagree for this child (RULE_AMBIGUITY). */
        AMBIGUOUS
    }

    PracticeOutcome {
        ambiguities = Collections.unmodifiableSortedSet(new TreeSet<>(ambiguities));
        support = List.copyOf(support);
    }

    static PracticeOutcome of(String component, Readings.Verdict verdict, List<Support> support) {
        if (verdict.ambiguous()) {
            return new PracticeOutcome(
                    component,
                    Status.AMBIGUOUS,
                    prefix(verdict.ambiguities()) + String.join(",", verdict.ambiguities()),
                    verdict.ambiguities(),
                    support);
        }
        return verdict.met()
                ? new PracticeOutcome(component, Status.MET, MET, new TreeSet<>(), support)
                : new PracticeOutcome(component, Status.NOT_MET, NOT_MET, new TreeSet<>(), List.of());
    }

    private static String prefix(SortedSet<String> reasons) {
        for (String reason : reasons) {
            if (!reason.startsWith(C2Codes.SOURCE_GAP_PREFIX)) {
                return AMBIGUITY_PREFIX;
            }
        }
        return UNAVAILABLE_PREFIX;
    }

    /** The same outcome, saying the practice's window has not closed yet when it was not met. */
    PracticeOutcome windowOpen() {
        return status == Status.NOT_MET
                ? new PracticeOutcome(component, status, NOT_MET_WINDOW_OPEN, ambiguities, support)
                : this;
    }

    static PracticeOutcome exempt(String component) {
        return new PracticeOutcome(component, Status.EXEMPT, EXEMPT_EAP, new TreeSet<>(), List.of());
    }

    boolean scores() {
        return status == Status.MET || status == Status.EXEMPT;
    }

    /** A source record a practice decision rests on, with what the evidence row shows of it. */
    record Support(SourceRef sourceRef, LocalDate date, String cbo, String cnes, String ine, String model) {}
}
