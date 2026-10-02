package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalTeam;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * The CNES team type of a link's INE (24 b): the most recent observation up to the cutoff. The DW
 * usually lacks it (limitation L1); then the eAP exception is not applied — never presumed.
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

    /** True only when the INE's team is proven to be an eAP tipo 76. */
    boolean eap76(String ine) {
        return ine != null
                && teams.stream()
                        .filter(t -> ine.equals(t.ine()))
                        .filter(t -> {
                            LocalDate observed = C3Dates.parse(t.observedAt());
                            return observed == null || !observed.isAfter(cutoff);
                        })
                        .max(RECENCY)
                        .map(t -> C3Codes.EAP_TEAM_TYPE.equals(C3Codes.token(t.teamTypeCode())))
                        .orElse(false);
    }
}
