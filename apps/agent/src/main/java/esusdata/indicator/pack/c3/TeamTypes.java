package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalTeam;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The team of a link's INE as observed in the source (24 b): the most recent observation dated up
 * to the cutoff. An observation without a date proves nothing. The DW usually lacks the team type
 * (limitation L1); then neither the eAP exception nor the out-of-scope exclusion applies — never
 * presumed.
 */
final class TeamTypes {

    private static final Comparator<CanonicalTeam> RECENCY =
            Recency.of(CanonicalTeam::observedAt, CanonicalTeam::sourceRef);

    private final List<CanonicalTeam> teams;
    private final LocalDate cutoff;

    TeamTypes(List<CanonicalTeam> teams, LocalDate cutoff) {
        this.teams = List.copyOf(teams);
        this.cutoff = cutoff;
    }

    /** The team observation current at the cutoff for the INE, if any. */
    Optional<CanonicalTeam> current(String ine) {
        if (ine == null) {
            return Optional.empty();
        }
        return teams.stream()
                .filter(t -> ine.equals(t.ine()))
                .filter(t -> {
                    LocalDate observed = C3Dates.parse(t.observedAt());
                    return observed != null && !observed.isAfter(cutoff);
                })
                .max(RECENCY);
    }

    /** True only when the INE's team is proven to be an eAP tipo 76. */
    boolean eap76(String ine) {
        return type(ine).map(C3Codes.EAP_TEAM_TYPE::equals).orElse(false);
    }

    /** True only when the INE's team type is known and is neither 70 nor 76 (24 b). */
    boolean outOfScope(String ine) {
        return type(ine).map(t -> !C3Codes.TEAM_TYPES_IN_SCOPE.contains(t)).orElse(false);
    }

    /** The CNES of the INE's current team, if known. */
    Optional<String> cnes(String ine) {
        return current(ine).map(CanonicalTeam::cnes).filter(c -> !c.isBlank());
    }

    private Optional<String> type(String ine) {
        return current(ine).map(t -> C3Codes.token(t.teamTypeCode())).filter(t -> !t.isEmpty());
    }
}
