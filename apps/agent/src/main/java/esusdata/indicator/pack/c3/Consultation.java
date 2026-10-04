package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalProcedureEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A consultation of the Quadro 02 (A, B, I): a MIAI by a médica(o) or enfermeira(o) with at least
 * one CIAP-2/CID-10 code, or a consultation SIGTAP recorded only in the MIP on a day without such a
 * MIAI (AMB-C3-12 (i)); on the same day both are the same encounter, counted once (CT-C3-20).
 */
record Consultation(EventRef event, CodeMatch pregnancy, CodeMatch puerperium, boolean procedureOnly) {

    /** The person's consultations, in evidence order. */
    static List<Consultation> of(PersonRecords person) {
        List<Consultation> consultations = new ArrayList<>();
        Set<LocalDate> miaiDays = new HashSet<>();
        for (CanonicalCareEvent event : person.individualCare()) {
            EventRef ref = EventRef.of(event);
            if (ref.date() != null && C3Codes.CONSULT_CBO.matches(event.cbo()) && CodeMatch.hasAnyCode(event)) {
                miaiDays.add(ref.date());
                consultations.add(new Consultation(
                        ref,
                        CodeMatch.of(event, C3Codes.PREGNANCY_CIAP, C3Codes.PREGNANCY_CID),
                        CodeMatch.of(event, C3Codes.PUERPERIUM_CIAP, C3Codes.PUERPERIUM_CID),
                        false));
            }
        }
        for (CanonicalProcedureEvent procedure : person.procedures()) {
            EventRef ref = EventRef.of(procedure);
            if (ref.date() != null
                    && !miaiDays.contains(ref.date())
                    && Procedures.fromMip(procedure)
                    && C3Codes.CONSULT_SIGTAP.contains(Procedures.sigtap(procedure))
                    && C3Codes.CONSULT_CBO.matches(procedure.cbo())) {
                consultations.add(new Consultation(ref, CodeMatch.NONE, CodeMatch.NONE, true));
            }
        }
        consultations.sort((a, b) -> EventRef.ORDER.compare(a.event(), b.event()));
        return consultations;
    }

    LocalDate date() {
        return event.date();
    }

    /** {@code null} when it is certainly a prenatal consultation (A, B); else why it is not certain. */
    Ambiguity pregnancyAmbiguity() {
        return ambiguity(pregnancy);
    }

    /** {@code null} when it is certainly a puerperal consultation (I); else why it is not certain. */
    Ambiguity puerperiumAmbiguity() {
        return ambiguity(puerperium);
    }

    private Ambiguity ambiguity(CodeMatch match) {
        if (procedureOnly) {
            return Ambiguity.AMB_C3_12;
        }
        return switch (match) {
            case EXACT -> null;
            case PREFIX -> Ambiguity.AMB_C3_08;
            case NONE -> Ambiguity.AMB_C3_11;
        };
    }
}
