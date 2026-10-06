package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.TeamScope;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The team rule of item 24 b for the INEs of the links ({@link TeamScope}): the type valid on the
 * last day of the competência. Without a type, with two, or with a type other than 70 and 76 the
 * episode is left out with its reason; never presumed.
 */
final class TeamTypes {

    private static final Comparator<CanonicalTeam> RECENCY = Comparator.comparing(
                    (CanonicalTeam t) -> t.validFrom() == null ? "" : t.validFrom())
            .thenComparing(t -> t.observedAt() == null ? "" : t.observedAt());

    private final List<CanonicalTeam> teams;
    private final LocalDate end;
    private final TeamScope scope;

    TeamTypes(List<CanonicalTeam> teams, LocalDate end) {
        this.teams = List.copyOf(teams);
        this.end = end;
        this.scope = TeamScope.of(this.teams, end);
    }

    /** What the team rule says about the INE. */
    TeamScope.Decision decide(String ine) {
        return scope.decide(ine);
    }

    /** The CNES of the INE's state valid on the last day (else the most recent state), if known. */
    Optional<String> cnes(String ine) {
        if (ine == null) {
            return Optional.empty();
        }
        List<CanonicalTeam> states =
                teams.stream().filter(t -> ine.strip().equals(t.ine())).toList();
        List<CanonicalTeam> valid = states.stream().filter(t -> t.validOn(end)).toList();
        return (valid.isEmpty() ? states : valid)
                .stream().max(RECENCY).map(CanonicalTeam::cnes).filter(c -> c != null && !c.isBlank());
    }
}
