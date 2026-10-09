package esusdata.indicator.pack.c2;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamScope;
import esusdata.indicator.reconciliation.MethodologyProbe;
import esusdata.indicator.reconciliation.ProbeContext;
import esusdata.indicator.reconciliation.ProbeContext.NotaFinalProbeContext;
import esusdata.indicator.reconciliation.ProbeContext.PackProbeContext;
import esusdata.indicator.reconciliation.ProbeDiff;
import esusdata.indicator.reconciliation.ProbeDiff.Divergence;
import esusdata.indicator.reconciliation.ProbeResult;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * The methodology probes of C2 for the Portão D @2 (spec 2026-10-08 §9.4; research {@code
 * edicoes-oficiais-siaps.md} §6.2 and §10.3). Each one measures, on the local data of a
 * quadrimestre, how much another reading of the ficha would change what the rule gives.
 *
 * <p><b>Technique.</b> The dataset of each month is rewritten into the one the other reading
 * implies, the real {@link C2Pack} runs on it, and {@link ProbeDiff} compares the outcome with the
 * baseline of the context. No rule is copied for that, so {@code divergent} (the teams of the
 * revision whose month result changes) is exactly what the rule would publish. What is mirrored is
 * only the selection of the records a reading concerns, which decides {@code affected}.
 *
 * <p><b>Unit of {@code affected}.</b> A child: a key that is eligible in the cohort of one of the
 * four months, is linked to a team of the revision, and either has a record the other reading
 * treats differently (selected as the rule selects it, minus the one condition the reading drops;
 * the windows of the practices are not applied) or has its evidence rows changed by the rewrite.
 * The second part makes {@code affected} never smaller than the children the rewrite touches,
 * whatever drift the mirrored selection may suffer. A child outside the revision is counted in the
 * local detail only.
 *
 * <p><b>Observability.</b> A month whose baseline is {@code UNSUPPORTED_SOURCE} (the extract lacks
 * a capability or a window that {@link C2Pack#requirements} asks for) has nothing to compare: with
 * no such month the probe is {@code COMPLETE}, with some it is {@code PARTIAL}, with all of them it
 * is {@code NONE}. A baseline that is supported proves every window of the requirements is covered,
 * because the rule checks all of its parts. The candidate readings of a probe that the extract
 * cannot show make it {@code PARTIAL} whatever the months are. Reasons carry no count and no
 * subject, as they are versioned as written.
 *
 * <p>The probes are final code for {@code c2-desenvolvimento-infantil@0.3.0}. Here are the two
 * {@code DIFFERENT} rows of the research (n6 and n7) and the {@code UNKNOWN} row that tells the
 * edition applied (n3); the other {@code UNKNOWN} rows (cohort, age, modality, same-day count,
 * second visit, vaccine, CBO) come with their own probes, and the {@code SAME} rows have none.
 */
public final class C2MethodologyProbes {

    /** Row n7: the official reading counts a visit of any outcome, with the motive filled (FAQ Q30). */
    public static final String VISIT_OUTCOME = "c2.visit.outcome";

    /** Row n6: the official reading counts a consultation whatever the type of the team (FAQ Q28 and Q29). */
    public static final String ENCOUNTER_TEAM_SCOPE = "c2.consult.encounter-team-scope";

    /** Row n3: which individual attendances are consultations of puericultura (rodapé 2 of the E26). */
    public static final String PUERICULTURA_FILTER = "c2.consult.puericultura-filter";

    private static final String NO_LIMIT = "";

    private static final String NO_BASELINE = "no month of the quadrimestre has a C2 baseline: the extract lacks a"
            + " capability or a window that C2Pack.requirements() asks for";

    private static final String SOME_WITHOUT_BASELINE = "at least one month of the quadrimestre has no C2 baseline (a"
            + " capability or a window of C2Pack.requirements() is missing from the extract), so only the other"
            + " months were measured";

    private static final String NOT_A_PACK =
            "the Nota Final is not a rule over source records: the C2 probes read the" + " dataset of the pack";

    private static final String FIELD_NOT_IN_EXTRACT = "candidate (ii) needs the PEC Puericultura field, which no"
            + " column of care_encounter carries (lacuna L7: only the evaluated CIAP-2 and CID-10 codes are read),"
            + " so only candidate (i), attendances of a doctor or nurse that identify a problem without A98 or Z001,"
            + " was measured and both counts are lower bounds";

    private static final Pattern CODE_PUNCTUATION = Pattern.compile("[.\\-\\s]");

    private C2MethodologyProbes() {}

    /** The probes of C2 in the order of the research matrix. */
    public static List<MethodologyProbe> all() {
        return List.of(
                new ReadingProbe(VISIT_OUTCOME, new VisitOutcome(), NO_LIMIT),
                new ReadingProbe(ENCOUNTER_TEAM_SCOPE, new EncounterTeamScope(), NO_LIMIT),
                new ReadingProbe(PUERICULTURA_FILTER, new PuericulturaFilter(), FIELD_NOT_IN_EXTRACT));
    }

    /** One other reading of the ficha: the dataset it implies and the children it concerns. */
    private interface Reading {

        /** The dataset of the month as the other reading takes it; the input is left as it is. */
        CanonicalDataset alternative(PackInput input);

        /** The children among {@code eligible} that have a record the other reading treats differently. */
        Set<String> candidates(PackInput input, Set<String> eligible);
    }

    /**
     * The generic probe: runs a {@link Reading} on the four months and reports the observability its
     * months and its {@code limit} allow.
     *
     * @param limit what the extract cannot show of the readings of this probe, or empty when nothing
     */
    private record ReadingProbe(String id, Reading reading, String limit) implements MethodologyProbe {

        @Override
        public Set<String> packs() {
            return Set.of(C2Pack.ID);
        }

        @Override
        public ProbeResult evaluate(ProbeContext context) {
            return switch (context) {
                case PackProbeContext pack -> measure(pack);
                case NotaFinalProbeContext notaFinal -> ProbeResult.none(id, NOT_A_PACK);
            };
        }

        private ProbeResult measure(PackProbeContext context) {
            Map<String, String> revision = context.revisionTeams();
            List<RuleOutcome> alternative = new ArrayList<>();
            Set<String> applies = new TreeSet<>();
            Set<String> inRevision = new TreeSet<>();
            List<String> unobserved = new ArrayList<>();
            for (int month = 0; month < context.inputs().size(); month++) {
                PackInput input = context.inputs().get(month);
                RuleOutcome baseline = context.baseline().get(month);
                if (supported(baseline)) {
                    RuleOutcome other = input.rule().evaluate(reading.alternative(input), input.context());
                    // A child counts when the reading applies to them on a team of the revision in any
                    // month: a later month on another team does not take that back.
                    appliesTo(input, baseline, other).forEach((child, ine) -> {
                        applies.add(child);
                        if (ine != null && revision.containsKey(ine)) {
                            inRevision.add(child);
                        }
                    });
                    alternative.add(other);
                } else {
                    unobserved.add(baseline.result().referencePeriod() + ": no C2 baseline (UNSUPPORTED_SOURCE)");
                    alternative.add(baseline);
                }
            }
            if (unobserved.size() == context.inputs().size()) {
                return ProbeResult.none(id, NO_BASELINE);
            }
            Divergence divergence = ProbeDiff.compare(context.baseline(), alternative, revision);
            List<String> detail = new ArrayList<>(divergence.localDetail());
            detail.addAll(unobserved);
            detail.add("children the other reading applies to: " + inRevision.size() + " on teams of the revision, "
                    + (applies.size() - inRevision.size()) + " on other teams (not counted)");
            return conclude(unobserved.isEmpty(), inRevision.size(), divergence.teams(), detail);
        }

        private ProbeResult conclude(boolean everyMonth, int affected, int divergent, List<String> detail) {
            List<String> limits = new ArrayList<>();
            if (!everyMonth) {
                limits.add(SOME_WITHOUT_BASELINE);
            }
            if (!limit.isEmpty()) {
                limits.add(limit);
            }
            return limits.isEmpty()
                    ? ProbeResult.complete(id, affected, divergent, detail)
                    : ProbeResult.partial(id, affected, divergent, String.join("; ", limits), detail);
        }

        /**
         * The children of the month the other reading applies to, key to the INE of their team. The
         * cohort must be the same under both readings: the rewrites only change what counts for a
         * practice, never who is in the denominator.
         */
        private Map<String, String> appliesTo(PackInput input, RuleOutcome baseline, RuleOutcome other) {
            Map<String, String> eligible = eligible(baseline);
            if (!eligible.equals(eligible(other))) {
                throw new IllegalStateException(id + ": the other reading changed who is in the cohort");
            }
            Set<String> children = new TreeSet<>(reading.candidates(input, eligible.keySet()));
            children.addAll(rowsChanged(baseline, other, eligible.keySet()));
            Map<String, String> applies = new TreeMap<>();
            children.forEach(child -> applies.put(child, eligible.get(child)));
            return applies;
        }
    }

    /**
     * Row n7. Official: a visit of any outcome counts for practice D, realizada, recusada or ausente,
     * as long as the motive is filled (FAQ Q30). Local: only "realizada" counts (AMB-C2-08 iv). The
     * other reading turns every filled outcome into "realizada" and leaves the rest to the rule, which
     * still asks for the motive, the ACS/TACS CBO and the child's life. A visit with no outcome stays
     * out under both readings: the FAQ speaks of the options of the field. Practice C reads the weight
     * and height of a visit whatever its outcome, so only D moves.
     */
    private static final class VisitOutcome implements Reading {

        @Override
        public CanonicalDataset alternative(PackInput input) {
            CanonicalDataset data = input.data();
            List<CanonicalHomeVisit> visits =
                    data.homeVisits().stream().map(VisitOutcome::counted).toList();
            return copy(data, data.careEvents(), visits, data.teams());
        }

        @Override
        public Set<String> candidates(PackInput input, Set<String> eligible) {
            CanonicalDataset data = input.data();
            Map<String, LocalDate> births = births(data, eligible);
            Set<String> children = new TreeSet<>();
            for (CanonicalHomeVisit visit : data.homeVisits()) {
                if (eligible.contains(visit.personKey())
                        && otherOutcome(visit)
                        && readByTheRule(visit)
                        && inLife(births.get(visit.personKey()), input.context().dataCutoff(), visit.visitDate())) {
                    children.add(visit.personKey());
                }
            }
            return children;
        }

        private static CanonicalHomeVisit counted(CanonicalHomeVisit visit) {
            if (!otherOutcome(visit)) {
                return visit;
            }
            return new CanonicalHomeVisit(
                    visit.sourceRef(),
                    visit.municipalityIbge(),
                    visit.personKey(),
                    visit.visitDate(),
                    visit.cbo(),
                    visit.cnes(),
                    visit.ine(),
                    C2Codes.VISIT_OUTCOME_DONE,
                    visit.reasonCodes(),
                    visit.weightKg(),
                    visit.heightCm());
        }

        /** An outcome that is filled and is not "realizada": what the official reading admits and the local does not. */
        private static boolean otherOutcome(CanonicalHomeVisit visit) {
            String outcome = visit.outcomeCode();
            return outcome != null && !outcome.isBlank() && !C2Codes.VISIT_OUTCOME_DONE.equals(outcome);
        }

        /** The motive and the CBO that {@code VisitPractice} asks for, whatever the outcome (AMB-C2-08 iii). */
        private static boolean readByTheRule(CanonicalHomeVisit visit) {
            boolean motive = visit.reasonCodes().contains(C2Codes.VISIT_REASON_NEWBORN)
                    || visit.reasonCodes().contains(C2Codes.VISIT_REASON_CHILD);
            return motive && C2Codes.VISIT.matches(visit.cbo());
        }
    }

    /**
     * Row n6. Official: a consultation counts whatever the type of the team of the professional (FAQ
     * Q28 and Q29). Local: the rule drops a consultation whose INE has a known type other than 70 and
     * 76, the type being the one valid on the last day of the competência; an INE with no type or two
     * types is accepted (AMB-C2-11 item 2, C2-LIM-06). The other reading drops the team states of
     * those INEs from the month's dataset, which makes their type unknown and so their consultations
     * accepted. The children linked to those teams are out of the cohort either way: {@code
     * C2Cohort} leaves out a team without type as it leaves out a team of another type. Nothing else
     * in the practices reads the type of an encounter's team ({@code ConsultPractices.consideredTeam}
     * is the only reader of {@code ChildRecords.teamTypes}), so only practices A and B can move.
     */
    private static final class EncounterTeamScope implements Reading {

        @Override
        public CanonicalDataset alternative(PackInput input) {
            CanonicalDataset data = input.data();
            Set<String> outside = outOfScope(data, input.context().competencia());
            List<CanonicalTeam> teams = data.teams().stream()
                    .filter(team ->
                            team.ine() == null || !outside.contains(team.ine().strip()))
                    .toList();
            return copy(data, data.careEvents(), data.homeVisits(), teams);
        }

        @Override
        public Set<String> candidates(PackInput input, Set<String> eligible) {
            Set<String> outside = outOfScope(input.data(), input.context().competencia());
            return consultChildren(input, eligible, event -> puericulture(event) && fromOutside(event, outside));
        }
    }

    /**
     * Row n3. The local rule recognises puericultura by CIAP-2 A98 or CID-10 Z001 among the evaluated
     * problems (AMB-C2-05). Candidate (i), the E25 reading: no filter, any individual attendance of a
     * doctor or nurse that identifies a problem or condition counts; the rewrite adds A98 to the
     * problems of every such attendance that lacks A98 and Z001, and leaves the CBO, the child's life,
     * the modality and the team of the rule as they are. Candidate (ii), the PEC Puericultura field
     * required besides A98 and Z001, is not observable: the field is no column of the extract (lacuna
     * L7). The A98 and Z001 pair it writes (Guia T1) would be a proxy and not a guaranteed lower bound,
     * since AMB-C2-05 notes that the professional can remove the automatic line, so it is left out of
     * the counts. In 2026Q1 the mixture the research asks for (January to March under the E25, April
     * under the E26) diverges on a subset of the teams and children of (i), so it adds nothing to the
     * union and is not computed. A98 and Z001 are read by {@code ConsultPractices.childCare} alone, so
     * the rewrite reaches practices A and B and no other.
     */
    private static final class PuericulturaFilter implements Reading {

        @Override
        public CanonicalDataset alternative(PackInput input) {
            CanonicalDataset data = input.data();
            List<CanonicalCareEvent> events = data.careEvents().stream()
                    .map(PuericulturaFilter::unfiltered)
                    .toList();
            return copy(data, events, data.homeVisits(), data.teams());
        }

        @Override
        public Set<String> candidates(PackInput input, Set<String> eligible) {
            Set<String> outside = outOfScope(input.data(), input.context().competencia());
            return consultChildren(
                    input,
                    eligible,
                    event -> !fromOutside(event, outside) && hasProblem(event) && !puericulture(event));
        }

        private static CanonicalCareEvent unfiltered(CanonicalCareEvent event) {
            if (!C2Codes.INDIVIDUAL_FORM.equals(event.form()) || !hasProblem(event) || puericulture(event)) {
                return event;
            }
            List<String> ciap = new ArrayList<>(event.ciapCodes());
            ciap.add(C2Codes.PUERICULTURE_CIAP);
            return new CanonicalCareEvent(
                    event.sourceRef(),
                    event.municipalityIbge(),
                    event.personKey(),
                    event.careDate(),
                    event.form(),
                    event.cbo(),
                    event.cnes(),
                    event.ine(),
                    event.careTypeCode(),
                    event.careLocationCode(),
                    event.remote(),
                    ciap,
                    event.cidCodes(),
                    event.proceduresRequested(),
                    event.proceduresEvaluated(),
                    event.proceduresPerformed(),
                    event.weightKg(),
                    event.heightCm(),
                    event.systolicMmhg(),
                    event.diastolicMmhg(),
                    event.lmpDate(),
                    event.gestationalAgeWeeks(),
                    event.pregnant(),
                    event.birthDate());
        }
    }

    // ---- the dataset

    /** The dataset with the care events, visits and team states given and everything else, windows included, as it was. */
    private static CanonicalDataset copy(
            CanonicalDataset data,
            List<CanonicalCareEvent> careEvents,
            List<CanonicalHomeVisit> homeVisits,
            List<CanonicalTeam> teams) {
        CanonicalDataset.Builder builder = CanonicalDataset.builder();
        data.windows().forEach(builder::window);
        data.encounters().forEach(builder::encounter);
        data.persons().forEach(builder::add);
        data.registrations().forEach(builder::add);
        teams.forEach(builder::add);
        careEvents.forEach(builder::add);
        data.procedureEvents().forEach(builder::add);
        homeVisits.forEach(builder::add);
        data.immunizations().forEach(builder::add);
        data.conditions().forEach(builder::add);
        data.measurements().forEach(builder::add);
        data.pregnancyOutcomes().forEach(builder::add);
        return builder.build();
    }

    /** The INEs whose single type on the last day of the month is known and is neither 70 nor 76. */
    private static Set<String> outOfScope(CanonicalDataset data, YearMonth month) {
        TeamScope scope = TeamScope.of(data.teams(), month.atEndOfMonth());
        Set<String> outside = new HashSet<>();
        for (CanonicalTeam team : data.teams()) {
            if (team.ine() != null && scope.decide(team.ine()).verdict() == TeamScope.Verdict.OUT_OF_SCOPE) {
                outside.add(team.ine().strip());
            }
        }
        return outside;
    }

    // ---- the selection of the records a reading concerns

    /**
     * The children among {@code eligible} with an individual attendance that the rule would read as a
     * consultation (doctor or nurse CBO, inside the child's life) but for the conditions {@code rest}
     * decides.
     */
    private static Set<String> consultChildren(
            PackInput input, Set<String> eligible, Predicate<CanonicalCareEvent> rest) {
        CanonicalDataset data = input.data();
        Map<String, LocalDate> births = births(data, eligible);
        Set<String> children = new TreeSet<>();
        for (CanonicalCareEvent event : data.careEvents()) {
            if (eligible.contains(event.personKey())
                    && C2Codes.INDIVIDUAL_FORM.equals(event.form())
                    && C2Codes.CONSULT.matches(event.cbo())
                    && inLife(births.get(event.personKey()), input.context().dataCutoff(), event.careDate())
                    && rest.test(event)) {
                children.add(event.personKey());
            }
        }
        return children;
    }

    private static Map<String, LocalDate> births(CanonicalDataset data, Set<String> children) {
        Map<String, LocalDate> births = new HashMap<>();
        for (CanonicalPerson person : data.persons()) {
            if (children.contains(person.personKey())) {
                births.putIfAbsent(person.personKey(), LocalDate.parse(person.birthDate()));
            }
        }
        return births;
    }

    /** Inside the child's life and not after the cutoff, as {@code ChildRecords.inScope} reads it. */
    private static boolean inLife(LocalDate birth, LocalDate cutoff, String date) {
        LocalDate day = LocalDate.parse(date);
        return birth != null && !day.isBefore(birth) && !day.isAfter(cutoff);
    }

    /** The encounter's INE is one the rule drops for its team type; the lookup is by the INE as written, as the rule's. */
    private static boolean fromOutside(CanonicalCareEvent event, Set<String> outside) {
        return event.ine() != null && outside.contains(event.ine());
    }

    /** CIAP-2 A98 or CID-10 Z001 among the evaluated problems, ignoring case and punctuation (AMB-C2-05). */
    private static boolean puericulture(CanonicalCareEvent event) {
        return event.ciapCodes().stream().anyMatch(code -> C2Codes.PUERICULTURE_CIAP.equals(normalized(code)))
                || event.cidCodes().stream().anyMatch(code -> C2Codes.PUERICULTURE_CID.equals(normalized(code)));
    }

    /** At least one evaluated CIAP-2 or CID-10 code: a problem or condition identified. */
    private static boolean hasProblem(CanonicalCareEvent event) {
        return event.ciapCodes().stream().anyMatch(code -> !normalized(code).isEmpty())
                || event.cidCodes().stream().anyMatch(code -> !normalized(code).isEmpty());
    }

    private static String normalized(String code) {
        return code == null ? "" : CODE_PUNCTUATION.matcher(code).replaceAll("").toUpperCase(Locale.ROOT);
    }

    // ---- the outcomes

    private static boolean supported(RuleOutcome outcome) {
        return outcome.result().status() != IndicatorStatus.UNSUPPORTED_SOURCE;
    }

    /** The children in the denominator of the month, key to the INE of their team. */
    private static Map<String, String> eligible(RuleOutcome outcome) {
        Map<String, String> children = new TreeMap<>();
        for (EvidenceItem item : outcome.evidence()) {
            if (item.decision() == EvidenceDecision.ELIGIBLE) {
                children.put(item.subjectKey(), item.ine());
            }
        }
        return children;
    }

    /** The children whose evidence rows are not the same in the two outcomes. */
    private static Set<String> rowsChanged(RuleOutcome before, RuleOutcome after, Set<String> children) {
        Map<String, List<EvidenceItem>> was = rowsOf(before, children);
        Map<String, List<EvidenceItem>> is = rowsOf(after, children);
        Set<String> differ = new TreeSet<>();
        for (String child : children) {
            if (!was.getOrDefault(child, List.of()).equals(is.getOrDefault(child, List.of()))) {
                differ.add(child);
            }
        }
        return differ;
    }

    private static Map<String, List<EvidenceItem>> rowsOf(RuleOutcome outcome, Set<String> children) {
        Map<String, List<EvidenceItem>> rows = new HashMap<>();
        for (EvidenceItem item : outcome.evidence()) {
            if (item.subjectKind() == EvidenceSubjectKind.PERSON && children.contains(item.subjectKey())) {
                rows.computeIfAbsent(item.subjectKey(), key -> new ArrayList<>())
                        .add(item);
            }
        }
        return rows;
    }
}
