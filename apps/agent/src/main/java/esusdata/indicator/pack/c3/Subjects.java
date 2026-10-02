package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The subjects of one person: each episode active in the competência, with its cohort verdict and,
 * when eligible, its practices; plus one ambiguous subject {@code personKey#sem-dum} when a 24 f
 * code falls outside every episode's window (AMB-C3-03 (iii), CT-C3-71/73).
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
            Cohort.Activity activity = cohort.activity(episode);
            if (activity == Cohort.Activity.INACTIVE) {
                continue;
            }
            Verdict verdict = cohort.decide(episode, person, link, activity);
            Map<Practice, PracticeOutcome> practices =
                    verdict.eligible() ? evaluator.evaluate(evidence, episode, teamTypes.eap76(link.ine())) : Map.of();
            subjects.add(new Subject(episode.key(), link, verdict, episode, practices));
        }
        LocalDate orphan = codeWithoutDum(person, episodes);
        if (orphan != null) {
            Verdict personal = cohort.personal(person, link, orphan);
            Verdict verdict = personal == null ? Verdict.ambiguous(Ambiguity.AMB_C3_03, orphan) : personal;
            subjects.add(new Subject(person.personKey() + WITHOUT_DUM, link, verdict, null, Map.of()));
        }
        return subjects;
    }

    /** The earliest 24 f code that no episode's {@code [DUM, D + 42]} covers, or {@code null}. */
    private LocalDate codeWithoutDum(PersonRecords person, List<Episode> episodes) {
        LocalDate floor = cohort.firstDay().minusDays(CODE_REACH_DAYS);
        LocalDate earliest = null;
        for (CanonicalCareEvent event : person.individualCare()) {
            LocalDate date = C3Dates.parse(event.careDate());
            boolean candidate = date != null && !date.isBefore(floor) && hasPhaseCode(event);
            if (candidate && !covered(date, episodes) && (earliest == null || date.isBefore(earliest))) {
                earliest = date;
            }
        }
        return earliest;
    }

    private static boolean hasPhaseCode(CanonicalCareEvent event) {
        return CodeMatch.of(event, C3Codes.PREGNANCY_CIAP, C3Codes.PREGNANCY_CID)
                        .found()
                || CodeMatch.of(event, C3Codes.PUERPERIUM_CIAP, C3Codes.PUERPERIUM_CID)
                        .found();
    }

    private static boolean covered(LocalDate date, List<Episode> episodes) {
        for (Episode episode : episodes) {
            if (C3Dates.within(date, episode.primary().dum(), episode.coverageEnd())) {
                return true;
            }
        }
        return false;
    }
}
