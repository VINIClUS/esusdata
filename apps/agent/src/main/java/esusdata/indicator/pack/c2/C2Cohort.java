package esusdata.indicator.pack.c2;

import esusdata.indicator.model.AgeAt;
import esusdata.indicator.model.AgeAt.AnniversaryRule;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.EvaluationContext;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

/**
 * The C2 cohort (items 14, 15 and 23, p.1–2; 4.1 p.4): "Nº total de crianças com até 02 (dois) anos
 * de vida vinculadas à equipe no período", read literally for the month (AMB-C2-03, a standing
 * limitation): every linked child born by the cutoff whose second birthday is not before the first
 * day of the competência — children completing two years in the month included.
 *
 * <p>The link is the latest complete individual registration version up to the cutoff (§1.7.3;
 * the NT 30/2025 national link is not reproducible locally, DW gap L8); interruptions are those of
 * item 15 that the local PEC can see.
 */
final class C2Cohort {

    /**
     * The anniversary convention for the cohort (ENG-27, AMB-C2-02): Lei nº 810/1949, art. 3º — a
     * child born on 29/02 completes two years on 01/03.
     */
    static final AnniversaryRule COHORT_RULE = AnniversaryRule.NEXT_DAY;

    static final String ELIGIBLE = "COORTE_ATE_2_ANOS";
    static final String ELIGIBLE_COMPLETES_TWO = "COORTE_COMPLETA_2_ANOS_NA_COMPETENCIA";
    static final String BORN_AFTER_CUTOFF = "EXCLUIDO_NASCIDO_APOS_CORTE";
    static final String OVER_TWO_YEARS = "EXCLUIDO_IDADE_ACIMA_2_ANOS";
    static final String NO_LINK = "EXCLUIDO_SEM_VINCULO";
    static final String REFUSED = "EXCLUIDO_RECUSA_CADASTRO";
    static final String TERRITORY_CHANGE = "INTERROMPIDO_MUDANCA_TERRITORIO";
    static final String DEATH = "INTERROMPIDO_OBITO";

    private static final Comparator<CanonicalRegistration> LATEST = Comparator.comparing(
                    (CanonicalRegistration r) -> LocalDate.parse(r.registrationDate()))
            .thenComparing(r -> r.sourceRef().recordId());

    private C2Cohort() {}

    /** Where one person of the citizen extract stands on the cutoff. */
    record Member(
            CanonicalPerson person,
            ChildClock clock,
            boolean eligible,
            String reasonCode,
            String ine,
            String cnes,
            boolean completesTwoInMonth) {}

    static Member classify(
            CanonicalPerson person, List<CanonicalRegistration> registrations, EvaluationContext context) {
        LocalDate birth = LocalDate.parse(person.birthDate());
        ChildClock clock = new ChildClock(birth);
        LocalDate cutoff = context.dataCutoff();
        YearMonth month = context.competencia();
        LocalDate secondBirthday = AgeAt.anniversaryYears(birth, 2, COHORT_RULE);
        CanonicalRegistration link = link(registrations, cutoff);
        String ine = link == null ? null : blankToNull(link.ine());
        String cnes = link == null ? null : link.cnes();
        String excluded = exclusion(person, link, birth, secondBirthday, context);
        if (excluded != null) {
            return new Member(person, clock, false, excluded, ine, cnes, false);
        }
        boolean completesTwo = YearMonth.from(secondBirthday).equals(month);
        return new Member(
                person, clock, true, completesTwo ? ELIGIBLE_COMPLETES_TWO : ELIGIBLE, ine, cnes, completesTwo);
    }

    private static String exclusion(
            CanonicalPerson person,
            CanonicalRegistration link,
            LocalDate birth,
            LocalDate secondBirthday,
            EvaluationContext context) {
        LocalDate cutoff = context.dataCutoff();
        if (birth.isAfter(cutoff)) {
            return BORN_AFTER_CUTOFF;
        }
        if (secondBirthday.isBefore(context.competencia().atDay(1))) {
            return OVER_TWO_YEARS;
        }
        if (person.deathDate() != null && !LocalDate.parse(person.deathDate()).isAfter(cutoff)) {
            return DEATH;
        }
        if (link == null) {
            return NO_LINK;
        }
        if (Boolean.TRUE.equals(link.refused())) {
            return REFUSED;
        }
        if (C2Codes.EXIT_TERRITORY_CHANGE.equals(link.exitReason())) {
            return TERRITORY_CHANGE;
        }
        if (C2Codes.EXIT_DEATH.equals(link.exitReason())) {
            return DEATH;
        }
        return blankToNull(link.ine()) == null ? NO_LINK : null;
    }

    /** The latest complete, active registration version up to the cutoff (never the latest encounter). */
    private static CanonicalRegistration link(List<CanonicalRegistration> registrations, LocalDate cutoff) {
        CanonicalRegistration latest = null;
        for (CanonicalRegistration r : registrations) {
            boolean usable = !Boolean.TRUE.equals(r.simplified())
                    && !Boolean.TRUE.equals(r.inactive())
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
