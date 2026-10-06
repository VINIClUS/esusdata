package esusdata.indicator.pack.c2;

import esusdata.indicator.model.SourceRef;
import java.time.LocalDate;
import java.util.List;

/** How one practice ended for one child, with the source events behind it. */
record PracticeOutcome(String component, Status status, String reasonCode, List<Support> support) {

    static final String MET = "CUMPRIDA";
    static final String NOT_MET = "NAO_CUMPRIDA";
    static final String EXEMPT_EAP = "ISENTA_EAP_76";
    /** Not met while the practice's window is still open on the cutoff (Tech Spec §2.4 C2). */
    static final String NOT_MET_WINDOW_OPEN = "NAO_CUMPRIDA_PRAZO_ABERTO";

    enum Status {
        MET,
        NOT_MET,
        /** The ficha scores it without evidence (D for eAP tipo 76). */
        EXEMPT
    }

    PracticeOutcome {
        support = List.copyOf(support);
    }

    static PracticeOutcome of(String component, boolean met, List<Support> support) {
        return met
                ? new PracticeOutcome(component, Status.MET, MET, support)
                : new PracticeOutcome(component, Status.NOT_MET, NOT_MET, List.of());
    }

    /** The same outcome, saying the practice's window has not closed yet when it was not met. */
    PracticeOutcome windowOpen() {
        return status == Status.NOT_MET ? new PracticeOutcome(component, status, NOT_MET_WINDOW_OPEN, support) : this;
    }

    static PracticeOutcome exempt(String component) {
        return new PracticeOutcome(component, Status.EXEMPT, EXEMPT_EAP, List.of());
    }

    boolean scores() {
        return status == Status.MET || status == Status.EXEMPT;
    }

    /** A source record a practice decision rests on, with what the evidence row shows of it. */
    record Support(SourceRef sourceRef, LocalDate date, String cbo, String cnes, String ine, String model) {}
}
