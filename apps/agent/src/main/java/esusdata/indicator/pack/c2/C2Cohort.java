package esusdata.indicator.pack.c2;

import esusdata.indicator.model.AgeAt;
import esusdata.indicator.model.AgeAt.AnniversaryRule;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.EvaluationContext;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * The C2 cohort (items 14, 15 and 23, p.1–2; 4.1 p.4), as decided in AMB-C2-03: the children linked
 * to the team whose second birthday falls in the competência. Each child is evaluated once, in the
 * month it completes two years, over its whole life up to that birthday (NT 8/2026: a month with no
 * child completing two years has no result). A child who completes two years before the first day
 * of the competência, or after its last, is out.
 *
 * <p>The link is the latest complete individual registration version up to the cutoff (§1.7.3;
 * the NT 30/2025 national link is not reproducible locally, DW gap L8); interruptions are those of
 * item 15 that the local PEC can see.
 */
final class C2Cohort {

    /**
     * The anniversary the pack reports for the cohort (ENG-27): Lei nº 810/1949, art. 3º — a child
     * born on 29/02 completes two years on 01/03 (AMB-C2-02).
     */
    static final AnniversaryRule COHORT_RULE = AnniversaryRule.NEXT_DAY;

    static final String ELIGIBLE = "COORTE_COMPLETA_2_ANOS_NA_COMPETENCIA";
    static final String BORN_AFTER_CUTOFF = "EXCLUIDO_NASCIDO_APOS_CORTE";
    static final String OVER_TWO_YEARS = "EXCLUIDO_IDADE_ACIMA_2_ANOS";
    static final String NOT_YET_TWO_YEARS = "EXCLUIDO_AINDA_NAO_COMPLETA_2_ANOS";
    static final String NO_LINK = "EXCLUIDO_SEM_VINCULO";
    static final String REFUSED = "EXCLUIDO_RECUSA_CADASTRO";
    static final String TERRITORY_CHANGE = "INTERROMPIDO_MUDANCA_TERRITORIO";
    static final String DEATH = "INTERROMPIDO_OBITO";
    static final String TEAM_TYPE_NOT_CONSIDERED = "EXCLUIDO_TIPO_EQUIPE_NAO_CONSIDERADO";

    /** Same-day versions are ordered by the source id as a number (length, then text): "10" after "9". */
    private static final Comparator<CanonicalRegistration> LATEST = Comparator.comparing(
                    (CanonicalRegistration r) -> LocalDate.parse(r.registrationDate()))
            .thenComparingInt(r -> r.sourceRef().recordId().length())
            .thenComparing(r -> r.sourceRef().recordId());

    private C2Cohort() {}

    /** Where one person of the citizen extract stands on the cutoff. */
    record Member(
            CanonicalPerson person, ChildClock clock, boolean eligible, String reasonCode, String ine, String cnes) {}

    static Member classify(
            CanonicalPerson person, List<CanonicalRegistration> registrations, EvaluationContext context) {
        LocalDate birth = LocalDate.parse(person.birthDate());
        ChildClock clock = new ChildClock(birth);
        LocalDate cutoff = context.dataCutoff();
        CanonicalRegistration link = latest(registrations, cutoff, false);
        CanonicalRegistration latest = latest(registrations, cutoff, true);
        String ine = link == null ? null : blankToNull(link.ine());
        String cnes = link == null ? null : link.cnes();
        String excluded = exclusion(person, link, latest, birth, context);
        return new Member(person, clock, excluded == null, excluded == null ? ELIGIBLE : excluded, ine, cnes);
    }

    /**
     * 24 b (p.2): only eSF (70) and eAP (76) teams are considered. A child linked to a team whose
     * type is known and is neither leaves the cohort; an unknown type keeps it (gap L1, declared).
     */
    static Member onConsideredTeam(Member member, String teamType) {
        if (!member.eligible() || teamType == null || C2Codes.CONSIDERED_TEAM_TYPES.contains(teamType)) {
            return member;
        }
        return new Member(
                member.person(), member.clock(), false, TEAM_TYPE_NOT_CONSIDERED, member.ine(), member.cnes());
    }

    private static String exclusion(
            CanonicalPerson person,
            CanonicalRegistration link,
            CanonicalRegistration latest,
            LocalDate birth,
            EvaluationContext context) {
        LocalDate cutoff = context.dataCutoff();
        LocalDate secondBirthday = AgeAt.anniversaryYears(birth, 2, COHORT_RULE);
        if (birth.isAfter(cutoff)) {
            return BORN_AFTER_CUTOFF;
        }
        if (secondBirthday.isBefore(context.competencia().atDay(1))) {
            return OVER_TWO_YEARS;
        }
        if (secondBirthday.isAfter(context.competencia().atEndOfMonth())) {
            return NOT_YET_TWO_YEARS;
        }
        if (person.deathDate() != null && !LocalDate.parse(person.deathDate()).isAfter(cutoff)) {
            return DEATH;
        }
        String exit = latest == null ? null : latest.exitReason();
        if (C2Codes.EXIT_TERRITORY_CHANGE.equals(exit)) {
            return TERRITORY_CHANGE;
        }
        if (C2Codes.EXIT_DEATH.equals(exit)) {
            return DEATH;
        }
        if (link == null) {
            return NO_LINK;
        }
        if (Boolean.TRUE.equals(link.refused())) {
            return REFUSED;
        }
        return blankToNull(link.ine()) == null ? NO_LINK : null;
    }

    /**
     * The latest complete registration version up to the cutoff (never the latest encounter). The
     * exit of item 15 is read from "a atualização mais recente" whether or not the source marks it
     * inactive ({@code withInactive}); the team link only from an active version.
     */
    private static CanonicalRegistration latest(
            List<CanonicalRegistration> registrations, LocalDate cutoff, boolean withInactive) {
        CanonicalRegistration latest = null;
        for (CanonicalRegistration r : registrations) {
            boolean usable = !Boolean.TRUE.equals(r.simplified())
                    && (withInactive || !Boolean.TRUE.equals(r.inactive()))
                    && !LocalDate.parse(r.registrationDate()).isAfter(cutoff);
            if (usable && (latest == null || LATEST.compare(r, latest) > 0)) {
                latest = r;
            }
        }
        return latest;
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
