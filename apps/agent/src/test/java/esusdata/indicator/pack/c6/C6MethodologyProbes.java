package esusdata.indicator.pack.c6;

import esusdata.indicator.model.AgeAt;
import esusdata.indicator.model.AgeAt.AnniversaryRule;
import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.CboGroups;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.RecordKind;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamScope;
import esusdata.indicator.pack.c6.C6Practices.Practice;
import esusdata.indicator.reconciliation.MethodologyProbe;
import esusdata.indicator.reconciliation.ProbeContext;
import esusdata.indicator.reconciliation.ProbeContext.NotaFinalProbeContext;
import esusdata.indicator.reconciliation.ProbeContext.PackProbeContext;
import esusdata.indicator.reconciliation.ProbeDiff;
import esusdata.indicator.reconciliation.ProbeDiff.Divergence;
import esusdata.indicator.reconciliation.ProbeResult;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

/**
 * The methodology probes of C6 (spec 2026-10-08 §9.4), in the package of the pack because the
 * rewrites read the pack's own lists and conventions. Two are about the CBO of practice B (weight and
 * height the same day, Quadro 03), the two dimensions of C6 whose ficha changed between the editions
 * (K2 in {@code docs/indicadores/portoes/edicoes-oficiais-siaps.md}, n6 and n7), the ones that decide
 * the verdict. Two are declared conventions (K3, ADR 0034 §7): the ficha is silent in both editions,
 * so their probes only RECORD an effect in the dossier and never decide the verdict.
 *
 * <ul>
 *   <li>{@value #WEIGHT_HEIGHT}: footnote 4 of the 2026 ficha added seven groups (2232, 2234, 2236,
 *       2238, 2237, 2241 and 2239) to Quadro 03. The other reading is Quadro 03 without them.
 *   <li>{@value #TSB_3224}: footnote 1 of the 2026 ficha took 3224 (TSB) out of item 24 d. The local
 *       reading follows Quadro 03, where 3224 is not; the other reading counts it in B.
 *   <li>{@value #BIRTHDAY_RULE} (convention, C6-05, C6-12, C6-LIM-09): the local reading is the age in
 *       completed years on the last day of the competência, a 29/02 birthday falling on 01/03. The
 *       other reading is the age on the first day of the competência, a 29/02 birthday falling on
 *       28/02 (BN §10).
 *   <li>{@value #EAP_CREDIT} (convention, C6-D1, C6-LIM-14): the local reading credits practice C in
 *       full to the people of eAP 76 teams. The other reading gives no credit: C counts for them only
 *       when met. The BN §10 row also names "C excluded, renormalized over 75" for them; a rewrite of
 *       the data cannot produce that scoring, so it is not computed.
 * </ul>
 *
 * <p>Each probe rewrites the dataset of each month, runs the rule on it, and reports the teams of the
 * revision whose result changes. The CBO probes re-attribute the individual-care, MIP/MIAC
 * measurement and procedure records of the CBO they move. The seven groups and 3224 are in no list
 * of C6 but Quadro 03 (A takes physicians and nurses, C takes ACS and TACS on the home visit, D takes
 * any professional), so the stand-ins make the rewrite exact: {@link #NO_LIST} is read nowhere, and
 * {@link #QUADRO_03_ONLY} is read by Quadro 03 alone. Home visits are left alone: B reads their
 * measures only when an ACS or TACS made the visit, under both readings.
 *
 * <p>The birthday probe gives each person whose eligibility by age differs between the two readings
 * a stand-in birth date in that month: a date whose age on the last day, by the rule, is on the same
 * side of 60 as the age the other reading computes on the first day. Birth is read nowhere else in
 * C6 ({@code C6Cohort.personExclusion}). People with several birth dates stay as they are. The
 * 29/02 channel alone is inert at 60 until 2100 (a person turning 60 on a 29/02 does so in a leap
 * year), so the effect it records is that of the first-day reference. The eAP probe retypes the
 * eAP 76 states of the INEs the rule reads as eAP in that month to 70, which changes nothing but the
 * credit: the team stays considered, and an INE that carries two types on the last day stays in
 * conflict.
 *
 * <p>Observability is complete. The pack binds only SIGTAP and immunobiological codes and the CBO is
 * filtered by the rule, so every record of the moved CBO is in the extract; the people the first-day
 * reading drops are a subset of those the rule reads, born up to 60 years before the last day; and
 * the team states are read whole.
 *
 * <p>{@code affected} counts people of the teams of the revision: for the CBO probes, those whose
 * practice B is met under one reading and not under the other in some month (people who also have a
 * weight and height of another CBO on some day keep B under both readings and are not counted); for
 * the birthday probe, those eligible under one reading and not under the other in some month; for
 * the eAP probe, those whose practice C counts under one reading and not under the other.
 */
public final class C6MethodologyProbes {

    /** The probe id the profiles use for the seven groups Quadro 03 added in 2026. */
    public static final String WEIGHT_HEIGHT = "c6.cbo.weight-height";

    /** The probe id the profiles use for the dental technician family (3224). */
    public static final String TSB_3224 = "c6.cbo.tsb-3224";

    /** The probe id the profiles use for the reference date and the 29/02 rule of the age. */
    public static final String BIRTHDAY_RULE = "c6.age.birthday-rule";

    /** The probe id the profiles use for the credit of practice C to the people of eAP 76 teams. */
    public static final String EAP_CREDIT = "c6.team.eap-credit";

    /** The CBO a record of the seven groups is re-attributed to: in no list of the pack. */
    static final String NO_LIST = "000000";

    /** The CBO a 3224 record is re-attributed to: one of the seven groups, read by Quadro 03 alone. */
    static final String QUADRO_03_ONLY = "224105";

    static final CboGroups SEVEN_GROUPS = CboGroups.of("2232", "2234", "2236", "2238", "2237", "2241", "2239");

    static final CboGroups TSB = CboGroups.of("3224");

    private static final String DECISION_ON_B = "decision on practice B";

    private static final String NOT_A_PACK =
            "The records C6 reads are in the source records of the pack, which the Nota Final context does not"
                    + " carry.";

    private static final String NOT_READ =
            "At least one month of the extract does not cover what C6 reads, so the rule gave no result there and"
                    + " nothing can be compared with the other reading.";

    private C6MethodologyProbes() {}

    private static String sevenGroupsOut(String cbo) {
        return SEVEN_GROUPS.matches(cbo) ? NO_LIST : cbo;
    }

    private static String tsbIn(String cbo) {
        return TSB.matches(cbo) ? QUADRO_03_ONLY : cbo;
    }

    /** The probes of C6, in the order of the research table. */
    public static List<MethodologyProbe> all() {
        return List.of(
                new RewriteProbe(
                        WEIGHT_HEIGHT,
                        (data, month) -> reattributed(data, C6MethodologyProbes::sevenGroupsOut),
                        practiceRow(Practice.B),
                        DECISION_ON_B),
                new RewriteProbe(
                        TSB_3224,
                        (data, month) -> reattributed(data, C6MethodologyProbes::tsbIn),
                        practiceRow(Practice.B),
                        DECISION_ON_B),
                new RewriteProbe(
                        BIRTHDAY_RULE,
                        C6MethodologyProbes::ageOnFirstDay,
                        C6MethodologyProbes::isEligibilityRow,
                        "eligibility"),
                new RewriteProbe(
                        EAP_CREDIT,
                        C6MethodologyProbes::withoutEapCredit,
                        practiceRow(Practice.C),
                        "decision on practice C"));
    }

    /** A probe that reads the rule on the dataset of each month rewritten to the other reading. */
    private record RewriteProbe(
            String id,
            BiFunction<CanonicalDataset, YearMonth, CanonicalDataset> rewrite,
            Predicate<EvidenceItem> decisionRow,
            String changed)
            implements MethodologyProbe {

        @Override
        public Set<String> packs() {
            return Set.of(C6Pack.ID);
        }

        @Override
        public ProbeResult evaluate(ProbeContext context) {
            return switch (context) {
                case PackProbeContext pack -> measure(this, pack);
                case NotaFinalProbeContext notaFinal -> ProbeResult.none(id, NOT_A_PACK);
            };
        }
    }

    private static ProbeResult measure(RewriteProbe probe, PackProbeContext context) {
        boolean everyMonthRead = context.baseline().stream()
                .noneMatch(outcome -> outcome.result().status() == IndicatorStatus.UNSUPPORTED_SOURCE);
        if (!everyMonthRead) {
            return ProbeResult.none(probe.id(), NOT_READ);
        }
        List<RuleOutcome> alternative = context.inputs().stream()
                .map(input -> input.rule()
                        .evaluate(
                                probe.rewrite()
                                        .apply(input.data(), input.context().competencia()),
                                input.context()))
                .toList();
        Divergence divergence = ProbeDiff.compare(context.baseline(), alternative, context.revisionTeams());
        Set<String> people = changedPeople(
                context.baseline(), alternative, context.revisionTeams().keySet(), probe.decisionRow());
        List<String> detail = new ArrayList<>(divergence.localDetail());
        detail.add(people.size() + " person(s) of the revision with another " + probe.changed());
        return ProbeResult.complete(probe.id(), people.size(), divergence.teams(), detail);
    }

    /** The dataset of {@code probeId} with the CBO of its three record kinds moved. */
    static CanonicalDataset reattributed(CanonicalDataset data, String probeId) {
        UnaryOperator<String> standIn = switch (probeId) {
            case WEIGHT_HEIGHT -> C6MethodologyProbes::sevenGroupsOut;
            case TSB_3224 -> C6MethodologyProbes::tsbIn;
            default -> throw new IllegalArgumentException("no CBO probe of C6 is " + probeId);
        };
        return reattributed(data, standIn);
    }

    private static CanonicalDataset reattributed(CanonicalDataset data, UnaryOperator<String> standIn) {
        return mapped(data, each -> reattributedRecord(each, standIn));
    }

    /** A copy of the dataset with {@code change} applied to every record: windows and encounters are kept. */
    private static CanonicalDataset mapped(CanonicalDataset data, UnaryOperator<Record> change) {
        CanonicalDataset.Builder builder = CanonicalDataset.builder();
        data.windows().forEach(builder::window);
        data.encounters().forEach(builder::encounter);
        for (RecordKind kind : RecordKind.values()) {
            for (Record each : recordsOf(data, kind)) {
                builder.add(kind, change.apply(each));
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

    // ---- the age on the first day of the competência

    /**
     * The dataset of {@code month} as the other reading of the age sees it: the people the two
     * readings decide differently get a stand-in birth date (see {@link #standInBirth}), everyone
     * else keeps theirs. A person with more than one birth date is not touched, so she stays
     * excluded as divergent under both.
     */
    static CanonicalDataset ageOnFirstDay(CanonicalDataset data, YearMonth month) {
        Map<String, Set<LocalDate>> births = new HashMap<>();
        for (CanonicalPerson person : data.persons()) {
            if (person.birthDate() != null) {
                births.computeIfAbsent(person.personKey(), key -> new TreeSet<>())
                        .add(LocalDate.parse(person.birthDate()));
            }
        }
        return mapped(data, each -> withOtherReadingOfAge(each, births, month));
    }

    private static Record withOtherReadingOfAge(Record each, Map<String, Set<LocalDate>> births, YearMonth month) {
        if (each instanceof CanonicalPerson person && person.birthDate() != null) {
            Set<LocalDate> own = births.get(person.personKey());
            if (own.size() == 1) {
                return new CanonicalPerson(
                        person.sourceRef(),
                        person.municipalityIbge(),
                        person.personKey(),
                        standInBirth(own.iterator().next(), month).toString(),
                        person.sex(),
                        person.genderIdentity(),
                        person.deathDate());
            }
        }
        return each;
    }

    /**
     * The birth date the rule must be given in {@code month} for the age it computes on the last day
     * (a 29/02 anniversary on 01/03) to be on the same side of 60 as the age on the first day with a
     * 29/02 anniversary on 28/02. The date itself is kept when the two readings agree. Otherwise a
     * date 61 (the other reading takes her as 60) or 59 years before the last day stands in: the rule
     * reads from a birth date nothing but the age against 60, so any date on the right side of 60
     * gives the same outcome.
     */
    static LocalDate standInBirth(LocalDate birth, YearMonth month) {
        LocalDate last = month.atEndOfMonth();
        boolean sixtyNow = sixty(birth, last, C6Cohort.ANNIVERSARY);
        boolean sixtyOtherReading = sixty(birth, month.atDay(1), AnniversaryRule.CLAMP_TO_MONTH_END);
        if (sixtyNow == sixtyOtherReading) {
            return birth;
        }
        long years = sixtyOtherReading ? C6Codes.MINIMUM_AGE_YEARS + 1L : C6Codes.MINIMUM_AGE_YEARS - 1L;
        return last.minusYears(years);
    }

    private static boolean sixty(LocalDate birth, LocalDate on, AnniversaryRule rule) {
        return AgeAt.completedYears(birth, on, rule) >= C6Codes.MINIMUM_AGE_YEARS;
    }

    // ---- the credit of practice C to eAP

    /**
     * The dataset of {@code month} with no credit for eAP: the 76 states of the INEs the rule reads
     * as eAP on the last day become 70. Nothing else of the dataset changes. An INE the rule does not
     * read as eAP (two types on the last day, another type, none) is left as it is.
     */
    static CanonicalDataset withoutEapCredit(CanonicalDataset data, YearMonth month) {
        TeamScope scope = TeamScope.of(data.teams(), month.atEndOfMonth());
        return mapped(data, each -> each instanceof CanonicalTeam team && readAsEap(team, scope) ? asEsf(team) : each);
    }

    private static boolean readAsEap(CanonicalTeam team, TeamScope scope) {
        return team.ine() != null
                && team.teamTypeCode() != null
                && C6Codes.TEAM_TYPE_EAP.equals(team.teamTypeCode().strip())
                && scope.decide(team.ine()).eap76();
    }

    private static CanonicalTeam asEsf(CanonicalTeam team) {
        return new CanonicalTeam(
                team.sourceRef(),
                team.municipalityIbge(),
                team.ine(),
                team.cnes(),
                C6Codes.TEAM_TYPE_ESF,
                team.observedAt(),
                team.validFrom(),
                team.validTo(),
                team.typeSource());
    }

    // ---- who changed

    /** The people of the revision whose decision row is not the same in some month of the two outcomes. */
    private static Set<String> changedPeople(
            List<RuleOutcome> baseline,
            List<RuleOutcome> alternative,
            Set<String> revision,
            Predicate<EvidenceItem> decisionRow) {
        Set<String> changed = new TreeSet<>();
        for (int month = 0; month < baseline.size(); month++) {
            Map<String, EvidenceItem> was = decisionRows(baseline.get(month), decisionRow);
            Map<String, EvidenceItem> is = decisionRows(alternative.get(month), decisionRow);
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

    /** The decision row of each person of a month that {@code decisionRow} selects, by person key. */
    private static Map<String, EvidenceItem> decisionRows(RuleOutcome outcome, Predicate<EvidenceItem> decisionRow) {
        Map<String, EvidenceItem> rows = new TreeMap<>();
        for (EvidenceItem item : outcome.evidence()) {
            if (item.subjectKind() == EvidenceSubjectKind.PERSON && decisionRow.test(item)) {
                rows.put(item.subjectKey(), item);
            }
        }
        return rows;
    }

    /** The row that says whether a practice is met for an eligible person. */
    private static Predicate<EvidenceItem> practiceRow(Practice practice) {
        return item -> practice.name().equals(item.component())
                && (item.decision() == EvidenceDecision.PRACTICE_MET
                        || item.decision() == EvidenceDecision.PRACTICE_NOT_MET);
    }

    /** The row that says whether a person is in the cohort: eligible, or excluded. */
    private static boolean isEligibilityRow(EvidenceItem item) {
        return item.component() == null
                && (item.decision() == EvidenceDecision.ELIGIBLE || item.decision() == EvidenceDecision.EXCLUDED);
    }
}
