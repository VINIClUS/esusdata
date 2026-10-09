package esusdata.indicator.pack.c7;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.SourceRef;
import esusdata.indicator.pack.PackSupport;
import esusdata.indicator.reconciliation.MethodologyProbe;
import esusdata.indicator.reconciliation.ProbeContext;
import esusdata.indicator.reconciliation.ProbeContext.PackProbeContext;
import esusdata.indicator.reconciliation.ProbeDiff;
import esusdata.indicator.reconciliation.ProbeDiff.Divergence;
import esusdata.indicator.reconciliation.ProbeResult;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * The methodology probes of C7 (Portão D, spec 2026-10-08 §9.4): the detectors that tell which
 * edition of the ficha the SIAPS applied to a quadrimestre. A probe is a pure function of its
 * context: it reruns the rule on a rewritten copy of each month's accepted dataset and compares the
 * teams of the revision with the baseline ({@link ProbeDiff}); it reads nothing of the official side
 * but the teams of the revision.
 *
 * <p>The molecular HPV exam is observable from the competência 2026-01, the first one for which the
 * pack binds its SIGTAP code into the extract ({@link C7Codes#procedureCodes}), with the 60-month
 * window of the footnote ({@link C7Pack#parts}), in both exam parts ({@code procedure_performed} and
 * {@code exam_request_evaluation}); both queries match {@code procedure_codes} by equality and filter
 * the period and the births only. Before then the rule does not count the code anyway, so the other
 * readings coincide with it and there is nothing for the probe to find.
 *
 * <p>{@code affected} counts only what lies in a team of the revision (spec §9.5: "da revisão"); the
 * count outside it goes to the local detail.
 */
public final class C7MethodologyProbes {

    /** Research row {@code c7.exam.molecular-hpv-validity} (docs/indicadores/portoes/edicoes-oficiais-siaps.md, 6.7). */
    static final String MOLECULAR_HPV_VALIDITY = "c7.exam.molecular-hpv-validity";

    /** The first day of the competência the footnote 4 of the 2026 ficha starts counting the exam in. */
    private static final LocalDate COUNTED_FROM = C7Codes.HPV_MOLECULAR_DESDE.atDay(1);

    private static final String COMPONENT_A = C7Subgroup.A.name();

    private static final String NOT_COVERED = "the extract of at least one month was not read for the window the rule"
            + " asks, so the rule gave that month no result and its molecular HPV exams are not observed";

    private C7MethodologyProbes() {}

    /** Every probe of C7, in the order of the dimensions of its profile. */
    public static List<MethodologyProbe> all() {
        return List.of(new MolecularHpvValidity());
    }

    /**
     * The candidate official readings of 2026Q1 for SIGTAP 02.02.10.025-1 (research 6.7, n2); the
     * local reading counts every record the 60 months hold.
     */
    private enum Reading {
        /** (a) the 2025 edition, which did not list the code: the exam does not count. */
        NOT_COUNTED("(a) exam not counted"),
        /** (c) the exam counts only when its record is dated from the competência 2026-01 on. */
        COUNTED_FROM_JANUARY_2026("(c) exam counted from 2026-01");

        private final String label;

        Reading(String label) {
            this.label = label;
        }

        /** Whether this reading takes the record out of the count. */
        boolean drops(CanonicalProcedureEvent event) {
            return isMolecular(event) && (this == NOT_COUNTED || datedBeforeCountedFrom(event));
        }
    }

    /**
     * {@code c7.exam.molecular-hpv-validity}. Candidate readings: (a) the exam does not count; (c) it
     * counts only when registered from 2026-01 on. The probe diverges where either does ({@link
     * Divergence#plus}); (c) drops a subset of the records (a) does, so (a) alone bounds it.
     *
     * <p>Technique: the molecular records each reading drops are removed from the dataset and the
     * rule is run again. Unit of {@code affected}: people (women, and trans men, of 25 to 64 years) of
     * a team of the revision whose support for practice A, in the baseline of some month, includes a
     * molecular exam record the reading drops; counted once across candidates and months. The rule
     * lists every distinct qualifying record as support ({@code C7Practices.decide}), not the first it
     * finds, so the count does not depend on the order of the records. "Registered" is the date of the
     * record, the attendance's ({@code event_date} is {@code tb_dim_tempo.dt_registro}), not the day a
     * result was typed in.
     */
    private static final class MolecularHpvValidity implements MethodologyProbe {

        @Override
        public String id() {
            return MOLECULAR_HPV_VALIDITY;
        }

        @Override
        public Set<String> packs() {
            return Set.of(C7Pack.ID);
        }

        @Override
        public ProbeResult evaluate(ProbeContext context) {
            if (context instanceof PackProbeContext pack && C7Pack.ID.equals(pack.packId())) {
                return measure(pack);
            }
            return ProbeResult.none(
                    MOLECULAR_HPV_VALIDITY, "this probe reads the records of C7, and the context is not C7's");
        }
    }

    private static ProbeResult measure(PackProbeContext context) {
        long covered =
                context.inputs().stream().filter(C7MethodologyProbes::covered).count();
        if (covered == 0) {
            return ProbeResult.none(MOLECULAR_HPV_VALIDITY, NOT_COVERED);
        }
        List<Finding> findings = Stream.of(Reading.values())
                .map(reading -> find(context, reading))
                .toList();
        Set<String> people = findings.stream()
                .flatMap(finding -> finding.people().stream())
                .collect(Collectors.toCollection(TreeSet::new));
        Divergence divergence = findings.stream().map(Finding::divergence).reduce(Divergence.NONE, Divergence::plus);
        List<String> detail =
                new ArrayList<>(findings.stream().map(Finding::line).toList());
        detail.addAll(divergence.localDetail());
        if (covered == context.inputs().size()) {
            return ProbeResult.complete(MOLECULAR_HPV_VALIDITY, people.size(), divergence.teams(), detail);
        }
        return ProbeResult.partial(MOLECULAR_HPV_VALIDITY, people.size(), divergence.teams(), NOT_COVERED, detail);
    }

    /**
     * Whether the extract of the month was read for what the rule asks. A part not read leaves the
     * rule without a result for the month ({@code UNSUPPORTED_SOURCE}, no team), never with a partial
     * one. An extract that declares no window (a synthetic one) is taken as complete.
     */
    private static boolean covered(PackInput input) {
        List<PartRequirement> parts =
                input.rule().requirements(input.context().competencia()).parts();
        return PackSupport.uncoveredParts(input.data(), parts).isEmpty();
    }

    /** What one candidate reading does to the quadrimestre: who it applies to and which teams it moves. */
    private record Finding(Reading reading, Set<String> people, int outside, Divergence divergence) {

        String line() {
            return reading.label + ": " + people.size() + " people in teams of the revision, " + outside
                    + " outside it (not counted); " + divergence.teams() + " team(s) of the revision diverge";
        }
    }

    private static Finding find(PackProbeContext context, Reading reading) {
        Map<String, String> revision = context.revisionTeams();
        List<RuleOutcome> alternative = context.inputs().stream()
                .map(input -> input.rule().evaluate(without(input.data(), reading), input.context()))
                .toList();
        Set<String> inside = new TreeSet<>();
        Set<String> outside = new TreeSet<>();
        for (int month = 0; month < alternative.size(); month++) {
            Set<SourceRef> dropped = droppedRecords(context.inputs().get(month).data(), reading);
            Map<Boolean, Set<String>> people = supported(context.baseline().get(month), dropped, revision);
            inside.addAll(people.get(true));
            outside.addAll(people.get(false));
        }
        Divergence divergence = ProbeDiff.compare(context.baseline(), alternative, revision);
        return new Finding(reading, inside, outside.size(), divergence);
    }

    /**
     * The people whose support for A in the baseline includes one of the {@code dropped} records,
     * split by whether their team is a team of the revision. The team is the link on the person's own
     * row: the support rows carry the INE of the event.
     */
    private static Map<Boolean, Set<String>> supported(
            RuleOutcome baseline, Set<SourceRef> dropped, Map<String, String> revision) {
        Map<String, String> teamOf = new HashMap<>();
        for (EvidenceItem row : baseline.evidence()) {
            if (row.component() == null && row.decision() == EvidenceDecision.ELIGIBLE) {
                teamOf.put(row.subjectKey(), row.ine());
            }
        }
        return baseline.evidence().stream()
                .filter(row -> supportsAWith(row, dropped))
                .collect(Collectors.partitioningBy(
                        row -> inRevision(teamOf.get(row.subjectKey()), revision),
                        Collectors.mapping(EvidenceItem::subjectKey, Collectors.toSet())));
    }

    private static boolean supportsAWith(EvidenceItem row, Set<SourceRef> dropped) {
        return row.decision() == EvidenceDecision.SUPPORTING_EVENT
                && COMPONENT_A.equals(row.component())
                && dropped.contains(row.sourceRef());
    }

    /** The map of the revision is a TreeMap, which refuses a null key. */
    private static boolean inRevision(String ine, Map<String, String> revision) {
        return ine != null && revision.containsKey(ine);
    }

    private static Set<SourceRef> droppedRecords(CanonicalDataset data, Reading reading) {
        return data.procedureEvents().stream()
                .filter(reading::drops)
                .map(CanonicalProcedureEvent::sourceRef)
                .collect(Collectors.toSet());
    }

    /** The dataset without the molecular records the reading drops, and nothing else changed. */
    private static CanonicalDataset without(CanonicalDataset data, Reading reading) {
        CanonicalDataset.Builder copy = CanonicalDataset.builder();
        data.windows().forEach(copy::window);
        data.encounters().forEach(copy::encounter);
        recordsOf(data).stream().filter(item -> kept(item, reading)).forEach(copy::add);
        return copy.build();
    }

    private static boolean kept(Record item, Reading reading) {
        return !(item instanceof CanonicalProcedureEvent event) || !reading.drops(event);
    }

    /** Every record of the v2 kinds of the dataset. */
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

    /** The code is compared as the rule compares it: no dots, hyphens or spaces. */
    private static boolean isMolecular(CanonicalProcedureEvent event) {
        return C7Codes.A_SIGTAP_HPV_MOLECULAR.equals(C7Codes.normalized(event.sigtapCode()));
    }

    /**
     * ISO dates order as text, so a record the rule does not read (it parses only the models and
     * stages it accepts) cannot make the probe throw; a record without a date is not dropped.
     */
    private static boolean datedBeforeCountedFrom(CanonicalProcedureEvent event) {
        return event.eventDate() != null && event.eventDate().compareTo(COUNTED_FROM.toString()) < 0;
    }
}
