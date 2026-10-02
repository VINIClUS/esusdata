package esusdata.indicator.pack.c4;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.SourceRef;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Who is in the C4 denominator on the cutoff (ficha items 5, 14, 15, 24 b and 4.1): people with an
 * eligible diabetes code evaluated since 2013, linked to a team by the latest version of their
 * individual registration (§1.7.3 — never by the latest encounter), and not interrupted.
 *
 * <p>The evaluation qualifies through the problem list ({@code condition_list}, professional basis,
 * no CBO in the source — S-C4-01) or through an individual encounter by a médico/enfermeiro read in
 * the 12-month window. Every person with any diabetes signal is a candidate, so the evidence
 * rebuilds the population including who was left out and why (ENG-36). Exclusions are checked in a
 * fixed order: no qualifying evaluation, death, change of territory, no link, team type outside 70/76,
 * all conditions resolved.
 */
final class C4Cohort {

    /**
     * Versions on the same date are ordered by their source record id, numerically when both are
     * numbers (DW surrogate ids grow with the version), else as text.
     */
    static final Comparator<SourceRef> RECORD_ORDER = (a, b) -> {
        String x = a.recordId();
        String y = b.recordId();
        if (x.chars().allMatch(Character::isDigit) && y.chars().allMatch(Character::isDigit)) {
            return x.length() == y.length() ? x.compareTo(y) : Integer.compare(x.length(), y.length());
        }
        return x.compareTo(y);
    };

    private static final Comparator<CanonicalRegistration> REGISTRATION_ORDER = Comparator.comparing(
                    (CanonicalRegistration r) -> LocalDate.parse(r.registrationDate()))
            .thenComparing(CanonicalRegistration::sourceRef, RECORD_ORDER);

    private static final Comparator<CanonicalCondition> CONDITION_ORDER = Comparator.comparing(
                    (CanonicalCondition c) -> LocalDate.parse(c.recordedDate()))
            .thenComparing(CanonicalCondition::sourceRef, RECORD_ORDER);

    private static final Comparator<CanonicalTeam> TEAM_ORDER = Comparator.comparing(
                    (CanonicalTeam t) -> observedDate(t), Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(CanonicalTeam::sourceRef, RECORD_ORDER);

    private final LocalDate cutoff;
    private final SortedSet<String> candidates = new TreeSet<>();
    private final Set<String> evaluated = new HashSet<>();
    private final Set<String> dead = new HashSet<>();
    private final Map<String, CanonicalRegistration> latestRegistration = new HashMap<>();
    private final Map<String, Map<String, CanonicalCondition>> latestCondition = new HashMap<>();
    private final Map<String, CanonicalTeam> latestTeam = new HashMap<>();

    /** The team a person is linked to on the cutoff, with its CNES type when the source has it. */
    record Link(String ine, String cnes, String teamType) {}

    /** A candidate: eligible when {@code exclusion} is {@code null}. */
    record Subject(String personKey, Link link, String exclusion) {
        boolean eligible() {
            return exclusion == null;
        }

        boolean eap76() {
            return link != null && C4Codes.EAP_TEAM_TYPE.equals(link.teamType());
        }
    }

    private C4Cohort(LocalDate cutoff) {
        this.cutoff = cutoff;
    }

    static List<Subject> resolve(CanonicalDataset data, LocalDate cutoff) {
        C4Cohort cohort = new C4Cohort(cutoff);
        data.conditions().forEach(cohort::readCondition);
        data.careEvents().forEach(cohort::readCareEvent);
        data.registrations().forEach(cohort::readRegistration);
        data.persons().forEach(cohort::readPerson);
        data.teams().forEach(cohort::readTeam);
        List<Subject> subjects = new ArrayList<>(cohort.candidates.size());
        for (String key : cohort.candidates) {
            CanonicalRegistration registration = cohort.latestRegistration.get(key);
            Link link = registration == null ? null : cohort.link(registration);
            subjects.add(new Subject(key, link, cohort.exclusion(key, registration, link)));
        }
        return subjects;
    }

    private String exclusion(String key, CanonicalRegistration registration, Link link) {
        if (!evaluated.contains(key)) {
            return C4Reasons.NO_PROFESSIONAL_EVALUATION;
        }
        String exitReason = registration == null ? null : registration.exitReason();
        if (dead.contains(key) || C4Codes.EXIT_DEATH.equals(exitReason)) {
            return C4Reasons.DEATH;
        }
        if (C4Codes.EXIT_TERRITORY_CHANGE.equals(exitReason)) {
            return C4Reasons.TERRITORY_CHANGE;
        }
        if (!linksToTeam(registration)) {
            return C4Reasons.NO_LINK;
        }
        if (link.teamType() != null && !C4Codes.TEAM_TYPES.contains(link.teamType())) {
            return C4Reasons.TEAM_TYPE_OUT_OF_SCOPE;
        }
        return allResolved(key) ? C4Reasons.CONDITIONS_RESOLVED : null;
    }

    private Link link(CanonicalRegistration registration) {
        CanonicalTeam team = registration.ine() == null ? null : latestTeam.get(registration.ine());
        return new Link(registration.ine(), registration.cnes(), team == null ? null : team.teamTypeCode());
    }

    private void readCondition(CanonicalCondition condition) {
        if (!C4Codes.isEligibleCondition(condition.codeSystem(), condition.code())) {
            return;
        }
        candidates.add(condition.personKey());
        LocalDate recorded = LocalDate.parse(condition.recordedDate());
        if (!C4Codes.BASIS_PROFESSIONAL.equals(condition.basis()) || recorded.isAfter(cutoff)) {
            return;
        }
        if (!recorded.isBefore(C4Codes.EVALUATED_SINCE)) {
            evaluated.add(condition.personKey());
        }
        String problem = condition.codeSystem() + ':' + C4Codes.normalized(condition.code());
        latestCondition
                .computeIfAbsent(condition.personKey(), k -> new HashMap<>())
                .merge(problem, condition, (a, b) -> CONDITION_ORDER.compare(a, b) >= 0 ? a : b);
    }

    private void readCareEvent(CanonicalCareEvent event) {
        boolean diabetes = event.ciapCodes().stream().anyMatch(C4Codes::isEligibleCiap)
                || event.cidCodes().stream().anyMatch(C4Codes::isEligibleCid);
        if (!diabetes) {
            return;
        }
        candidates.add(event.personKey());
        LocalDate date = LocalDate.parse(event.careDate());
        if (C4Practices.isIndividualCare(event)
                && C4Codes.CBO_CONDITION.matches(event.cbo())
                && !date.isAfter(cutoff)
                && !date.isBefore(C4Codes.EVALUATED_SINCE)) {
            evaluated.add(event.personKey());
        }
    }

    private void readRegistration(CanonicalRegistration registration) {
        if (Boolean.TRUE.equals(registration.selfReportedDiabetes())) {
            candidates.add(registration.personKey());
        }
        if (Boolean.TRUE.equals(registration.simplified())
                || LocalDate.parse(registration.registrationDate()).isAfter(cutoff)) {
            return;
        }
        latestRegistration.merge(
                registration.personKey(), registration, (a, b) -> REGISTRATION_ORDER.compare(a, b) >= 0 ? a : b);
    }

    private void readPerson(CanonicalPerson person) {
        if (person.deathDate() != null && !LocalDate.parse(person.deathDate()).isAfter(cutoff)) {
            dead.add(person.personKey());
        }
    }

    private void readTeam(CanonicalTeam team) {
        LocalDate observed = observedDate(team);
        if (team.ine() != null && team.teamTypeCode() != null && (observed == null || !observed.isAfter(cutoff))) {
            latestTeam.merge(team.ine(), team, (a, b) -> TEAM_ORDER.compare(a, b) >= 0 ? a : b);
        }
    }

    /** The date part of {@code observedAt} (a date or a timestamp), or {@code null}. */
    private static LocalDate observedDate(CanonicalTeam team) {
        String at = team.observedAt();
        return at == null || at.length() < 10 ? null : LocalDate.parse(at.substring(0, 10));
    }

    private static boolean linksToTeam(CanonicalRegistration registration) {
        return registration != null
                && registration.ine() != null
                && !Boolean.TRUE.equals(registration.inactive())
                && !Boolean.TRUE.equals(registration.refused());
    }

    /**
     * True when the person has eligible problem-list conditions and the last state of every one is
     * resolved (AMB-C4-04: Latente is not resolved; a later encounter does not reopen the list).
     */
    private boolean allResolved(String key) {
        Map<String, CanonicalCondition> problems = latestCondition.get(key);
        if (problems == null || problems.isEmpty()) {
            return false;
        }
        return problems.values().stream().allMatch(this::resolvedOnCutoff);
    }

    private boolean resolvedOnCutoff(CanonicalCondition condition) {
        return C4Codes.RESOLVED_STATUS.equals(condition.status())
                && (condition.resolvedDate() == null
                        || !LocalDate.parse(condition.resolvedDate()).isAfter(cutoff));
    }
}
