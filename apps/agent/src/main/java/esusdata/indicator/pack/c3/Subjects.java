package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The subjects of one person: each episode active in the competência, with its cohort verdict and,
 * when eligible, its practices; plus one excluded subject {@code personKey#sem-dum} when a 24 f
 * code falls outside every episode's window, a pregnancy code without a DUM or IG to date it
 * (AMB-C3-03 (iii), reason {@code EXCLUIDO_SEM_DUM_NEM_IG}).
 */
final class Subjects {

    /** How far back a code without a DUM still reaches the competência: 294 + 42 days. */
    private static final int CODE_REACH_DAYS = GestationWindow.MAX_PREGNANCY_DAYS + GestationWindow.PUERPERIUM_DAYS;

    private static final String WITHOUT_DUM = "#sem-dum";

    private final Cohort cohort;
    private final PracticeEvaluator evaluator;
    private final TeamTypes teamTypes;
    private final LocalDate cutoff;

    Subjects(Cohort cohort, PracticeEvaluator evaluator, TeamTypes teamTypes, LocalDate cutoff) {
        this.cohort = cohort;
        this.evaluator = evaluator;
        this.teamTypes = teamTypes;
        this.cutoff = cutoff;
    }

    List<Subject> of(PersonRecords person) {
        List<Episode> episodes = Episodes.of(person);
        RegistrationLink link = RegistrationLink.resolve(person.registrations(), cutoff);
        PracticeEvaluator.PersonEvidence evidence = PracticeEvaluator.PersonEvidence.of(person);
        List<Subject> subjects = new ArrayList<>();
        for (Episode episode : episodes) {
            if (!cohort.active(episode)) {
                continue;
            }
            Verdict verdict = cohort.decide(episode, person, link);
            Map<Practice, PracticeOutcome> practices =
                    verdict.eligible() ? evaluator.evaluate(evidence, episode, teamTypes.eap76(link.ine())) : Map.of();
            subjects.add(new Subject(episode.key(), link, verdict, episode, practices));
        }
        LocalDate orphan = codeWithoutDum(person, episodes);
        if (orphan != null) {
            Verdict personal = cohort.personal(person, link, orphan);
            Verdict verdict = personal == null ? Verdict.excluded(C3Reasons.EXCLUIDO_SEM_DUM_NEM_IG, orphan) : personal;
            subjects.add(new Subject(person.personKey() + WITHOUT_DUM, link, verdict, null, Map.of()));
        }
        return subjects;
    }

    /**
     * The earliest 24 f code that no episode explains, or {@code null}. A pregnancy code is explained
     * by an episode's {@code [DUM, D + 42]}; a code only of the puerperium list, recorded after a known
     * DUM, is a late puerperal record of that pregnancy, not a pregnancy without a DUM.
     */
    private LocalDate codeWithoutDum(PersonRecords person, List<Episode> episodes) {
        LocalDate floor = cohort.firstDay().minusDays(CODE_REACH_DAYS);
        LocalDate earliest = null;
        for (CanonicalCareEvent event : person.individualCare()) {
            LocalDate date = C3Dates.parse(event.careDate());
            boolean candidate = date != null && !date.isBefore(floor) && unexplained(event, date, episodes);
            if (candidate && (earliest == null || date.isBefore(earliest))) {
                earliest = date;
            }
        }
        return earliest;
    }

    /**
     * A code of the pregnancy list (the more specific list deciding, AMB-C3-08) outside every
     * episode, or a puerperium code before every DUM.
     */
    private static boolean unexplained(CanonicalCareEvent event, LocalDate date, List<Episode> episodes) {
        if (CodeMatch.of(event, C3Codes.PREGNANCY_CIAP, C3Codes.PREGNANCY_CID, C3Codes.PUERPERIUM_CID)) {
            return !covered(date, episodes);
        }
        boolean puerperium =
                CodeMatch.of(event, C3Codes.PUERPERIUM_CIAP, C3Codes.PUERPERIUM_CID, C3Codes.PREGNANCY_CID);
        return puerperium && !afterSomeDum(date, episodes);
    }

    private static boolean afterSomeDum(LocalDate date, List<Episode> episodes) {
        for (Episode episode : episodes) {
            if (!date.isBefore(episode.window().dum())) {
                return true;
            }
        }
        return false;
    }

    private static boolean covered(LocalDate date, List<Episode> episodes) {
        for (Episode episode : episodes) {
            if (C3Dates.within(date, episode.window().dum(), episode.coverageEnd())) {
                return true;
            }
        }
        return false;
    }
}
