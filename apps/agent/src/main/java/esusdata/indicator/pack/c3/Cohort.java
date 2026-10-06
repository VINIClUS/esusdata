package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalPregnancyOutcome;
import esusdata.indicator.model.TeamScope;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * The cohort of one competência, in the contract's order: activity in the competência (4.1),
 * link at the cutoff (item 14), death (item 15), abortion (24 g), the pregnancy code (24 f) and,
 * last, eligibility with the end date used (MET-21).
 */
final class Cohort {

    private final LocalDate firstDay;
    private final LocalDate cutoff;
    private final TeamTypes teamTypes;

    Cohort(YearMonth competencia, LocalDate cutoff, TeamTypes teamTypes) {
        this.teamTypes = teamTypes;
        this.firstDay = competencia.atDay(1);
        this.cutoff = cutoff;
    }

    /**
     * Whether an episode is "ativa na competência" (4.1): the pregnancy has started by the cutoff
     * and the puerperium (D + 42 inclusive) reaches the competência (AMB-C3-04).
     */
    boolean active(Episode episode) {
        GestationWindow window = episode.window();
        return !window.dum().isAfter(cutoff) && !window.lastDay().isBefore(firstDay);
    }

    /** The cohort decision of an active episode. */
    Verdict decide(Episode episode, PersonRecords person, RegistrationLink link) {
        GestationWindow window = episode.window();
        LocalDate end = window.end();
        Verdict personal = personal(person, link, end);
        if (personal != null) {
            return personal;
        }
        LocalDate abortion = abortion(person, window);
        if (abortion != null) {
            return Verdict.excluded(C3Reasons.EXCLUIDO_ABORTO, abortion);
        }
        if (!pregnancyCode(person, window)) {
            return Verdict.excluded(C3Reasons.EXCLUIDO_SEM_CODIGO_GESTACAO, end);
        }
        String reason = switch (window.endSource()) {
            case RECORDED_OUTCOME -> C3Reasons.ELEGIVEL_DESFECHO_REGISTRADO;
            case LPC_RESOLUTION -> C3Reasons.ELEGIVEL_DESFECHO_RESOLUCAO_LPC;
            case SUBSTITUTE_294 -> C3Reasons.ELEGIVEL_DATA_SUBSTITUTIVA_294D;
        };
        return Verdict.eligible(reason, end);
    }

    /**
     * Link, team type (24 b: only types 70 and 76, valid on the last day) and death, which do not
     * depend on the episode's dates; {@code null} when they hold.
     */
    Verdict personal(PersonRecords person, RegistrationLink link, LocalDate eventDate) {
        if (C3Reasons.EXCLUIDO_OBITO.equals(link.exclusion())) {
            return Verdict.excluded(C3Reasons.EXCLUIDO_OBITO, link.since());
        }
        if (link.exclusion() != null) {
            return Verdict.excluded(link.exclusion(), eventDate);
        }
        TeamScope.Decision team = teamTypes.decide(link.ine());
        if (!team.considered()) {
            return Verdict.excluded(team.exclusionReason(), eventDate);
        }
        for (CanonicalPerson record : person.persons()) {
            LocalDate death = C3Dates.parse(record.deathDate());
            if (death != null && !death.isAfter(cutoff)) {
                return Verdict.excluded(C3Reasons.EXCLUIDO_OBITO, death);
            }
        }
        return null;
    }

    LocalDate firstDay() {
        return firstDay;
    }

    /**
     * 24 g: the earliest exclusion code in {@code [DUM, D]} known at the cutoff excludes the
     * episode from the competência of the record (AMB-C3-07 (i)); {@code null} when none. A code
     * outside {@code [DUM, D]}, in the puerperium included, belongs to another pregnancy (iv). An
     * LPC condition counts when active, latent or resolved (ii).
     */
    private LocalDate abortion(PersonRecords person, GestationWindow window) {
        LocalDate earliest = null;
        for (LocalDate date : exclusionDates(person)) {
            boolean counts = window.inPregnancy(date) && !date.isAfter(cutoff);
            if (counts && (earliest == null || date.isBefore(earliest))) {
                earliest = date;
            }
        }
        return earliest;
    }

    private static List<LocalDate> exclusionDates(PersonRecords person) {
        List<LocalDate> dates = new ArrayList<>();
        for (CanonicalCareEvent event : person.individualCare()) {
            add(dates, event.careDate(), CodeMatch.of(event, C3Codes.EXCLUSION_CIAP, C3Codes.EXCLUSION_CID, List.of()));
        }
        for (CanonicalPregnancyOutcome outcome : person.outcomes()) {
            for (String code : outcome.codes()) {
                add(
                        dates,
                        outcome.outcomeDate(),
                        CodeMatch.any(code, C3Codes.EXCLUSION_CIAP, C3Codes.EXCLUSION_CID, List.of()));
            }
        }
        for (CanonicalCondition condition : person.conditions()) {
            add(
                    dates,
                    condition.recordedDate(),
                    C3Codes.recorded(condition)
                            && CodeMatch.of(condition, C3Codes.EXCLUSION_CIAP, C3Codes.EXCLUSION_CID, List.of()));
        }
        return dates;
    }

    private static void add(List<LocalDate> dates, String date, boolean match) {
        LocalDate parsed = C3Dates.parse(date);
        if (match && parsed != null) {
            dates.add(parsed);
        }
    }

    /**
     * 24 f: a pregnancy code in some MIAI, or in the LPC, within {@code [DUM, D]}; the CID-10 by
     * category, the more specific list deciding (AMB-C3-08).
     */
    private static boolean pregnancyCode(PersonRecords person, GestationWindow window) {
        for (CanonicalCareEvent event : person.individualCare()) {
            if (window.inPregnancy(C3Dates.parse(event.careDate()))
                    && CodeMatch.of(event, C3Codes.PREGNANCY_CIAP, C3Codes.PREGNANCY_CID, C3Codes.PUERPERIUM_CID)) {
                return true;
            }
        }
        for (CanonicalCondition condition : person.conditions()) {
            if (window.inPregnancy(C3Dates.parse(condition.recordedDate()))
                    && CodeMatch.of(condition, C3Codes.PREGNANCY_CIAP, C3Codes.PREGNANCY_CID, C3Codes.PUERPERIUM_CID)) {
                return true;
            }
        }
        return false;
    }
}
