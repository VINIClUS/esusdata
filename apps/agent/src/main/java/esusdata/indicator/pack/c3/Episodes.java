package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalPregnancyOutcome;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Builds a person's pregnancies from the MIAI (4.1, item 17). Each record gives at most one DUM:
 * the recorded one when valid ({@code careDate − 294 <= DUM <= careDate}), else the one derived
 * from the gestational age ({@code careDate − 7 × IG}) (AMB-C3-03 (i)). The first record (by care
 * date) with a DUM opens an episode with that DUM; it ends on the first outcome recorded in
 * {@code (DUM, DUM + 294]}, else on the resolution of W78 in the LPC, else on DUM + 294 — an
 * outcome after DUM + 294 is ignored (AMB-C3-05). The records whose DUM falls up to that end
 * belong to it; the next one opens another episode.
 */
final class Episodes {

    private static final Pattern WEEKS = Pattern.compile("\\d{1,2}");
    private static final int DAYS_PER_WEEK = 7;

    private Episodes() {}

    /** The DUM one record gives, and the record. */
    private record Candidate(LocalDate dum, EventRef ref) {}

    static List<Episode> of(PersonRecords person) {
        List<Candidate> candidates = candidates(person.individualCare());
        Ends ends = new Ends(outcomeDates(person.outcomes()), resolutionDates(person.conditions()));
        List<Episode> episodes = new ArrayList<>();
        while (!candidates.isEmpty()) {
            Candidate first = candidates.get(0);
            GestationWindow window = ends.window(first.dum());
            episodes.add(new Episode(person.personKey() + "#" + first.dum(), window, first.ref()));
            ends.discardLate(first.dum());
            candidates.removeIf(c -> !c.dum().isAfter(window.end()));
        }
        return episodes;
    }

    /** The dates that can end a pregnancy: recorded outcomes and resolutions of W78 in the LPC. */
    private record Ends(NavigableSet<LocalDate> outcomes, NavigableSet<LocalDate> resolutions) {

        /** The end D by precedence: recorded outcome, LPC resolution, DUM + 294 (EMENDA 1; L2). */
        GestationWindow window(LocalDate dum) {
            LocalDate outcome = within(outcomes, dum, GestationWindow.MAX_PREGNANCY_DAYS);
            if (outcome != null) {
                return new GestationWindow(dum, outcome, GestationWindow.EndSource.RECORDED_OUTCOME);
            }
            LocalDate resolved = within(resolutions, dum, GestationWindow.MAX_PREGNANCY_DAYS);
            return resolved == null
                    ? GestationWindow.substitute(dum)
                    : new GestationWindow(dum, resolved, GestationWindow.EndSource.LPC_RESOLUTION);
        }

        /**
         * AMB-C3-05: an outcome or resolution in {@code (DUM + 294, DUM + 336]} was ignored by the
         * episode of {@code dum}; it must not end another episode.
         */
        void discardLate(LocalDate dum) {
            LocalDate from = dum.plusDays(GestationWindow.MAX_PREGNANCY_DAYS);
            LocalDate through = from.plusDays(GestationWindow.PUERPERIUM_DAYS);
            outcomes.subSet(from, false, through, true).clear();
            resolutions.subSet(from, false, through, true).clear();
        }
    }

    /** The DUM of each record, in evidence order (care date, then source reference). */
    private static List<Candidate> candidates(List<CanonicalCareEvent> care) {
        List<Candidate> candidates = new ArrayList<>();
        for (CanonicalCareEvent event : care) {
            EventRef ref = EventRef.of(event);
            LocalDate dum = dumOf(ref.date(), event);
            if (dum != null) {
                candidates.add(new Candidate(dum, ref));
            }
        }
        candidates.sort((a, b) -> EventRef.ORDER.compare(a.ref(), b.ref()));
        return candidates;
    }

    /** The recorded DUM when valid, else the one derived from the gestational age; may be null. */
    private static LocalDate dumOf(LocalDate careDate, CanonicalCareEvent event) {
        if (careDate == null) {
            return null;
        }
        LocalDate lmp = C3Dates.parse(event.lmpDate());
        boolean valid =
                lmp != null && C3Dates.within(lmp, careDate.minusDays(GestationWindow.MAX_PREGNANCY_DAYS), careDate);
        return valid ? lmp : fromGestationalAge(careDate, event.gestationalAgeWeeks());
    }

    private static LocalDate fromGestationalAge(LocalDate careDate, String weeks) {
        if (weeks == null || !WEEKS.matcher(weeks.strip()).matches()) {
            return null;
        }
        int value = Integer.parseInt(weeks.strip());
        if (value < 1 || value > C3Codes.MAX_GESTATIONAL_WEEKS) {
            return null; // LEDI accepts 1 to 42 weeks
        }
        return careDate.minusDays((long) DAYS_PER_WEEK * value);
    }

    private static NavigableSet<LocalDate> outcomeDates(List<CanonicalPregnancyOutcome> outcomes) {
        NavigableSet<LocalDate> dates = new TreeSet<>();
        for (CanonicalPregnancyOutcome outcome : outcomes) {
            LocalDate date = C3Dates.parse(outcome.outcomeDate());
            if (date != null) {
                dates.add(date);
            }
        }
        return dates;
    }

    /**
     * The resolution dates of the pregnancy condition W78 (CIAP-2, exact) marked resolved in the
     * LPC: the PEC resolves it on the outcome (guia T3, manual do PEC; gap L2). Other 24 f codes
     * resolved do not end the pregnancy.
     */
    private static NavigableSet<LocalDate> resolutionDates(List<CanonicalCondition> conditions) {
        NavigableSet<LocalDate> dates = new TreeSet<>();
        for (CanonicalCondition condition : conditions) {
            LocalDate date = C3Dates.parse(condition.resolvedDate());
            boolean pregnancy = CodeMatch.CIAP2.equals(C3Codes.token(condition.codeSystem()))
                    && C3Codes.PREGNANCY_CONDITION_CIAP.equals(C3Codes.normalized(condition.code()));
            if (date != null && pregnancy && C3Codes.resolved(condition)) {
                dates.add(date);
            }
        }
        return dates;
    }

    /** The first date in {@code (from, from + days]}, or {@code null}. */
    private static LocalDate within(NavigableSet<LocalDate> dates, LocalDate from, int days) {
        LocalDate next = dates.higher(from);
        return next != null && !next.isAfter(from.plusDays(days)) ? next : null;
    }
}
