package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * A consultation of the Quadro 02 (A, B, I): a MIAI by a médica(o) or enfermeira(o) with at least
 * one code of the 24 f list of the pregnancy (A, B, the first consultation of E) or of the
 * puerperium (I) (AMB-C3-11). A consultation only in the MIP does not count (AMB-C3-12).
 */
record Consultation(EventRef event, boolean pregnancy, boolean puerperium) {

    /** The person's consultations of the pregnancy or of the puerperium, in evidence order. */
    static List<Consultation> of(PersonRecords person) {
        List<Consultation> consultations = new ArrayList<>();
        for (CanonicalCareEvent event : person.individualCare()) {
            EventRef ref = EventRef.of(event);
            if (ref.date() == null || !C3Codes.CONSULT_CBO.matches(event.cbo())) {
                continue;
            }
            boolean pregnancy =
                    CodeMatch.of(event, C3Codes.PREGNANCY_CIAP, C3Codes.PREGNANCY_CID, C3Codes.PUERPERIUM_CID);
            boolean puerperium =
                    CodeMatch.of(event, C3Codes.PUERPERIUM_CIAP, C3Codes.PUERPERIUM_CID, C3Codes.PREGNANCY_CID);
            if (pregnancy || puerperium) {
                consultations.add(new Consultation(ref, pregnancy, puerperium));
            }
        }
        consultations.sort((a, b) -> EventRef.ORDER.compare(a.event(), b.event()));
        return consultations;
    }

    LocalDate date() {
        return event.date();
    }
}
