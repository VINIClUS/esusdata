package esusdata.indicator.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * The team-type rule every pack of the Componente de Qualidade shares (decision records C1–C7,
 * "Regra de tipo de equipe"): which teams are considered on the last day of a competência, and why
 * the others are not. Item 24 b of the fichas: only eSF (type {@code 70}) and eAP ({@code 76}) teams
 * count, "atendendo as condições previstas na Portaria GM/MS nº 3.493/2024" (SCNES, not checked).
 *
 * <p>The type of an INE is the one valid on {@code end} ({@code validFrom <= end < validTo}: the
 * {@code team} capability's {@code valid_to} is exclusive, ADR 0031). When no state covers that day
 * the most recent state that began on or before it stands in ("a última competência válida", item
 * 11). No state at all is {@link Verdict#WITHOUT_TYPE}; two codes at once, {@link
 * Verdict#CONFLICT}; any code other than 70 and 76, {@link Verdict#OUT_OF_SCOPE}. Never a guess: a
 * team that is not considered is left out of the cohort with its reason and a disclosed count.
 */
public final class TeamScope {

    /** Team type {@code 70}: Equipe de Saúde da Família. */
    public static final String ESF = "70";

    /** Team type {@code 76}: Equipe de Atenção Primária. */
    public static final String EAP = "76";

    /** Evidence reason: the INE has no team type in the source. */
    public static final String REASON_WITHOUT_TYPE = "EXCLUIDO_EQUIPE_SEM_TIPO";

    /** Evidence reason: the INE carries two different types on the same day. */
    public static final String REASON_CONFLICT = "EXCLUIDO_TIPO_EQUIPE_CONFLITANTE";

    /** Evidence reason: a known type other than 70 and 76 (eMulti, eSB, EMAD...). */
    public static final String REASON_OUT_OF_SCOPE = "EXCLUIDO_EQUIPE_FORA_DO_ESCOPO";

    /** Evidence reason of the practice a person of an eAP 76 team is credited with, without the event. */
    public static final String REASON_CREDITED_EAP76 = "PRATICA_CREDITADA_EAP76";

    private static final Decision NO_INE = new Decision(Verdict.WITHOUT_TYPE, null);

    private final LocalDate end;
    private final Map<String, List<CanonicalTeam>> byIne = new HashMap<>();
    private final Map<String, Decision> decided = new HashMap<>();

    /** What the rule says about one INE. */
    public enum Verdict {
        ESF,
        EAP,
        OUT_OF_SCOPE,
        WITHOUT_TYPE,
        CONFLICT
    }

    /**
     * The decision for one INE.
     *
     * @param typeCode the code when a single one is valid, otherwise {@code null}
     */
    public record Decision(Verdict verdict, String typeCode) {

        /** Whether the team counts: eSF 70 or eAP 76. */
        public boolean considered() {
            return verdict == Verdict.ESF || verdict == Verdict.EAP;
        }

        public boolean eap76() {
            return verdict == Verdict.EAP;
        }

        /** The evidence reason of a team that is not considered, {@code null} when it is. */
        public String exclusionReason() {
            return switch (verdict) {
                case ESF, EAP -> null;
                case OUT_OF_SCOPE -> REASON_OUT_OF_SCOPE;
                case WITHOUT_TYPE -> REASON_WITHOUT_TYPE;
                case CONFLICT -> REASON_CONFLICT;
            };
        }
    }

    private TeamScope(List<CanonicalTeam> teams, LocalDate end) {
        this.end = end;
        for (CanonicalTeam team : teams) {
            if (team.ine() != null && team.teamTypeCode() != null) {
                byIne.computeIfAbsent(team.ine().strip(), ignored -> new ArrayList<>())
                        .add(team);
            }
        }
    }

    /** The scope of the teams {@code teams} on {@code end}, the last day of the competência. */
    public static TeamScope of(List<CanonicalTeam> teams, LocalDate end) {
        return new TeamScope(teams, end);
    }

    /** The decision for {@code ine}; an absent INE has no type. */
    public Decision decide(String ine) {
        if (ine == null || ine.isBlank()) {
            return NO_INE;
        }
        return decided.computeIfAbsent(ine.strip(), this::resolve);
    }

    private Decision resolve(String ine) {
        List<CanonicalTeam> states = byIne.getOrDefault(ine, List.of());
        SortedSet<String> codes = new TreeSet<>();
        for (CanonicalTeam state : states) {
            if (state.validOn(end)) {
                codes.add(state.teamTypeCode().strip());
            }
        }
        if (codes.isEmpty()) {
            codes = mostRecentBefore(states);
        }
        if (codes.isEmpty()) {
            return new Decision(Verdict.WITHOUT_TYPE, null);
        }
        if (codes.size() > 1) {
            return new Decision(Verdict.CONFLICT, null);
        }
        String code = codes.first();
        return new Decision(
                ESF.equals(code) ? Verdict.ESF : EAP.equals(code) ? Verdict.EAP : Verdict.OUT_OF_SCOPE, code);
    }

    /** No state covers {@code end}: the codes of the latest state that began on or before it. */
    private SortedSet<String> mostRecentBefore(List<CanonicalTeam> states) {
        LocalDate latest = null;
        SortedSet<String> codes = new TreeSet<>();
        for (CanonicalTeam state : states) {
            LocalDate from = state.validFrom() == null ? LocalDate.MIN : LocalDate.parse(state.validFrom());
            if (from.isAfter(end)) {
                continue;
            }
            if (latest == null || from.isAfter(latest)) {
                latest = from;
                codes.clear();
            }
            if (from.equals(latest)) {
                codes.add(state.teamTypeCode().strip());
            }
        }
        return codes;
    }
}
