package esusdata.indicator.pack.c5;

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

/**
 * The methodology probes of C5 (spec 2026-10-08 §9.4, research row {@code c5.cbo.bp-measurement}),
 * in the package of the pack because the rewrite reads the pack's own CBO lists.
 *
 * <p>{@value #BP_MEASUREMENT}: practice B takes a blood-pressure record from the CBO of Quadro 03
 * of 2026 ({@code C5Codes#CBO_AFERICAO_PA}), which leaves the community health agent (5151-05) out
 * and keeps the dental technician family (3224) in. The 2025 reading (E25) is the opposite on
 * both: the ACS counts and 3224 does not. The probe re-attributes every individual-care,
 * measurement and procedure record of those two CBO to a stand-in that the other quadros read the
 * way the original is read, runs the rule on that dataset for each month, and reports the teams of
 * the revision whose result changes. {@link #ACS_STAND_IN} (a Quadro 03 and Quadro 04 CBO of no
 * other quadro) answers like the ACS in every list the rewritten records meet except Quadro 03,
 * and {@link #NO_LIST} answers like 3224 in every list except Quadro 03. The seven groups that
 * Quadro 03 added in 2026 are left alone.
 *
 * <p>Observability is never complete. B reads the pressure field of the individual-care header, the
 * SIGTAP code in a MIAI or MIP procedure and the pressure of a collective-activity participant. The
 * extract holds the first two with their CBO and no filter on it ({@code care_encounter} and {@code
 * procedure_performed} queries), so both halves are measured there. It does not hold the pressure of
 * a home visit (MIVDT), which the {@code home_visit} query leaves out (lacuna L6), nor of a
 * collective-activity participant (MIAC), which {@code measurement_record} writes as null; the ACS
 * works mostly in those forms, so its half is a lower bound. The 3224 half removes support the rule
 * had, all of it in records the extract holds.
 *
 * <p>{@code affected} counts people of the teams of the revision whose practice B is met under one
 * reading and not under the other in some month: the people whose pressure comes only from the
 * ACS, from 3224, or from both. People who also have a record of another CBO keep B met under
 * both readings and are not counted.
 */
public final class C5MethodologyProbes {

    /** The probe id the profiles use for the CBO of the blood-pressure record. */
    public static final String BP_MEASUREMENT = "c5.cbo.bp-measurement";

    /**
     * The CBO the ACS record is re-attributed to: a family (2241) that Quadros 03 and 04 list and
     * that Quadro 02 does not, so it is accepted for B and C and refused for A, as the ACS is for A
     * and, in Quadro 04, for C.
     */
    static final String ACS_STAND_IN = "224105";

    /** The CBO the 3224 record is re-attributed to: in no list of the pack, as 3224 is in none but Quadro 03. */
    static final String NO_LIST = "000000";

    private static final CboGroups ACS = CboGroups.of("5151-05");
    private static final CboGroups TSB = CboGroups.of("3224");

    private static final String NOT_A_PACK =
            "The CBO of a blood-pressure record is read from the source records of the pack, which the Nota Final"
                    + " context does not carry.";

    private static final String NOT_READ =
            "At least one month of the extract does not cover what C5 reads, so the rule gave no result there and"
                    + " nothing can be compared with the other reading.";

    private static final String FORMS_NOT_READ =
            "The blood pressure that a community health agent writes in the home-visit form (MIVDT) or for a"
                    + " participant of a collective activity (MIAC) is not in the extract, because neither has a"
                    + " pressure column in the canonical record: the ACS reading is measured only through the"
                    + " individual-care and SIGTAP records. The counts are lower bounds.";

    private C5MethodologyProbes() {}

    /** The probes of C5, in the order of the research table. */
    public static List<MethodologyProbe> all() {
        return List.of(new BpMeasurement());
    }

    /** The CBO that count for the blood-pressure record: the ACS in and 3224 out. */
    private static final class BpMeasurement implements MethodologyProbe {

        @Override
        public String id() {
            return BP_MEASUREMENT;
        }

        @Override
        public Set<String> packs() {
            return Set.of(C5Pack.ID);
        }

        @Override
        public ProbeResult evaluate(ProbeContext context) {
            return switch (context) {
                case PackProbeContext pack -> measure(pack);
                case NotaFinalProbeContext notaFinal -> ProbeResult.none(BP_MEASUREMENT, NOT_A_PACK);
            };
        }
    }

    private static ProbeResult measure(PackProbeContext context) {
        boolean everyMonthRead = context.baseline().stream()
                .noneMatch(outcome -> outcome.result().status() == IndicatorStatus.UNSUPPORTED_SOURCE);
        if (!everyMonthRead) {
            return ProbeResult.none(BP_MEASUREMENT, NOT_READ);
        }
        List<RuleOutcome> alternative = context.inputs().stream()
                .map(input -> input.rule().evaluate(reattributed(input.data()), input.context()))
                .toList();
        Divergence divergence = ProbeDiff.compare(context.baseline(), alternative, context.revisionTeams());
        Set<String> people = changedPeople(
                context.baseline(), alternative, context.revisionTeams().keySet());
        List<String> detail = new ArrayList<>(divergence.localDetail());
        detail.add(people.size() + " person(s) of the revision with another decision on practice B");
        return ProbeResult.partial(BP_MEASUREMENT, people.size(), divergence.teams(), FORMS_NOT_READ, detail);
    }

    /** The dataset with the ACS and 3224 records of the rule's three record kinds re-attributed. */
    static CanonicalDataset reattributed(CanonicalDataset data) {
        CanonicalDataset.Builder builder = CanonicalDataset.builder();
        data.windows().forEach(builder::window);
        data.encounters().forEach(builder::encounter);
        for (RecordKind kind : RecordKind.values()) {
            for (Record each : recordsOf(data, kind)) {
                builder.add(kind, reattributedRecord(each));
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

    private static Record reattributedRecord(Record each) {
        if (each instanceof CanonicalCareEvent event) {
            return withStandIn(event);
        }
        if (each instanceof CanonicalMeasurement measurement) {
            return withStandIn(measurement);
        }
        if (each instanceof CanonicalProcedureEvent procedure) {
            return withStandIn(procedure);
        }
        return each;
    }

    /** The CBO to read instead: the ACS becomes the stand-in, 3224 becomes the CBO of no list, the rest stays. */
    private static String standIn(String cbo) {
        if (ACS.matches(cbo)) {
            return ACS_STAND_IN;
        }
        return TSB.matches(cbo) ? NO_LIST : cbo;
    }

    private static CanonicalCareEvent withStandIn(CanonicalCareEvent e) {
        return new CanonicalCareEvent(
                e.sourceRef(),
                e.municipalityIbge(),
                e.personKey(),
                e.careDate(),
                e.form(),
                standIn(e.cbo()),
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

    private static CanonicalMeasurement withStandIn(CanonicalMeasurement m) {
        return new CanonicalMeasurement(
                m.sourceRef(),
                m.municipalityIbge(),
                m.personKey(),
                m.measuredDate(),
                m.weightKg(),
                m.heightCm(),
                m.systolicMmhg(),
                m.diastolicMmhg(),
                standIn(m.cbo()),
                m.origin(),
                m.activityTypeCode(),
                m.healthPracticeCodes());
    }

    private static CanonicalProcedureEvent withStandIn(CanonicalProcedureEvent p) {
        return new CanonicalProcedureEvent(
                p.sourceRef(),
                p.municipalityIbge(),
                p.personKey(),
                p.eventDate(),
                p.sigtapCode(),
                p.stage(),
                standIn(p.cbo()),
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
                    && C5Practices.B.equals(item.component())
                    && (item.decision() == EvidenceDecision.PRACTICE_MET
                            || item.decision() == EvidenceDecision.PRACTICE_NOT_MET);
            if (practiceB) {
                rows.put(item.subjectKey(), item);
            }
        }
        return rows;
    }
}
