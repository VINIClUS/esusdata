package esusdata.indicator.pack.c6;

import esusdata.indicator.model.AgeAt;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.TeamScope;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The C6 cohort: every person the run read, eligible or excluded with a stable reason (ENG-36).
 * Age is the completed age on the last day of the competência (AMB-C6-05) under {@link #ANNIVERSARY};
 * the link is the individual registration version in force at the cutoff (§1.7.3), never the
 * latest encounter.
 */
final class C6Cohort {

    /**
     * The anniversary convention C6 declares (ENG-27): decision C6-D3 (AMB-C6-12): a 29/02
     * anniversary falls on 01/03 (Lei nº 810/1949, art. 3º), as in C7. The birth-date bind of
     * {@link C6Pack#requirements} is a superset (it clamps), so no one eligible is left unread. A 60th
     * birthday only falls on a missing 29/02 when the 60th year is not a leap year (born 29/02/2040,
     * competência 2100-02).
     */
    static final AgeAt.AnniversaryRule ANNIVERSARY = AgeAt.AnniversaryRule.NEXT_DAY;

    static final String ELIGIBLE = "ELEGIVEL_60_ANOS_VINCULADO";
    static final String NO_BIRTH_DATE = "EXCLUIDO_SEM_DATA_NASCIMENTO";
    static final String DIVERGENT_BIRTH_DATE = "EXCLUIDO_DATA_NASCIMENTO_DIVERGENTE";
    static final String UNDER_60 = "EXCLUIDO_IDADE_MENOR_60";
    static final String DEATH = "INTERROMPIDO_OBITO";
    static final String NO_LINK = "EXCLUIDO_SEM_VINCULO";
    static final String CONFLICTING_LINK = "EXCLUIDO_VINCULO_CONFLITANTE";
    static final String REFUSED = "EXCLUIDO_RECUSA_CADASTRO";
    static final String INACTIVE = "EXCLUIDO_CADASTRO_INATIVO";
    static final String CHANGE_OF_TERRITORY = "INTERROMPIDO_MUDANCA_TERRITORIO";
    static final String UNMAPPED_EXIT = "EXCLUIDO_SAIDA_CADASTRO_NAO_MAPEADA";

    private static final Comparator<CanonicalRegistration> BY_VERSION = Comparator.comparing(
                    (CanonicalRegistration r) -> LocalDate.parse(r.registrationDate()))
            .thenComparing(r -> r.sourceRef().recordId(), Comparator.nullsFirst(Comparator.naturalOrder()));

    private C6Cohort() {}

    /**
     * One person considered by the rule.
     *
     * @param link the registration version in force at the cutoff, or {@code null}
     * @param reasonCode {@link #ELIGIBLE} or the first exclusion that applies
     * @param team what the team-type rule says about the linked team, or {@code null} without a link
     */
    record Subject(String personKey, CanonicalRegistration link, String reasonCode, TeamScope.Decision team) {
        boolean eligible() {
            return ELIGIBLE.equals(reasonCode);
        }

        /** The person belongs to an eAP 76 team (item 24 b): practice C is credited in full. */
        boolean eap76() {
            return team != null && team.eap76();
        }

        String ine() {
            return link == null ? null : link.ine();
        }

        String cnes() {
            return link == null ? null : link.cnes();
        }
    }

    /** Every person key of the person and registration parts, in key order. */
    static List<Subject> resolve(CanonicalDataset data, LocalDate ageReference, LocalDate cutoff, TeamScope teams) {
        Map<String, List<CanonicalPerson>> persons = new TreeMap<>();
        data.persons()
                .forEach(p -> persons.computeIfAbsent(p.personKey(), k -> new ArrayList<>())
                        .add(p));
        Map<String, List<CanonicalRegistration>> registrations = new TreeMap<>();
        data.registrations()
                .forEach(r -> registrations
                        .computeIfAbsent(r.personKey(), k -> new ArrayList<>())
                        .add(r));
        SortedSet<String> keys = new TreeSet<>(persons.keySet());
        keys.addAll(registrations.keySet());
        List<Subject> subjects = new ArrayList<>(keys.size());
        for (String key : keys) {
            List<CanonicalPerson> rows = persons.getOrDefault(key, List.of());
            Link link = link(registrations.getOrDefault(key, List.of()), cutoff);
            CanonicalRegistration version = link.conflicting() ? null : link.version();
            TeamScope.Decision team = version == null || version.ine() == null ? null : teams.decide(version.ine());
            String reason = personExclusion(rows, ageReference, cutoff);
            if (reason == null) {
                reason = linkExclusion(link);
            }
            if (reason == null && team != null && !team.considered()) {
                reason = team.exclusionReason();
            }
            subjects.add(new Subject(key, version, reason == null ? ELIGIBLE : reason, team));
        }
        return subjects;
    }

    private static String personExclusion(List<CanonicalPerson> rows, LocalDate ageReference, LocalDate cutoff) {
        SortedSet<String> births = new TreeSet<>();
        for (CanonicalPerson p : rows) {
            if (p.birthDate() != null) {
                births.add(p.birthDate());
            }
        }
        if (births.isEmpty()) {
            return NO_BIRTH_DATE;
        }
        if (births.size() > 1) {
            return DIVERGENT_BIRTH_DATE;
        }
        LocalDate birth = LocalDate.parse(births.first());
        if (AgeAt.completedYears(birth, ageReference, ANNIVERSARY) < C6Codes.MINIMUM_AGE_YEARS) {
            return UNDER_60;
        }
        boolean dead = rows.stream()
                .map(CanonicalPerson::deathDate)
                .filter(Objects::nonNull)
                .anyMatch(d -> !LocalDate.parse(d).isAfter(cutoff));
        return dead ? DEATH : null;
    }

    /**
     * The ficha's own interruptions (item 15, p. 1) come first; then the local conventions declared
     * as limitations (refusal, inactive version); a person without a team is not «vinculada à
     * equipe» (item 23, p. 2).
     */
    private static String linkExclusion(Link link) {
        CanonicalRegistration version = link.version();
        if (version == null) {
            return NO_LINK;
        }
        if (link.conflicting()) {
            return CONFLICTING_LINK;
        }
        String exit = blankToNull(version.exitReason());
        if (exit != null) {
            return exitExclusion(exit);
        }
        if (Boolean.TRUE.equals(version.refused())) {
            return REFUSED;
        }
        if (Boolean.TRUE.equals(version.inactive())) {
            return INACTIVE;
        }
        return version.ine() == null ? NO_LINK : null;
    }

    private static String exitExclusion(String exit) {
        if (C6Codes.EXIT_CHANGE_OF_TERRITORY.equals(exit)) {
            return CHANGE_OF_TERRITORY;
        }
        return C6Codes.EXIT_DEATH.equals(exit) ? DEATH : UNMAPPED_EXIT;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    /**
     * The latest complete registration version on or before the cutoff. A simplified record is not
     * a complete individual registration (§1.7) and never links; versions of the same latest day
     * that disagree are a conflict, not a choice (§1.7.3).
     */
    private static Link link(List<CanonicalRegistration> versions, LocalDate cutoff) {
        List<CanonicalRegistration> valid = versions.stream()
                .filter(r -> !Boolean.TRUE.equals(r.simplified()))
                .filter(r -> !LocalDate.parse(r.registrationDate()).isAfter(cutoff))
                .sorted(BY_VERSION)
                .toList();
        if (valid.isEmpty()) {
            return new Link(null, false);
        }
        CanonicalRegistration latest = valid.get(valid.size() - 1);
        long states = valid.stream()
                .filter(r -> r.registrationDate().equals(latest.registrationDate()))
                .map(LinkState::of)
                .distinct()
                .count();
        return new Link(latest, states > 1);
    }

    private record Link(CanonicalRegistration version, boolean conflicting) {}

    /** What a version says about the link; two same-day versions that differ here conflict. */
    private record LinkState(String ine, String cnes, String exitReason, boolean inactive, boolean refused) {
        static LinkState of(CanonicalRegistration r) {
            return new LinkState(
                    r.ine(),
                    r.cnes(),
                    blankToNull(r.exitReason()),
                    Boolean.TRUE.equals(r.inactive()),
                    Boolean.TRUE.equals(r.refused()));
        }
    }
}
