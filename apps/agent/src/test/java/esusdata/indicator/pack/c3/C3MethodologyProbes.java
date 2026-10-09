package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.RecordKind;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.reconciliation.MethodologyProbe;
import esusdata.indicator.reconciliation.ProbeContext;
import esusdata.indicator.reconciliation.ProbeContext.NotaFinalProbeContext;
import esusdata.indicator.reconciliation.ProbeContext.PackProbeContext;
import esusdata.indicator.reconciliation.ProbeDiff;
import esusdata.indicator.reconciliation.ProbeDiff.Divergence;
import esusdata.indicator.reconciliation.ProbeResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The methodology probes of C3 (spec 2026-10-08 §9.4, research row {@code c3.episode.end-date}),
 * in the package of the pack because the rewrite reads the pack's own code matching.
 *
 * <p>{@value #END_DATE}: the rule ends a pregnancy at the first recorded outcome in {@code (DUM,
 * DUM + 294]}, else at the resolution of W78 in the LPC, else at DUM + 294, and the puerperium is
 * the 42 days after (C3-LIM-06). Two official readings are candidates: E25, which ends it at DUM +
 * 294 always (FAQ Q32: the end is "calculado automaticamente"), and E26, which ends it at the
 * registered outcome date when there is one and at DUM + 294 otherwise (items 17 and 4.1). Neither
 * reads W78, so each is the rule with that step taken out. The probe rewrites the dataset of each
 * month that way, runs the rule on it, and reports the teams of the revision whose result changes
 * under either reading (the union of the candidates).
 *
 * <p>Observability is complete within a declared limitation (ADR 0034 §6). No capability reads the
 * pregnancy outcome ({@code C3Pack#requirements} lists none and {@code Capabilities} has no constant
 * for it: the DW has no outcome date, gap L2, and C3-LIM-06 declares it), so {@value #LIMITATION}
 * puts the E26 reading through registered outcomes outside the verdict. The probe names it and
 * counts exactly over the rest: the E25 reading, and E26 through W78. With no result for a month
 * there is nothing to compare, and the probe says so instead of counting zero.
 *
 * <p>{@code affected} counts episodes (subjects of the {@code EPISODE} evidence kind) of the teams
 * of the revision whose cohort row, its decision, reason or end date, is not the same under a
 * candidate reading in some month, or exists under one reading only: an end moved from D to DUM +
 * 294 also moves the puerperium, so the episode may enter or leave the cohort of a month.
 */
public final class C3MethodologyProbes {

    /** The probe id the profiles use for the end of the pregnancy. */
    public static final String END_DATE = "c3.episode.end-date";

    private static final String NOT_A_PACK =
            "The end of a pregnancy is read from the source records of the pack, which the Nota Final context does"
                    + " not carry.";

    private static final String NOT_READ =
            "At least one month of the extract does not cover what C3 reads, so the rule gave no result there and"
                    + " nothing can be compared with the other reading.";

    /** The declared limitation of the outcome date field (items 17 and 4.1 of the E26 ficha). */
    static final String LIMITATION = "oor.c3.outcome-date-field";

    private static final String OUTCOME_OUTSIDE =
            "The registered outcome date of the pregnancy (items 17 and 4.1 of the E26 ficha) is not in the data of"
                    + " this installation, so the E26 reading is measured through the W78 resolution it stops using,"
                    + " and the counts are exact over it.";

    private C3MethodologyProbes() {}

    /** The probes of C3, in the order of the research table. */
    public static List<MethodologyProbe> all() {
        return List.of(new EndDate());
    }

    /** The end of the pregnancy: the recorded outcome and the W78 resolution against DUM + 294. */
    private static final class EndDate implements MethodologyProbe {

        @Override
        public String id() {
            return END_DATE;
        }

        @Override
        public Set<String> packs() {
            return Set.of(C3Pack.ID);
        }

        @Override
        public ProbeResult evaluate(ProbeContext context) {
            return switch (context) {
                case PackProbeContext pack -> measure(pack);
                case NotaFinalProbeContext notaFinal -> ProbeResult.none(END_DATE, NOT_A_PACK);
            };
        }
    }

    private static ProbeResult measure(PackProbeContext context) {
        boolean everyMonthRead = context.baseline().stream()
                .noneMatch(outcome -> outcome.result().status() == IndicatorStatus.UNSUPPORTED_SOURCE);
        if (!everyMonthRead) {
            return ProbeResult.none(END_DATE, NOT_READ);
        }
        Set<String> revision = context.revisionTeams().keySet();
        Divergence divergence = Divergence.NONE;
        Set<String> affected = new TreeSet<>();
        for (List<RuleOutcome> alternative : alternatives(context)) {
            divergence = divergence.plus(ProbeDiff.compare(context.baseline(), alternative, context.revisionTeams()));
            affected.addAll(changedEpisodes(context.baseline(), alternative, revision));
        }
        List<String> detail = new ArrayList<>(divergence.localDetail());
        detail.add(
                affected.size() + " episode(s) of the revision with another cohort row under the candidate readings");
        return ProbeResult.completeWithin(
                END_DATE, affected.size(), divergence.teams(), List.of(LIMITATION), OUTCOME_OUTSIDE, detail);
    }

    /**
     * The outcomes of each month under each candidate reading. With no registered outcome in the
     * dataset E26 is E25, so it runs once; the extract has none today.
     */
    private static List<List<RuleOutcome>> alternatives(PackProbeContext context) {
        List<List<RuleOutcome>> readings = new ArrayList<>();
        readings.add(run(context, true));
        boolean outcomesRecorded = context.inputs().stream()
                .anyMatch(input -> !input.data().pregnancyOutcomes().isEmpty());
        if (outcomesRecorded) {
            readings.add(run(context, false));
        }
        return readings;
    }

    private static List<RuleOutcome> run(PackProbeContext context, boolean outcomesIgnored) {
        return context.inputs().stream()
                .map(input -> input.rule()
                        .evaluate(
                                outcomesIgnored ? endingAt294(input.data()) : endingAtOutcomeOr294(input.data()),
                                input.context()))
                .toList();
    }

    /** E25: no registered outcome and no W78 resolution, so the end is always DUM + 294. */
    static CanonicalDataset endingAt294(CanonicalDataset data) {
        return rewritten(data, true);
    }

    /** E26: the registered outcomes stay, the W78 resolution goes, so the end is the outcome or DUM + 294. */
    static CanonicalDataset endingAtOutcomeOr294(CanonicalDataset data) {
        return rewritten(data, false);
    }

    private static CanonicalDataset rewritten(CanonicalDataset data, boolean outcomesIgnored) {
        CanonicalDataset.Builder builder = CanonicalDataset.builder();
        data.windows().forEach(builder::window);
        data.encounters().forEach(builder::encounter);
        for (RecordKind kind : RecordKind.values()) {
            boolean dropped = outcomesIgnored && kind == RecordKind.PREGNANCY_OUTCOME;
            if (!dropped) {
                for (Record each : recordsOf(data, kind)) {
                    builder.add(kind, withoutResolution(each));
                }
            }
        }
        return builder.build();
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

    /** The W78 condition of the LPC without its resolution date, the only thing the rule reads it for. */
    private static Record withoutResolution(Record each) {
        if (each instanceof CanonicalCondition condition && pregnancyCondition(condition)) {
            return new CanonicalCondition(
                    condition.sourceRef(),
                    condition.municipalityIbge(),
                    condition.personKey(),
                    condition.codeSystem(),
                    condition.code(),
                    condition.recordedDate(),
                    condition.status(),
                    null,
                    condition.basis(),
                    condition.cbo());
        }
        return each;
    }

    /** The test of {@code Episodes#resolutionDates}: CIAP-2 W78, exact. */
    private static boolean pregnancyCondition(CanonicalCondition condition) {
        return CodeMatch.CIAP2.equals(C3Codes.token(condition.codeSystem()))
                && C3Codes.PREGNANCY_CONDITION_CIAP.equals(C3Codes.normalized(condition.code()));
    }

    /** The episodes of the revision whose cohort row differs between two outcomes of the same months. */
    private static Set<String> changedEpisodes(
            List<RuleOutcome> baseline, List<RuleOutcome> alternative, Set<String> revision) {
        Set<String> changed = new TreeSet<>();
        for (int month = 0; month < baseline.size(); month++) {
            Map<String, EvidenceItem> was = cohortRows(baseline.get(month));
            Map<String, EvidenceItem> is = cohortRows(alternative.get(month));
            Set<String> keys = new TreeSet<>(was.keySet());
            keys.addAll(is.keySet());
            for (String key : keys) {
                EvidenceItem before = was.get(key);
                EvidenceItem after = is.get(key);
                EvidenceItem any = before == null ? after : before;
                if (any.ine() != null && revision.contains(any.ine()) && !sameRow(before, after)) {
                    changed.add(key);
                }
            }
        }
        return changed;
    }

    /** The cohort row (eligible or excluded) of each episode of a month, by episode key. */
    private static Map<String, EvidenceItem> cohortRows(RuleOutcome outcome) {
        Map<String, EvidenceItem> rows = new TreeMap<>();
        for (EvidenceItem item : outcome.evidence()) {
            boolean cohort = item.subjectKind() == EvidenceSubjectKind.EPISODE
                    && item.component() == null
                    && (item.decision() == EvidenceDecision.ELIGIBLE || item.decision() == EvidenceDecision.EXCLUDED);
            if (cohort) {
                rows.put(item.subjectKey(), item);
            }
        }
        return rows;
    }

    private static boolean sameRow(EvidenceItem before, EvidenceItem after) {
        return before != null
                && after != null
                && before.decision() == after.decision()
                && Objects.equals(before.reasonCode(), after.reasonCode())
                && Objects.equals(before.eventDate(), after.eventDate());
    }
}
