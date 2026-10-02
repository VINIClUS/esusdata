package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalRegistration;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * The person's link at the cutoff, approximated by the local individual registration (item 14;
 * the national rule of NT 30/2025 is applied by the SIAPS — limitation L8): the most recent version
 * dated up to the cutoff, ignoring simplified and inactive ones (§1.7.3). {@code exclusion} is the
 * reason code when the link does not hold; {@code ine == null} means no team.
 */
record RegistrationLink(String exclusion, String cnes, String ine) {

    private static final Comparator<CanonicalRegistration> RECENCY = Comparator.comparing(
                    (CanonicalRegistration r) -> C3Dates.parse(r.registrationDate()))
            .thenComparing(r ->
                    r.sourceRef() == null ? "" : String.valueOf(r.sourceRef().recordId()));

    static RegistrationLink resolve(List<CanonicalRegistration> versions, LocalDate cutoff) {
        CanonicalRegistration current = versions.stream()
                .filter(r -> !Boolean.TRUE.equals(r.simplified()) && !Boolean.TRUE.equals(r.inactive()))
                .filter(r -> {
                    LocalDate date = C3Dates.parse(r.registrationDate());
                    return date != null && !date.isAfter(cutoff);
                })
                .max(RECENCY)
                .orElse(null);
        if (current == null || Boolean.TRUE.equals(current.refused())) {
            return new RegistrationLink(C3Reasons.SEM_VINCULO, null, null);
        }
        String exit = current.exitReason() == null ? "" : current.exitReason().strip();
        String exclusion = null;
        if (C3Codes.EXIT_CHANGE_OF_TERRITORY.equals(exit)) {
            exclusion = C3Reasons.INTERROMPIDO_MUDANCA_TERRITORIO;
        } else if (C3Codes.EXIT_DEATH.equals(exit)) {
            exclusion = C3Reasons.EXCLUIDO_OBITO;
        }
        return new RegistrationLink(exclusion, current.cnes(), current.ine());
    }
}
