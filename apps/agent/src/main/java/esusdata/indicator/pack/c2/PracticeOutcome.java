package esusdata.indicator.pack.c2;

import esusdata.indicator.model.SourceRef;
import esusdata.indicator.model.TeamScope;
import java.time.LocalDate;
import java.util.List;

/** How one practice ended for one child, with the source events behind it. */
record PracticeOutcome(String component, Status status, String reasonCode, List<Support> support) {

    static final String MET = "CUMPRIDA";
    static final String NOT_MET = "NAO_CUMPRIDA";
    /** Not met while the practice's window is still open on the cutoff (Tech Spec §2.4 C2). */
    static final String NOT_MET_WINDOW_OPEN = "NAO_CUMPRIDA_PRAZO_ABERTO";

    enum Status {
        MET,
        NOT_MET
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

    /** The practice credited in full to a child of an eAP 76 team (item 24 b, C2-D1): met, without the event. */
    static PracticeOutcome credited(String component) {
        return new PracticeOutcome(component, Status.MET, TeamScope.REASON_CREDITED_EAP76, List.of());
    }

    boolean scores() {
        return status == Status.MET;
    }

    /** A source record a practice decision rests on, with what the evidence row shows of it. */
    record Support(SourceRef sourceRef, LocalDate date, String cbo, String cnes, String ine, String model) {}
}
