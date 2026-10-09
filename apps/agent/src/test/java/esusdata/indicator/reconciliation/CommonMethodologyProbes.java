package esusdata.indicator.reconciliation;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.RecordKind;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamTimeline;
import esusdata.indicator.model.TeamTimeline.Resolution;
import esusdata.indicator.reconciliation.ProbeContext.NotaFinalProbeContext;
import esusdata.indicator.reconciliation.ProbeContext.PackProbeContext;
import esusdata.indicator.reconciliation.ProbeDiff.Divergence;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
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
 *   <li>some: the rule runs again on each month where one changes, with the type of the first day
 *       holding through the month ({@link #firstDayTypes}); {@code affected} is the number of those
 *       teams and {@code divergent} the teams of the revision whose result changes.
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
        if (context.inputs().stream().anyMatch(input -> input.data().teams().isEmpty())) {
            return ProbeResult.none(TEAM_TYPE_REFERENCE_DATE, NO_TEAM_STATES);
        }
        Set<String> revision = context.revisionTeams().keySet();
        Set<String> changed = new TreeSet<>();
        Set<String> fallback = new TreeSet<>();
        List<RuleOutcome> alternative = new ArrayList<>();
        for (int month = 0; month < context.inputs().size(); month++) {
            PackInput input = context.inputs().get(month);
            TeamTimeline timeline = TeamTimeline.of(input.data().teams());
            YearMonth competencia = input.context().competencia();
            Set<String> changedInMonth = new TreeSet<>();
            for (String ine : revision) {
                sort(
                        ine,
                        timeline.typeOn(ine, competencia.atDay(1)),
                        timeline.typeOn(ine, competencia.atEndOfMonth()),
                        changedInMonth,
                        fallback);
            }
            changed.addAll(changedInMonth);
            alternative.add(
                    changedInMonth.isEmpty()
                            ? context.baseline().get(month)
                            : input.rule()
                                    .evaluate(
                                            firstDayTypes(input.data(), competencia, changedInMonth), input.context()));
        }
        Divergence divergence = ProbeDiff.compare(context.baseline(), alternative, context.revisionTeams());
        List<String> detail = new ArrayList<>(divergence.localDetail());
        if (!changed.isEmpty()) {
            detail.add(changed.size() + " team(s) of the revision with another type on the first day of some month: "
                    + String.join(", ", changed));
        }
        if (!fallback.isEmpty()) {
            detail.add(fallback.size() + " team(s) of the revision read through their current type in some month: "
                    + String.join(", ", fallback));
        }
        return fallback.isEmpty()
                ? ProbeResult.complete(TEAM_TYPE_REFERENCE_DATE, changed.size(), divergence.teams(), detail)
                : ProbeResult.partial(
                        TEAM_TYPE_REFERENCE_DATE, changed.size(), divergence.teams(), FALLBACK_READ, detail);
    }

    /**
     * The dataset of {@code month} as the other reading takes it: each team of {@code ines} keeps
     * through the month the type it has on the first day. The state valid on the first day runs to the
     * first day of the next month, a state that begins inside the month begins then instead, and one
     * that lies wholly inside the month is dropped. Every other record, and every other month of a
     * state, is kept as it is.
     */
    static CanonicalDataset firstDayTypes(CanonicalDataset data, YearMonth month, Set<String> ines) {
        LocalDate first = month.atDay(1);
        LocalDate next = month.plusMonths(1).atDay(1);
        CanonicalDataset.Builder builder = CanonicalDataset.builder();
        data.windows().forEach(builder::window);
        data.encounters().forEach(builder::encounter);
        for (RecordKind kind : RecordKind.values()) {
            for (Record each : recordsOf(data, kind)) {
                if (each instanceof CanonicalTeam team && ines.contains(team.ine())) {
                    heldFromFirstDay(team, first, next).ifPresent(state -> builder.add(kind, state));
                } else {
                    builder.add(kind, each);
                }
            }
        }
        return builder.build();
    }

    private static Optional<CanonicalTeam> heldFromFirstDay(CanonicalTeam state, LocalDate first, LocalDate next) {
        if (state.validOn(first)) {
            boolean endsInside =
                    state.validTo() != null && LocalDate.parse(state.validTo()).isBefore(next);
            return Optional.of(endsInside ? valid(state, state.validFrom(), next.toString()) : state);
        }
        boolean beginsInside = state.validFrom() != null
                && LocalDate.parse(state.validFrom()).isAfter(first)
                && LocalDate.parse(state.validFrom()).isBefore(next);
        if (!beginsInside) {
            return Optional.of(state);
        }
        boolean endsInside =
                state.validTo() != null && !LocalDate.parse(state.validTo()).isAfter(next);
        return endsInside ? Optional.empty() : Optional.of(valid(state, next.toString(), state.validTo()));
    }

    private static CanonicalTeam valid(CanonicalTeam state, String from, String to) {
        return new CanonicalTeam(
                state.sourceRef(),
                state.municipalityIbge(),
                state.ine(),
                state.cnes(),
                state.teamTypeCode(),
                state.observedAt(),
                from,
                to,
                state.typeSource());
    }

    /** Every kind, with no default: a new kind of record is a compile error here until it is copied. */
    private static List<? extends Record> recordsOf(CanonicalDataset data, RecordKind kind) {
        return switch (kind) {
            case PERSON -> data.persons();
            case REGISTRATION -> data.registrations();
            case TEAM -> data.teams();
            case CARE_EVENT -> data.careEvents();
            case PROCEDURE_EVENT -> data.procedureEvents();
            case HOME_VISIT -> data.homeVisits();
            case IMMUNIZATION -> data.immunizations();
            case CONDITION -> data.conditions();
            case MEASUREMENT -> data.measurements();
            case PREGNANCY_OUTCOME -> data.pregnancyOutcomes();
        };
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
