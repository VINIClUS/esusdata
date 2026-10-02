package esusdata.indicator.model;

/**
 * One procedure or exam event (§1.7 "Exame"; kind {@code procedure_event}). Requesting, performing
 * and evaluating are different events: a request never proves an evaluation (§1.7, MET-09/15).
 *
 * @param sigtapCode SIGTAP code, digits only (e.g. {@code 0202010503})
 * @param stage {@code REQUESTED}, {@code EVALUATED} or {@code PERFORMED}
 * @param origin the information model it came from: {@code MIAI}, {@code MIP}, {@code MIAO} …
 */
public record CanonicalProcedureEvent(
        SourceRef sourceRef,
        String municipalityIbge,
        String personKey,
        String eventDate,
        String sigtapCode,
        String stage,
        String cbo,
        String cnes,
        String ine,
        String origin) {}
