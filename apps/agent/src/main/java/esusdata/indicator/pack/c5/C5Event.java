package esusdata.indicator.pack.c5;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.SourceRef;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

/**
 * One source record that can support a C5 practice, reduced to what its evidence row carries: the
 * record, its date, the professional and team, and the information model it came from ({@code
 * MIAI}, {@code MIP}, {@code MIAC} or {@code MIVDT}, item 24 e, p. 2–3). Never a name, CPF or CNS.
 */
record C5Event(SourceRef sourceRef, LocalDate date, String cbo, String cnes, String ine, String model) {

    static final String MIAI = "MIAI";
    static final String MIP = "MIP";
    static final String MIAC = "MIAC";
    static final String MIVDT = "MIVDT";

    static C5Event of(CanonicalCareEvent e) {
        return new C5Event(e.sourceRef(), date(e.careDate()), e.cbo(), e.cnes(), e.ine(), MIAI);
    }

    static C5Event of(CanonicalProcedureEvent e) {
        return new C5Event(e.sourceRef(), date(e.eventDate()), e.cbo(), e.cnes(), e.ine(), model(e.origin()));
    }

    /** A measurement has no team of its own; its model is its origin ({@code MIP} or {@code MIAC}). */
    static C5Event of(CanonicalMeasurement m) {
        return new C5Event(m.sourceRef(), date(m.measuredDate()), m.cbo(), null, null, model(m.origin()));
    }

    static C5Event of(CanonicalHomeVisit v) {
        return new C5Event(v.sourceRef(), date(v.visitDate()), v.cbo(), v.cnes(), v.ine(), MIVDT);
    }

    /** An ISO date, or {@code null} when the source has none (such a record proves nothing). */
    static LocalDate date(String iso) {
        return iso == null || iso.isBlank() ? null : LocalDate.parse(iso.strip());
    }

    /**
     * Whether a procedure or measurement came from one of {@code models}; a missing origin is no
     * model at all, and a dental record ({@code MIAO}) is in none of Quadros 03/04.
     */
    static boolean originIn(String origin, Set<String> models) {
        return origin != null && models.contains(normalizedOrigin(origin));
    }

    /** The key a practice deduplicates its supporting events by: the source record. */
    SourceRef identity() {
        return sourceRef;
    }

    private static String model(String origin) {
        return origin == null ? null : normalizedOrigin(origin);
    }

    private static String normalizedOrigin(String origin) {
        return origin.strip().toUpperCase(Locale.ROOT);
    }
}
