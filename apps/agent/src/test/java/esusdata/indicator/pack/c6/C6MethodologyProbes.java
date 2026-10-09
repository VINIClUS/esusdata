package esusdata.indicator.pack.c6;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CboGroups;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.RecordKind;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.pack.c6.C6Practices.Practice;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.UnaryOperator;

/**
 * The methodology probes of C6 (spec 2026-10-08 §9.4), in the package of the pack because the
 * rewrite reads the pack's own CBO lists. Both are about the CBO of practice B (weight and height
 * the same day, Quadro 03), the two dimensions of C6 whose ficha changed between the editions (K2
 * in {@code docs/indicadores/portoes/edicoes-oficiais-siaps.md}, n6 and n7); the K3 dimensions are
 * declared conventions (ADR 0034 §7) and have no probe here.
 *
 * <ul>
 *   <li>{@value #WEIGHT_HEIGHT}: footnote 4 of the 2026 ficha added seven groups (2232, 2234, 2236,
 *       2238, 2237, 2241 and 2239) to Quadro 03. The other reading is Quadro 03 without them.
 *   <li>{@value #TSB_3224}: footnote 1 of the 2026 ficha took 3224 (TSB) out of item 24 d. The local
 *       reading follows Quadro 03, where 3224 is not; the other reading counts it in B.
 * </ul>
 *
 * <p>Each probe re-attributes the individual-care, MIP/MIAC measurement and procedure records of
 * the CBO it moves, runs the rule on that dataset for each month, and reports the teams of the
 * revision whose result changes. The seven groups and 3224 are in no list of C6 but Quadro 03 (A
 * takes physicians and nurses, C takes ACS and TACS on the home visit, D takes any professional),
 * so the stand-ins make the rewrite exact: {@link #NO_LIST} is read nowhere, and {@link
 * #QUADRO_03_ONLY} is read by Quadro 03 alone. Home visits are left alone: B reads their measures
 * only when an ACS or TACS made the visit, under both readings.
 *
 * <p>Observability is complete. The pack binds only SIGTAP and immunobiological codes; the CBO is
 * filtered by the rule, so every record of the moved CBO is in the extract.
 *
 * <p>{@code affected} counts people of the teams of the revision whose practice B is met under one
 * reading and not under the other in some month. People who also have a weight and height of
 * another CBO on some day keep B under both readings and are not counted.
 */
public final class C6MethodologyProbes {

    /** The probe id the profiles use for the seven groups Quadro 03 added in 2026. */
    public static final String WEIGHT_HEIGHT = "c6.cbo.weight-height";

    /** The probe id the profiles use for the dental technician family (3224). */
    public static final String TSB_3224 = "c6.cbo.tsb-3224";

    /** The CBO a record of the seven groups is re-attributed to: in no list of the pack. */
    static final String NO_LIST = "000000";

    /** The CBO a 3224 record is re-attributed to: one of the seven groups, read by Quadro 03 alone. */
    static final String QUADRO_03_ONLY = "224105";

    static final CboGroups SEVEN_GROUPS = CboGroups.of("2232", "2234", "2236", "2238", "2237", "2241", "2239");

    static final CboGroups TSB = CboGroups.of("3224");

    private static final String NOT_A_PACK =
            "The CBO of a weight or height record is read from the source records of the pack, which the Nota"
                    + " Final context does not carry.";

    private static final String NOT_READ =
            "At least one month of the extract does not cover what C6 reads, so the rule gave no result there and"
                    + " nothing can be compared with the other reading.";

    private C6MethodologyProbes() {}

    /** The probes of C6, in the order of the research table. */
    public static List<MethodologyProbe> all() {
        return List.of(
                new CboProbe(WEIGHT_HEIGHT, cbo -> SEVEN_GROUPS.matches(cbo) ? NO_LIST : cbo),
                new CboProbe(TSB_3224, cbo -> TSB.matches(cbo) ? QUADRO_03_ONLY : cbo));
    }

    /** A probe that reads practice B with the CBO of some records moved. */
    private record CboProbe(String id, UnaryOperator<String> standIn) implements MethodologyProbe {

        @Override
        public Set<String> packs() {
            return Set.of(C6Pack.ID);
        }

        @Override
        public ProbeResult evaluate(ProbeContext context) {
            return switch (context) {
                case PackProbeContext pack -> measure(id, standIn, pack);
                case NotaFinalProbeContext notaFinal -> ProbeResult.none(id, NOT_A_PACK);
            };
        }
    }

    private static ProbeResult measure(String id, UnaryOperator<String> standIn, PackProbeContext context) {
        boolean everyMonthRead = context.baseline().stream()
                .noneMatch(outcome -> outcome.result().status() == IndicatorStatus.UNSUPPORTED_SOURCE);
        if (!everyMonthRead) {
            return ProbeResult.none(id, NOT_READ);
        }
        List<RuleOutcome> alternative = context.inputs().stream()
                .map(input -> input.rule().evaluate(reattributed(input.data(), standIn), input.context()))
                .toList();
        Divergence divergence = ProbeDiff.compare(context.baseline(), alternative, context.revisionTeams());
        Set<String> people = changedPeople(
                context.baseline(), alternative, context.revisionTeams().keySet());
        List<String> detail = new ArrayList<>(divergence.localDetail());
        detail.add(people.size() + " person(s) of the revision with another decision on practice B");
        return ProbeResult.complete(id, people.size(), divergence.teams(), detail);
    }

    /** The dataset of {@code probeId} with the CBO of its three record kinds moved. */
    static CanonicalDataset reattributed(CanonicalDataset data, String probeId) {
        CboProbe probe = all().stream()
                .map(CboProbe.class::cast)
                .filter(each -> each.id().equals(probeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no CBO probe of C6 is " + probeId));
        return reattributed(data, probe.standIn());
    }

    private static CanonicalDataset reattributed(CanonicalDataset data, UnaryOperator<String> standIn) {
        CanonicalDataset.Builder builder = CanonicalDataset.builder();
        data.windows().forEach(builder::window);
        data.encounters().forEach(builder::encounter);
        for (RecordKind kind : RecordKind.values()) {
            for (Record each : recordsOf(data, kind)) {
                builder.add(kind, reattributedRecord(each, standIn));
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

    private static Record reattributedRecord(Record each, UnaryOperator<String> standIn) {
        if (each instanceof CanonicalCareEvent event) {
            return withStandIn(event, standIn);
        }
        if (each instanceof CanonicalMeasurement measurement) {
            return withStandIn(measurement, standIn);
        }
        if (each instanceof CanonicalProcedureEvent procedure) {
            return withStandIn(procedure, standIn);
        }
        return each;
    }

    private static String moved(String cbo, UnaryOperator<String> standIn) {
        return cbo == null ? null : standIn.apply(cbo);
    }

    private static CanonicalCareEvent withStandIn(CanonicalCareEvent e, UnaryOperator<String> standIn) {
        return new CanonicalCareEvent(
                e.sourceRef(),
                e.municipalityIbge(),
                e.personKey(),
                e.careDate(),
                e.form(),
                moved(e.cbo(), standIn),
                e.cnes(),
                e.ine(),
                e.careTypeCode(),
                e.careLocationCode(),
                e.remote(),
                e.ciapCodes(),
                e.cidCodes(),
                e.proceduresRequested(),
                e.proceduresEvaluated(),
                e.proceduresPerformed(),
                e.weightKg(),
                e.heightCm(),
                e.systolicMmhg(),
                e.diastolicMmhg(),
                e.lmpDate(),
                e.gestationalAgeWeeks(),
                e.pregnant(),
                e.birthDate());
    }

    private static CanonicalMeasurement withStandIn(CanonicalMeasurement m, UnaryOperator<String> standIn) {
        return new CanonicalMeasurement(
                m.sourceRef(),
                m.municipalityIbge(),
                m.personKey(),
                m.measuredDate(),
                m.weightKg(),
                m.heightCm(),
                m.systolicMmhg(),
                m.diastolicMmhg(),
                moved(m.cbo(), standIn),
                m.origin(),
                m.activityTypeCode(),
                m.healthPracticeCodes());
    }

    private static CanonicalProcedureEvent withStandIn(CanonicalProcedureEvent p, UnaryOperator<String> standIn) {
        return new CanonicalProcedureEvent(
                p.sourceRef(),
                p.municipalityIbge(),
                p.personKey(),
                p.eventDate(),
                p.sigtapCode(),
                p.stage(),
                moved(p.cbo(), standIn),
                p.cnes(),
                p.ine(),
                p.origin());
    }

    /** The people of the revision whose practice B is decided differently in some month of the two outcomes. */
    private static Set<String> changedPeople(
            List<RuleOutcome> baseline, List<RuleOutcome> alternative, Set<String> revision) {
        Set<String> changed = new TreeSet<>();
        for (int month = 0; month < baseline.size(); month++) {
            Map<String, EvidenceItem> was = practiceRows(baseline.get(month));
            Map<String, EvidenceItem> is = practiceRows(alternative.get(month));
            Set<String> keys = new TreeSet<>(was.keySet());
            keys.addAll(is.keySet());
            for (String key : keys) {
                EvidenceItem before = was.get(key);
                EvidenceItem after = is.get(key);
                EvidenceItem any = before == null ? after : before;
                boolean decidedDifferently = before == null || after == null || before.decision() != after.decision();
                if (decidedDifferently && any.ine() != null && revision.contains(any.ine())) {
                    changed.add(key);
                }
            }
        }
        return changed;
    }

    /** The decision row of practice B of each eligible person of a month, by person key. */
    private static Map<String, EvidenceItem> practiceRows(RuleOutcome outcome) {
        Map<String, EvidenceItem> rows = new TreeMap<>();
        for (EvidenceItem item : outcome.evidence()) {
            boolean practiceB = item.subjectKind() == EvidenceSubjectKind.PERSON
                    && Practice.B.name().equals(item.component())
                    && (item.decision() == EvidenceDecision.PRACTICE_MET
                            || item.decision() == EvidenceDecision.PRACTICE_NOT_MET);
            if (practiceB) {
                rows.put(item.subjectKey(), item);
            }
        }
        return rows;
    }
}
