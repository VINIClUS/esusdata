package esusdata.indicator.reconciliation;

import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.TeamTimeline;
import esusdata.indicator.model.TeamTimeline.Resolution;
import esusdata.indicator.reconciliation.ProbeContext.NotaFinalProbeContext;
import esusdata.indicator.reconciliation.ProbeContext.PackProbeContext;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * The methodology probes every pack shares (spec 2026-10-08 §9.4; {@code common.*} in {@code
 * docs/indicadores/portoes/edicoes-oficiais-siaps.md}, section 5).
 *
 * <p>{@value #TEAM_TYPE_REFERENCE_DATE}: the packs read the team type valid on the last day of the
 * competência; the other reading is the type valid on its first day. Item 11 of the fichas («SCNES:
 * A última competência válida») does not say which, the same in both editions, so the dimension is
 * a declared convention (K3, ADR 0034 §7): this probe records its effect outside the verdict. The
 * two readings differ only for a team of the revision whose type on the first day is not its type
 * on the last day, so the probe looks for those first:
 *
 * <ul>
 *   <li>none, and no state read is a {@link CanonicalTeam#CURRENT_FALLBACK}: both readings give
 *       every team of the revision the same type in every month, the rule gives the same result,
 *       and the probe is complete with zero;
 *   <li>none, but a type read is the team's current type standing in for an unaudited past: a
 *       change inside the month would not show, so zero is a lower bound;
 *   <li>some: the result of the other reading is not computed, and the probe says so.
 * </ul>
 */
public final class CommonMethodologyProbes {

    /** The probe id the profiles use for the day of the competência the team type is read on. */
    public static final String TEAM_TYPE_REFERENCE_DATE = "common.team.type-reference-date";

    private static final String NOT_A_PACK =
            "The team type is read from the team states of a pack's extract, which the Nota Final context does not"
                    + " carry.";

    private static final String NO_TEAM_STATES =
            "The extract carries no team state in some month, so the type of no team can be read on either day.";

    private static final String TYPE_CHANGED =
            "A team of the revision has another type on the first day of a month than on the last day, and the"
                    + " result of the reading on the first day is not computed.";

    private static final String FALLBACK_READ =
            "The type of a team of the revision is its current type standing in for a past the source did not"
                    + " audit, so a change of type inside the month would not show; zero is a lower bound.";

    private CommonMethodologyProbes() {}

    /** The probes every pack shares, in the order of the research table. */
    public static List<MethodologyProbe> all() {
        return List.of(new TeamTypeReferenceDate());
    }

    private static final class TeamTypeReferenceDate implements MethodologyProbe {

        @Override
        public String id() {
            return TEAM_TYPE_REFERENCE_DATE;
        }

        @Override
        public Set<String> packs() {
            return GatePack.all().stream().map(GatePack::packId).collect(Collectors.toUnmodifiableSet());
        }

        @Override
        public ProbeResult evaluate(ProbeContext context) {
            return switch (context) {
                case PackProbeContext pack -> measure(pack);
                case NotaFinalProbeContext notaFinal -> ProbeResult.none(TEAM_TYPE_REFERENCE_DATE, NOT_A_PACK);
            };
        }
    }

    private static ProbeResult measure(PackProbeContext context) {
        Set<String> revision = context.revisionTeams().keySet();
        Set<String> changed = new TreeSet<>();
        Set<String> fallback = new TreeSet<>();
        for (PackInput input : context.inputs()) {
            if (input.data().teams().isEmpty()) {
                return ProbeResult.none(TEAM_TYPE_REFERENCE_DATE, NO_TEAM_STATES);
            }
            TeamTimeline timeline = TeamTimeline.of(input.data().teams());
            YearMonth month = input.context().competencia();
            for (String ine : revision) {
                sort(
                        ine,
                        timeline.typeOn(ine, month.atDay(1)),
                        timeline.typeOn(ine, month.atEndOfMonth()),
                        changed,
                        fallback);
            }
        }
        if (!changed.isEmpty()) {
            return ProbeResult.none(TEAM_TYPE_REFERENCE_DATE, TYPE_CHANGED);
        }
        List<String> detail =
                List.of(fallback.size() + " team(s) of the revision read through their current type in some month: "
                        + String.join(", ", fallback));
        return fallback.isEmpty()
                ? ProbeResult.complete(TEAM_TYPE_REFERENCE_DATE, 0, 0, List.of())
                : ProbeResult.partial(TEAM_TYPE_REFERENCE_DATE, 0, 0, FALLBACK_READ, detail);
    }

    /** Puts {@code ine} in {@code changed} when its two days differ, else in {@code fallback} when either stands in. */
    private static void sort(String ine, Resolution first, Resolution last, Set<String> changed, Set<String> fallback) {
        if (!sameType(first, last)) {
            changed.add(ine);
            return;
        }
        if (standsIn(first) || standsIn(last)) {
            fallback.add(ine);
        }
    }

    private static boolean sameType(Resolution first, Resolution last) {
        return first.kind() == last.kind() && Objects.equals(first.codes(), last.codes());
    }

    private static boolean standsIn(Resolution resolution) {
        return CanonicalTeam.CURRENT_FALLBACK.equals(resolution.typeSource());
    }
}
