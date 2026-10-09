package esusdata.indicator.pack.c7;

import esusdata.indicator.model.AgeAt;
import esusdata.indicator.model.AgeAt.AnniversaryRule;
import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
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
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
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
 *
 * <p>{@value #AGE_AND_WINDOW_CIVIL} is a declared convention (K3, ADR 0034 §7), not a detector of the
 * edition: its result is recorded in the dossier and never decides the verdict.
 */
public final class C7MethodologyProbes {

    /** Research row {@code c7.exam.molecular-hpv-validity} (docs/indicadores/portoes/edicoes-oficiais-siaps.md, 6.7). */
    static final String MOLECULAR_HPV_VALIDITY = "c7.exam.molecular-hpv-validity";

    /** The first day of the competência the footnote 4 of the 2026 ficha starts counting the exam in. */
    private static final LocalDate COUNTED_FROM = C7Codes.HPV_MOLECULAR_DESDE.atDay(1);

    /** Research row {@code c7.age-and-window.civil} (6.7, n7): a declared convention, outside the verdict. */
    static final String AGE_AND_WINDOW_CIVIL = "c7.age-and-window.civil";

    private static final String COMPONENT_A = C7Subgroup.A.name();

    /** The civil-month windows of the ficha: A (36), the molecular exam (60), D (24) and C (12). */
    private static final List<Integer> WINDOW_MONTHS = List.of(
            C7Subgroup.A.months(), C7Subgroup.HPV_MOLECULAR_MONTHS, C7Subgroup.D.months(), C7Subgroup.C.months());

    private static final String NOT_C7 = "this probe reads the records of C7, and the context is not C7's";

    private static final String NOT_READ_AT_ALL = "at least one month of the extract was not read for the window the"
            + " rule asks, so the rule gave it no result and nothing can be compared with the other reading";

    private static final String HELD_BACK =
            "The other reading takes the age on the first day of the competência, so it puts into a"
                    + " subgroup whoever crosses its upper age limit during the month: B at 14 (turns 15), C and D at 69"
                    + " (turns 70). The extract binds the births by the last day, so the immunizations, encounters, exams"
                    + " and problems of those people are not in it. They stay as the baseline has them, and the counts are"
                    + " lower bounds. A up to 64 is inside the bind of the screening parts and is computed, and so is anyone"
                    + " whose birthday falls on the last day. The dose upper bound, the link, the death and the team type stay"
                    + " at the last day under both readings.";

    private static final String NOT_COVERED = "the extract of at least one month was not read for the window the rule"
            + " asks, so the rule gave that month no result and its molecular HPV exams are not observed";

    private C7MethodologyProbes() {}

    /** Every probe of C7, in the order of the dimensions of its profile. */
    public static List<MethodologyProbe> all() {
        return List.of(new MolecularHpvValidity(), new AgeAndWindowCivil());
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

    // ---- c7.age-and-window.civil

    /**
     * {@code c7.age-and-window.civil}, a declared convention (C7-LIM-08; K3, ADR 0034 §7): the result is
     * recorded in the dossier and never decides the verdict. The local reading is the age in completed
     * years on the last day of the competência, a 29/02 birthday on 01/03, and windows of N civil months
     * ending at the end of the competência. The other reading (BN §10) is the age on the first day, a
     * 29/02 birthday on 28/02, and windows counted in days.
     *
     * <p>Technique: the rule is run again on a rewritten copy of each month's dataset, so that its own
     * conventions see the other reading.
     *
     * <ul>
     *   <li>Windows in days. N months are read as 365·N/12 days (12 months are 365, as in the C6 row of
     *       BN §10), ending on the last day. That window is never longer than the civil one, so every
     *       record it needs is in the extract. A record dated in the days the civil window has and the day
     *       window has not (the first one or two days, only when a 29/02 lies in the window) is dated the
     *       day before the civil window starts. At most one window can lose a record that way, because
     *       the starts are at least 12 months apart and the difference is at most two days: the windows
     *       that keep the record still hold the new date, and those that did not still do not. Doses of B
     *       have no window and are not touched.
     *   <li>Age on the first day. A person whose subgroups are the same at both ages keeps her birth
     *       date. Whoever would be in other subgroups gets a birth date {@code age} years before the last
     *       day, which gives the rule that age. B counts a dose from the 9th birthday, which depends on the
     *       birth date and not on the age (the 9th birthday under the rule's 29/02 convention, 01/03, and
     *       under the other, 28/02), so her doses lying between the bound the rule will use and the bound
     *       of the other reading are moved onto the rule's bound (counted), or the day before it (not
     *       counted). This is also the only place the 29/02 convention reaches C7: a dose dated 28/02 of
     *       the year of the 9th birthday of a girl born on a 29/02.
     * </ul>
     *
     * <p>Unit of {@code affected}: people of a team of the revision whose subgroups or the decision on
     * one of them differ between the two readings in some month. It counts decision rows, not support
     * rows, because the repair of the doses and the move of the records change the supports.
     *
     * <p>Observability: always PARTIAL when every month was read, and NONE when one was not (the rule gave
     * it no result). What the extract cannot hold is the records of whoever crosses an upper age limit
     * during the month ({@link #HELD_BACK}); the data decides who: a person is left as in the baseline
     * when the birth bind of any part that a subgroup she would join reads does not hold her. Absence
     * from the extract cannot be told from absence in the world, so there is no month where it is
     * COMPLETE.
     */
    private static final class AgeAndWindowCivil implements MethodologyProbe {

        @Override
        public String id() {
            return AGE_AND_WINDOW_CIVIL;
        }

        @Override
        public Set<String> packs() {
            return Set.of(C7Pack.ID);
        }

        @Override
        public ProbeResult evaluate(ProbeContext context) {
            if (context instanceof PackProbeContext pack && C7Pack.ID.equals(pack.packId())) {
                return civil(pack);
            }
            return ProbeResult.none(AGE_AND_WINDOW_CIVIL, NOT_C7);
        }
    }

    private static ProbeResult civil(PackProbeContext context) {
        boolean everyMonthRead = context.baseline().stream()
                .noneMatch(outcome -> outcome.result().status() == IndicatorStatus.UNSUPPORTED_SOURCE);
        if (!everyMonthRead) {
            return ProbeResult.none(AGE_AND_WINDOW_CIVIL, NOT_READ_AT_ALL);
        }
        List<RuleOutcome> alternative = new ArrayList<>();
        Set<String> heldBack = new TreeSet<>();
        for (PackInput input : context.inputs()) {
            YearMonth month = input.context().competencia();
            Map<String, Plan> plans = plans(input.data().persons(), month);
            plans.forEach((key, plan) -> {
                if (plan.heldBack()) {
                    heldBack.add(key);
                }
            });
            alternative.add(input.rule().evaluate(otherReading(input.data(), plans, month), input.context()));
        }
        Map<String, String> revision = context.revisionTeams();
        Divergence divergence = ProbeDiff.compare(context.baseline(), alternative, revision);
        Set<String> people = changedPeople(context.baseline(), alternative, revision);
        List<String> detail = new ArrayList<>(divergence.localDetail());
        detail.add(people.size() + " person(s) of the revision with other subgroups or another decision");
        detail.add(heldBack.size() + " person(s) crossing an upper age limit in a month, left as in the baseline"
                + " (their records are outside the extract)");
        return ProbeResult.partial(AGE_AND_WINDOW_CIVIL, people.size(), divergence.teams(), HELD_BACK, detail);
    }

    /**
     * What the rewrite does to one person in one month.
     *
     * @param standIn the birth date the rule is given instead of hers, or {@code null} for her own
     * @param ruleBound the 9th birthday the rule will compute, set when she is in B under the other reading
     * @param otherBound the 9th birthday the other reading computes, set together with {@code ruleBound}
     * @param heldBack the other reading needs records of hers that the extract cannot hold: she stays as
     *     the baseline has her
     */
    record Plan(LocalDate standIn, LocalDate ruleBound, LocalDate otherBound, boolean heldBack) {

        static final Plan UNCHANGED = new Plan(null, null, null, false);

        static final Plan HELD_BACK = new Plan(null, null, null, true);
    }

    /** The plan of each person (by key) for {@code month}, from the persons rows of the dataset. */
    static Map<String, Plan> plans(List<CanonicalPerson> persons, YearMonth month) {
        List<PartRequirement> parts = C7Pack.parts(month);
        Map<String, List<CanonicalPerson>> byKey = new TreeMap<>();
        for (CanonicalPerson person : persons) {
            byKey.computeIfAbsent(person.personKey(), key -> new ArrayList<>()).add(person);
        }
        Map<String, Plan> plans = new TreeMap<>();
        byKey.forEach((key, rows) -> plans.put(key, plan(rows, month, parts)));
        return plans;
    }

    private static Plan plan(List<CanonicalPerson> rows, YearMonth month, List<PartRequirement> parts) {
        CanonicalPerson first = rows.getFirst();
        if (first.birthDate() == null || disagree(rows)) {
            return Plan.UNCHANGED;
        }
        LocalDate birth = LocalDate.parse(first.birthDate());
        boolean transMan = C7Codes.SEXO_MASCULINO.equals(first.sex())
                && C7Codes.IDENTIDADE_HOMEM_TRANSGENERO.equals(first.genderIdentity());
        long now = ageOn(birth, month.atEndOfMonth(), C7Cohort.ANNIVERSARY);
        long other = ageOn(birth, month.atDay(1), AnniversaryRule.CLAMP_TO_MONTH_END);
        Set<C7Subgroup> inNow = membership(now, transMan);
        Set<C7Subgroup> inOther = membership(other, transMan);
        LocalDate used = birth;
        if (!inNow.equals(inOther)) {
            Set<C7Subgroup> joined = EnumSet.noneOf(C7Subgroup.class);
            joined.addAll(inOther);
            joined.removeAll(inNow);
            if (!joined.isEmpty() && !held(birth, joined, parts)) {
                return Plan.HELD_BACK;
            }
            used = month.atEndOfMonth().minusYears(other);
        }
        LocalDate standIn = used.equals(birth) ? null : used;
        if (inOther.contains(C7Subgroup.B)) {
            return new Plan(
                    standIn,
                    AgeAt.anniversaryYears(used, C7Subgroup.B.minAge(), C7Cohort.ANNIVERSARY),
                    AgeAt.anniversaryYears(birth, C7Subgroup.B.minAge(), AnniversaryRule.CLAMP_TO_MONTH_END),
                    false);
        }
        return new Plan(standIn, null, null, false);
    }

    /** The cohort decides a person with disagreeing rows as conflicting, under both readings. */
    private static boolean disagree(List<CanonicalPerson> rows) {
        CanonicalPerson first = rows.getFirst();
        return rows.stream()
                .anyMatch(row -> !Objects.equals(row.birthDate(), first.birthDate())
                        || !Objects.equals(row.sex(), first.sex())
                        || !Objects.equals(row.genderIdentity(), first.genderIdentity()));
    }

    /** The age the cohort computes: a birth after {@code on} is -1. */
    private static long ageOn(LocalDate birth, LocalDate on, AnniversaryRule rule) {
        return birth.isAfter(on) ? -1 : AgeAt.completedYears(birth, on, rule);
    }

    private static Set<C7Subgroup> membership(long age, boolean transMan) {
        Set<C7Subgroup> in = EnumSet.noneOf(C7Subgroup.class);
        for (C7Subgroup subgroup : C7Subgroup.values()) {
            if (subgroup.includes(age, transMan)) {
                in.add(subgroup);
            }
        }
        return in;
    }

    /** Whether the birth is inside the birth bind of every part the {@code joined} subgroups read. */
    private static boolean held(LocalDate birth, Set<C7Subgroup> joined, List<PartRequirement> parts) {
        Set<String> capabilities = new TreeSet<>(List.of(Capabilities.CITIZEN, Capabilities.INDIVIDUAL_REGISTRATION));
        joined.forEach(subgroup -> capabilities.addAll(readBy(subgroup)));
        return parts.stream()
                .filter(part -> capabilities.contains(part.capability()))
                .allMatch(part -> !birth.isBefore(part.dateParams().get(PartRequirement.BIRTH_DATE_FROM))
                        && !birth.isAfter(part.dateParams().get(PartRequirement.BIRTH_DATE_TO)));
    }

    /** The capabilities whose records decide the practice of a subgroup (C7Pack.parts, C7Practices). */
    private static Set<String> readBy(C7Subgroup subgroup) {
        return switch (subgroup) {
            case A, D ->
                Set.of(
                        Capabilities.PROCEDURE_PERFORMED,
                        Capabilities.EXAM_REQUEST_EVALUATION,
                        Capabilities.CONDITION_LIST);
            case B -> Set.of(Capabilities.IMMUNIZATION_HISTORY);
            case C -> Set.of(Capabilities.CARE_ENCOUNTER);
        };
    }

    /** The days a window of the civil months has and the same window in days has not. */
    record Flip(LocalDate from, LocalDate until) {

        /** The date a record in the flip gets: the day before the civil window starts. */
        LocalDate moveTo() {
            return from.minusDays(1);
        }
    }

    /** A window of {@code months} in days: 365·N/12, so 12 months are 365 days. */
    static long days(int months) {
        return 365L * months / 12;
    }

    /** The flips of the four windows in {@code month}: none when the civil window is not the longer. */
    static List<Flip> flips(YearMonth month) {
        LocalDate last = month.atEndOfMonth();
        List<Flip> flips = new ArrayList<>();
        for (int months : WINDOW_MONTHS) {
            LocalDate civil = DateWindow.lastCivilMonths(month, months).start();
            LocalDate inDays = last.minusDays(days(months) - 1);
            if (inDays.isAfter(civil)) {
                flips.add(new Flip(civil, inDays));
            }
        }
        return flips;
    }

    /** The date a record gets so that the civil windows of the rule hold it as the windows in days do. */
    static LocalDate windowed(LocalDate date, List<Flip> flips) {
        for (Flip flip : flips) {
            if (!date.isBefore(flip.from()) && date.isBefore(flip.until())) {
                return flip.moveTo();
            }
        }
        return date;
    }

    /** The date a dose gets so that the rule's bound counts it as the other reading's bound does. */
    static LocalDate dosed(LocalDate dose, Plan plan) {
        LocalDate rule = plan.ruleBound();
        LocalDate other = plan.otherBound();
        if (other == null) {
            return dose;
        }
        if (rule.isAfter(other) && !dose.isBefore(other) && dose.isBefore(rule)) {
            return rule;
        }
        if (rule.isBefore(other) && !dose.isBefore(rule) && dose.isBefore(other)) {
            return rule.minusDays(1);
        }
        return dose;
    }

    /** An ISO date, or {@code null} for one the rule would not read either. */
    private static LocalDate parsed(String date) {
        try {
            return date == null ? null : LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String windowed(String date, List<Flip> flips) {
        LocalDate day = parsed(date);
        return day == null ? date : windowed(day, flips).toString();
    }

    private static String dosed(String date, Plan plan) {
        LocalDate day = parsed(date);
        return day == null ? date : dosed(day, plan).toString();
    }

    /** The dataset of {@code month} as the other reading of the age and of the windows sees it. */
    static CanonicalDataset otherReading(CanonicalDataset data, Map<String, Plan> plans, YearMonth month) {
        List<Flip> flips = flips(month);
        CanonicalDataset.Builder copy = CanonicalDataset.builder();
        data.windows().forEach(copy::window);
        data.encounters().forEach(copy::encounter);
        recordsOf(data).forEach(item -> copy.add(shifted(item, plans, flips)));
        return copy.build();
    }

    private static Record shifted(Record item, Map<String, Plan> plans, List<Flip> flips) {
        return switch (item) {
            case CanonicalPerson person -> withBirth(person, plans.getOrDefault(person.personKey(), Plan.UNCHANGED));
            case CanonicalImmunization dose ->
                withDate(dose, dosed(dose.applicationDate(), plans.getOrDefault(dose.personKey(), Plan.UNCHANGED)));
            case CanonicalProcedureEvent procedure -> withDate(procedure, windowed(procedure.eventDate(), flips));
            case CanonicalCareEvent care -> withDate(care, windowed(care.careDate(), flips));
            case CanonicalCondition condition -> withDate(condition, windowed(condition.recordedDate(), flips));
            default -> item;
        };
    }

    private static CanonicalPerson withBirth(CanonicalPerson p, Plan plan) {
        if (plan.standIn() == null) {
            return p;
        }
        return new CanonicalPerson(
                p.sourceRef(),
                p.municipalityIbge(),
                p.personKey(),
                plan.standIn().toString(),
                p.sex(),
                p.genderIdentity(),
                p.deathDate());
    }

    private static CanonicalImmunization withDate(CanonicalImmunization d, String date) {
        return new CanonicalImmunization(
                d.sourceRef(),
                d.municipalityIbge(),
                d.personKey(),
                date,
                d.immunobiologicalCode(),
                d.doseCode(),
                d.strategyCode(),
                d.transcription(),
                d.cbo(),
                d.cnes(),
                d.ine(),
                d.registrationDate());
    }

    private static CanonicalProcedureEvent withDate(CanonicalProcedureEvent p, String date) {
        return new CanonicalProcedureEvent(
                p.sourceRef(),
                p.municipalityIbge(),
                p.personKey(),
                date,
                p.sigtapCode(),
                p.stage(),
                p.cbo(),
                p.cnes(),
                p.ine(),
                p.origin());
    }

    private static CanonicalCareEvent withDate(CanonicalCareEvent e, String date) {
        return new CanonicalCareEvent(
                e.sourceRef(),
                e.municipalityIbge(),
                e.personKey(),
                date,
                e.form(),
                e.cbo(),
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

    private static CanonicalCondition withDate(CanonicalCondition c, String date) {
        return new CanonicalCondition(
                c.sourceRef(),
                c.municipalityIbge(),
                c.personKey(),
                c.codeSystem(),
                c.code(),
                date,
                c.status(),
                c.resolvedDate(),
                c.basis(),
                c.cbo());
    }

    /**
     * The people of a team of the revision whose subgroups, or the decision on one of them, differ
     * between the two outcomes of some month. The team is the link on the person's own row.
     */
    private static Set<String> changedPeople(
            List<RuleOutcome> baseline, List<RuleOutcome> alternative, Map<String, String> revision) {
        Set<String> changed = new TreeSet<>();
        for (int month = 0; month < baseline.size(); month++) {
            Map<String, Set<String>> was = decisions(baseline.get(month));
            Map<String, Set<String>> is = decisions(alternative.get(month));
            Map<String, String> teamOf = teamsOf(baseline.get(month));
            teamOf.putAll(teamsOf(alternative.get(month)));
            Set<String> keys = new TreeSet<>(was.keySet());
            keys.addAll(is.keySet());
            for (String key : keys) {
                boolean differs = !was.getOrDefault(key, Set.of()).equals(is.getOrDefault(key, Set.of()));
                if (differs && inRevision(teamOf.get(key), revision)) {
                    changed.add(key);
                }
            }
        }
        return changed;
    }

    /** Per person, the subject row and the decision on each subgroup: no supports. */
    private static Map<String, Set<String>> decisions(RuleOutcome outcome) {
        Map<String, Set<String>> byPerson = new TreeMap<>();
        for (EvidenceItem row : outcome.evidence()) {
            if (row.decision() != EvidenceDecision.SUPPORTING_EVENT) {
                byPerson.computeIfAbsent(row.subjectKey(), key -> new TreeSet<>())
                        .add(row.component() + "|" + row.decision());
            }
        }
        return byPerson;
    }

    private static Map<String, String> teamsOf(RuleOutcome outcome) {
        Map<String, String> teamOf = new HashMap<>();
        for (EvidenceItem row : outcome.evidence()) {
            if (row.component() == null && row.decision() == EvidenceDecision.ELIGIBLE) {
                teamOf.put(row.subjectKey(), row.ine());
            }
        }
        return teamOf;
    }
}
