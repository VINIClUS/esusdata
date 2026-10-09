package esusdata.indicator.pack.c1;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CboGroups;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.pack.PackSupport;
import esusdata.indicator.reconciliation.MethodologyProbe;
import esusdata.indicator.reconciliation.ProbeContext;
import esusdata.indicator.reconciliation.ProbeContext.PackProbeContext;
import esusdata.indicator.reconciliation.ProbeDiff;
import esusdata.indicator.reconciliation.ProbeDiff.Divergence;
import esusdata.indicator.reconciliation.ProbeResult;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * The methodology probes of C1 (Portão D, spec 2026-10-08 §9.4): the detectors that tell which
 * edition of the ficha the SIAPS applied to a quadrimestre. A probe is a pure function of its
 * context: it reruns the rule on a rewritten copy of each month's accepted dataset and compares the
 * teams of the revision with the baseline ({@link ProbeDiff}); it reads nothing of the official side
 * but the teams of the revision.
 *
 * <p>The records are what the C1 extract holds, which is every individual encounter of the
 * competência, whatever its CBO or team: the query filters on municipality and date only
 * ({@code individual_encounter_modality@0.1.0.sql}, WHERE) and {@link C1Pack#requirements} binds no
 * code list. A CBO the other reading drops is therefore observable. A month whose extract was not
 * read for the window of the encounters, or without the team part the rule reads beside them, is
 * left out of the counts (partial), and with no month read nothing is observable.
 *
 * <p>{@code affected} counts only what lies in a team of the revision (spec §9.5: "da revisão"); the
 * count outside it goes to the local detail.
 */
public final class C1MethodologyProbes {

    /** Research row {@code c1.cbo.list} (docs/indicadores/portoes/edicoes-oficiais-siaps.md, 6.1). */
    static final String CBO_LIST = "c1.cbo.list";

    /**
     * The two occupations the 2026 ficha added by its footnotes 1 and 2 (2251-25 Médico Clínico and
     * 2252-50 Médico Ginecologista e Obstetra); the 2025 edition listed the other five. The local
     * rule keeps all seven in every competência (C1-D1).
     */
    static final CboGroups ADDED_IN_2026 = CboGroups.of("225125", "225250");

    private static final String NOT_COVERED = "the extract of at least one month was not read for what C1 asks (the"
            + " encounters of the month and the team part), so that month is left out of the counts";

    private C1MethodologyProbes() {}

    /** Every probe of C1, in the order of the dimensions of its profile. */
    public static List<MethodologyProbe> all() {
        return List.of(new CboList());
    }

    /**
     * {@code c1.cbo.list}. Other reading: the 2025 edition's five CBOs, without 225125 and 225250, in
     * numerator and denominator and in all four months. The research's mixture (five CBOs in January to
     * March, seven in April) drops a subset of the same encounters, so this reading bounds it.
     *
     * <p>Technique: the encounters of the two CBOs are removed from the dataset and the rule is run
     * again. Removing them, rather than relabelling their CBO, keeps their team-type exclusion out of
     * the comparison. Unit of {@code affected}: encounters (source records) the baseline counted in
     * numerator or denominator, of those CBOs, in a team of the revision, added over the months read
     * (all four when the observability is complete).
     */
    private static final class CboList implements MethodologyProbe {

        @Override
        public String id() {
            return CBO_LIST;
        }

        @Override
        public Set<String> packs() {
            return Set.of(C1Rule.INDICATOR_PACK);
        }

        @Override
        public ProbeResult evaluate(ProbeContext context) {
            if (context instanceof PackProbeContext pack && C1Rule.INDICATOR_PACK.equals(pack.packId())) {
                return measure(pack);
            }
            return ProbeResult.none(CBO_LIST, "this probe reads the encounters of C1, and the context is not C1's");
        }
    }

    private static ProbeResult measure(PackProbeContext context) {
        List<Integer> read = readMonths(context.inputs());
        if (read.isEmpty()) {
            return ProbeResult.none(CBO_LIST, NOT_COVERED);
        }
        List<RuleOutcome> baseline = read.stream().map(context.baseline()::get).toList();
        List<RuleOutcome> alternative = read.stream()
                .map(context.inputs()::get)
                .map(input -> input.rule().evaluate(withoutAddedCbos(input.data()), input.context()))
                .toList();
        Divergence divergence =
                ProbeDiff.compare(comparable(baseline), comparable(alternative), context.revisionTeams());
        Affected affected = affected(baseline, context.revisionTeams());
        List<String> detail = new ArrayList<>(divergence.localDetail());
        detail.add(affected.line());
        if (read.size() == context.inputs().size()) {
            return ProbeResult.complete(CBO_LIST, affected.inRevision(), divergence.teams(), detail);
        }
        return ProbeResult.partial(CBO_LIST, affected.inRevision(), divergence.teams(), NOT_COVERED, detail);
    }

    /** The positions, in order, of the months whose extract was read for what the rule asks. */
    private static List<Integer> readMonths(List<PackInput> inputs) {
        return IntStream.range(0, inputs.size())
                .filter(month -> covered(inputs.get(month)))
                .boxed()
                .toList();
    }

    /**
     * Whether the extract of the month was read for what the rule asks: the encounters and, beside
     * them, the team part (ADR 0033). C1 checks neither itself. Without the encounter window it still
     * counts the encounters it holds, which can only be some; without the team part it judges by CBO
     * and modality alone and counts the encounters of every team type, which a run, that always reads
     * the part, never does. A month not covered is left out of the counts, so what is left is a lower
     * bound. An extract that declares no window (a synthetic one) is taken as complete.
     */
    private static boolean covered(PackInput input) {
        YearMonth month = input.context().competencia();
        List<PartRequirement> asked =
                new ArrayList<>(input.rule().requirements(month).parts());
        asked.addAll(input.rule().supplements(month));
        return PackSupport.uncoveredParts(input.data(), asked).isEmpty();
    }

    /** The dataset without the encounters of the CBOs the other reading drops, and nothing else changed. */
    private static CanonicalDataset withoutAddedCbos(CanonicalDataset data) {
        CanonicalDataset.Builder copy = CanonicalDataset.builder();
        data.windows().forEach(copy::window);
        data.encounters().stream()
                .filter(encounter -> !ADDED_IN_2026.matches(encounter.cbo()))
                .forEach(copy::encounter);
        recordsOf(data).forEach(copy::add);
        return copy.build();
    }

    /** Every record of the v2 kinds of the dataset, so the rewritten copy loses none that C1 may read. */
    private static List<Record> recordsOf(CanonicalDataset data) {
        return Stream.<List<? extends Record>>of(
                        data.persons(),
                        data.registrations(),
                        data.teams(),
                        data.careEvents(),
                        data.procedureEvents(),
                        data.homeVisits(),
                        data.immunizations(),
                        data.conditions(),
                        data.measurements(),
                        data.pregnancyOutcomes())
                .<Record>flatMap(List::stream)
                .toList();
    }

    /**
     * The outcomes as {@link ProbeDiff} should read them. It keys the teams by INE in a TreeMap, which
     * refuses a null key, and C1 gives a result of its own to the encounters without INE of a CBO
     * outside the ficha (the "teamless" bucket): it belongs to no team of the revision, so it is left
     * out. A team without denominator has no value that month, as one without encounters does (the
     * month is a "-" either way, and the team has no class), so it is left out too: removing the
     * encounters of the other reading must not turn a bucket of 0/0 into a difference.
     */
    private static List<RuleOutcome> comparable(List<RuleOutcome> outcomes) {
        return outcomes.stream()
                .map(outcome -> new RuleOutcome(
                        outcome.result(),
                        outcome.teams().stream()
                                .filter(C1MethodologyProbes::hasValueSlot)
                                .toList(),
                        outcome.evidence()))
                .toList();
    }

    private static boolean hasValueSlot(TeamResult team) {
        return team.ine() != null && team.result().status() != IndicatorStatus.NO_DENOMINATOR;
    }

    /** What the baseline counted of the added CBOs, in the teams of the revision and outside them. */
    private record Affected(int inRevision, int outside) {

        String line() {
            return "encounters of CBO 225125 and 225250 counted in the baseline: " + inRevision
                    + " in teams of the revision, " + outside + " outside it (not counted)";
        }
    }

    private static Affected affected(List<RuleOutcome> baseline, Map<String, String> revisionTeams) {
        int inRevision = 0;
        int outside = 0;
        for (RuleOutcome month : baseline) {
            for (EvidenceItem row : month.evidence()) {
                if (!counted(row) || !ADDED_IN_2026.matches(row.cbo())) {
                    continue;
                }
                if (inRevision(row.ine(), revisionTeams)) {
                    inRevision++;
                } else {
                    outside++;
                }
            }
        }
        return new Affected(inRevision, outside);
    }

    /** An encounter the baseline put in the numerator or the denominator. */
    private static boolean counted(EvidenceItem row) {
        return row.decision() == EvidenceDecision.IN_NUMERATOR || row.decision() == EvidenceDecision.DENOMINATOR_ONLY;
    }

    /** The map of the revision is a TreeMap, which refuses a null key. */
    private static boolean inRevision(String ine, Map<String, String> revisionTeams) {
        return ine != null && revisionTeams.containsKey(ine);
    }
}
