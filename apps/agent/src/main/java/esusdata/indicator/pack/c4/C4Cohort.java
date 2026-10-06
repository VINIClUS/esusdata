package esusdata.indicator.pack.c4;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.SourceRef;
import esusdata.indicator.model.TeamScope;
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
 * evaluated by a médico/enfermeiro since 2013) or through an individual encounter by a médico/enfermeiro
 * read in the 12-month window. Every person with any diabetes signal is a candidate, so the evidence
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

    private final LocalDate cutoff;
    private final TeamScope teams;
    private final SortedSet<String> candidates = new TreeSet<>();
    private final Set<String> evaluated = new HashSet<>();
    private final Set<String> dead = new HashSet<>();
    private final Map<String, CanonicalRegistration> latestRegistration = new HashMap<>();
    private final Map<String, Map<String, CanonicalCondition>> latestCondition = new HashMap<>();

    /** The team a person is linked to on the cutoff, with what the team-type rule says about it. */
    record Link(String ine, String cnes, TeamScope.Decision team) {}

    /**
     * A candidate: eligible when {@code exclusion} is {@code null}. {@code link} is the team of a
     * usable registration (null without one); {@code unknownConditionStatus} flags a latest condition
     * status outside the LEDI 0/1/2, kept as not resolved (AMB-C4-04).
     */
    record Subject(String personKey, Link link, String exclusion, boolean unknownConditionStatus) {
        boolean eligible() {
            return exclusion == null;
        }

        /** The team this candidate is counted under: a usable link to a considered team (70 or 76). */
        boolean countsForTeam() {
            return link != null && link.team().considered();
        }

        boolean eap76() {
            return link != null && link.team().eap76();
        }
    }

    private C4Cohort(LocalDate cutoff, TeamScope teams) {
        this.cutoff = cutoff;
        this.teams = teams;
    }

    /**
     * The candidates. {@code teamsOn} is the last day of the competência: the team type is the one
     * valid then (C4-D2), whatever the care cutoff.
     */
    static List<Subject> resolve(CanonicalDataset data, LocalDate cutoff, LocalDate teamsOn) {
        C4Cohort cohort = new C4Cohort(cutoff, TeamScope.of(data.teams(), teamsOn));
        data.conditions().forEach(cohort::readCondition);
        data.careEvents().forEach(cohort::readCareEvent);
        data.registrations().forEach(cohort::readRegistration);
        data.persons().forEach(cohort::readPerson);
        List<Subject> subjects = new ArrayList<>(cohort.candidates.size());
        for (String key : cohort.candidates) {
            CanonicalRegistration registration = cohort.latestRegistration.get(key);
            Link link = linksToTeam(registration) ? cohort.link(registration) : null;
            subjects.add(new Subject(key, link, cohort.exclusion(key, registration, link), cohort.unknownStatus(key)));
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
        if (!link.team().considered()) {
            return link.team().exclusionReason();
        }
        return allResolved(key) ? C4Reasons.CONDITIONS_RESOLVED : null;
    }

    private Link link(CanonicalRegistration registration) {
        return new Link(registration.ine(), registration.cnes(), teams.decide(registration.ine()));
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
        if (!recorded.isBefore(C4Codes.EVALUATED_SINCE) && C4Codes.CBO_CONDITION.matches(condition.cbo())) {
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

    private boolean unknownStatus(String key) {
        Map<String, CanonicalCondition> problems = latestCondition.get(key);
        return problems != null
                && problems.values().stream()
                        .anyMatch(c -> c.status() == null || !C4Codes.CONDITION_STATUSES.contains(c.status()));
    }

    private boolean resolvedOnCutoff(CanonicalCondition condition) {
        return C4Codes.RESOLVED_STATUS.equals(condition.status())
                && (condition.resolvedDate() == null
                        || !LocalDate.parse(condition.resolvedDate()).isAfter(cutoff));
    }
}
