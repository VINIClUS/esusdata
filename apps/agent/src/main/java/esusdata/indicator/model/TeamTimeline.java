package esusdata.indicator.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The team type of an INE on a day, from the states the {@code team} capability reads (ADR 0031,
 * ENG-42). "As of" lookups never guess: no state, or two states with different codes on the same
 * day, is a result of its own, and the rule excludes the team and says why.
 *
 * <p>Codes are compared as the source gives them ({@code tb_tipo_equipe.nu_ms}); that only 70 and 76
 * mean something to a pack is the pack's business, not this class's.
 */
public final class TeamTimeline {

    private final Map<String, List<CanonicalTeam>> byIne = new HashMap<>();

    /** What is known about an INE's type on a day. */
    public enum Kind {
        /** Exactly one code is valid on the day. */
        TYPE,
        /** No state of the INE covers the day, or its covering states carry no code. */
        NONE,
        /** Covering states carry different codes: the source contradicts itself. */
        CONFLICT
    }

    /**
     * The outcome of a lookup.
     *
     * @param kind what is known
     * @param code the code when {@code kind} is {@link Kind#TYPE}, otherwise {@code null}
     * @param codes every code of the covering states, sorted, for evidence
     * @param typeSource {@link CanonicalTeam#AUDIT} or {@link CanonicalTeam#CURRENT_FALLBACK} when
     *     {@code kind} is {@link Kind#TYPE} and the source says; otherwise {@code null}
     */
    public record Resolution(Kind kind, String code, SortedSet<String> codes, String typeSource) {}

    private TeamTimeline(List<CanonicalTeam> teams) {
        for (CanonicalTeam team : teams) {
            if (team.ine() != null) {
                byIne.computeIfAbsent(team.ine(), ignored -> new ArrayList<>()).add(team);
            }
        }
    }

    public static TeamTimeline of(List<CanonicalTeam> teams) {
        return new TeamTimeline(teams);
    }

    /** The team type of {@code ine} on {@code day}. Several states with the same code are one. */
    public Resolution typeOn(String ine, LocalDate day) {
        SortedSet<String> codes = new TreeSet<>();
        String source = null;
        for (CanonicalTeam state : byIne.getOrDefault(ine, List.of())) {
            if (state.teamTypeCode() != null && state.validOn(day)) {
                codes.add(state.teamTypeCode());
                source = state.typeSource();
            }
        }
        if (codes.isEmpty()) {
            return new Resolution(Kind.NONE, null, codes, null);
        }
        if (codes.size() > 1) {
            return new Resolution(Kind.CONFLICT, null, codes, null);
        }
        return new Resolution(Kind.TYPE, codes.first(), codes, source);
    }

    /** The INEs the timeline knows, whatever the day. */
    public Set<String> ines() {
        return Collections.unmodifiableSet(byIne.keySet());
    }
}
