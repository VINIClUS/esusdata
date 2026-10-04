package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.SourceRef;
import java.time.LocalDate;
import java.util.Comparator;

/**
 * One source record behind a decision, with what an evidence row shows of it: the reference, the
 * date and the professional's CBO, CNES and INE — never a name, CPF or CNS (ENG-36).
 */
record EventRef(SourceRef sourceRef, LocalDate date, String cbo, String cnes, String ine) {

    /** Date (missing first), then source reference: the order evidence is written in. */
    static final Comparator<EventRef> ORDER = Comparator.comparing(
                    EventRef::date, Comparator.nullsFirst(Comparator.<LocalDate>naturalOrder()))
            .thenComparing(EventRef::refKey);

    static EventRef of(CanonicalCareEvent e) {
        return new EventRef(e.sourceRef(), C3Dates.parse(e.careDate()), e.cbo(), e.cnes(), e.ine());
    }

    static EventRef of(CanonicalProcedureEvent e) {
        return new EventRef(e.sourceRef(), C3Dates.parse(e.eventDate()), e.cbo(), e.cnes(), e.ine());
    }

    static EventRef of(CanonicalHomeVisit e) {
        return new EventRef(e.sourceRef(), C3Dates.parse(e.visitDate()), e.cbo(), e.cnes(), e.ine());
    }

    static EventRef of(CanonicalMeasurement e) {
        return new EventRef(e.sourceRef(), C3Dates.parse(e.measuredDate()), e.cbo(), null, null);
    }

    static EventRef of(CanonicalImmunization e) {
        return new EventRef(e.sourceRef(), C3Dates.parse(e.applicationDate()), e.cbo(), e.cnes(), e.ine());
    }

    /** The source reference as one comparable text; the same record twice has the same key (MET-32). */
    String refKey() {
        if (sourceRef == null) {
            return "";
        }
        return part(sourceRef.sourceId()) + '|' + part(sourceRef.entityType()) + '|' + part(sourceRef.recordId());
    }

    private static String part(String text) {
        return text == null ? "" : text;
    }
}
