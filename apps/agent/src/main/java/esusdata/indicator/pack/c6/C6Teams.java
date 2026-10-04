package esusdata.indicator.pack.c6;

import esusdata.indicator.model.CanonicalTeam;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Team types as of the cutoff (item 24 b, p. 2: «Serão consideradas equipes de Saúde da Família
 * (eSF), e equipes de Atenção Primária (eAP), tipo 70 e 76»). The DW has no team type (lacuna L1),
 * so this is empty unless an extract brings {@code team} records. Only the latest dated
 * observation counts; a null type is no information, an undated type is counted as a limitation,
 * and two types at the same latest instant are a conflict, never a choice (§1.7.3).
 */
final class C6Teams {

    /** What the ficha does with a team of this type. */
    enum TeamType {
        ESF,
        EAP,
        OUT_OF_SCOPE,
        CONFLICTING
    }

    private final Map<String, TeamType> types;
    private final long undated;

    private C6Teams(Map<String, TeamType> types, long undated) {
        this.types = Map.copyOf(types);
        this.undated = undated;
    }

    static C6Teams resolve(List<CanonicalTeam> teams, LocalDate cutoff) {
        Map<String, String> latestInstant = new HashMap<>();
        Map<String, SortedSet<String>> latestTypes = new HashMap<>();
        long undated = 0;
        for (CanonicalTeam t : teams) {
            if (t.ine() == null || t.teamTypeCode() == null) {
                continue;
            }
            if (t.observedAt() == null) {
                undated++;
            } else if (!observedDate(t).isAfter(cutoff)) {
                observe(t, latestInstant, latestTypes);
            }
        }
        Map<String, TeamType> types = new HashMap<>();
        latestTypes.forEach((ine, codes) -> types.put(ine, typeOf(codes)));
        return new C6Teams(types, undated);
    }

    /** Keeps, per INE, every type seen at the latest observation instant. */
    private static void observe(
            CanonicalTeam t, Map<String, String> latestInstant, Map<String, SortedSet<String>> latestTypes) {
        String current = latestInstant.get(t.ine());
        int order = current == null ? 1 : t.observedAt().compareTo(current);
        if (order > 0) {
            latestInstant.put(t.ine(), t.observedAt());
            latestTypes.put(t.ine(), new TreeSet<>());
        }
        if (order >= 0) {
            latestTypes.get(t.ine()).add(t.teamTypeCode());
        }
    }

    /** The type of {@code ine} at the cutoff, or empty when the extract does not say. */
    Optional<TeamType> typeOf(String ine) {
        return ine == null ? Optional.empty() : Optional.ofNullable(types.get(ine));
    }

    /** Typed observations without a date: ignored, and reported. */
    long undated() {
        return undated;
    }

    private static TeamType typeOf(SortedSet<String> codes) {
        if (codes.size() > 1) {
            return TeamType.CONFLICTING;
        }
        String code = codes.first();
        if (C6Codes.TEAM_TYPE_EAP.equals(code)) {
            return TeamType.EAP;
        }
        return C6Codes.TEAM_TYPE_ESF.equals(code) ? TeamType.ESF : TeamType.OUT_OF_SCOPE;
    }

    /** The ISO date (or date-time) a team type was observed; anything else is refused, never guessed. */
    private static LocalDate observedDate(CanonicalTeam team) {
        String observed = team.observedAt();
        try {
            return LocalDate.parse(observed.length() > 10 ? observed.substring(0, 10) : observed);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("team observedAt is not an ISO date: " + observed, e);
        }
    }
}
