package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalPregnancyOutcome;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Builds a person's pregnancies from the MIAI (4.1, item 17): every DUM recorded, or derived from
 * the gestational age ({@code careDate − 7 × IG}), is a candidate. The first unassigned candidate
 * c0 opens an episode that ends on the first outcome recorded in {@code (c0, c0 + 294]}, else on
 * c0 + 294; the candidates up to that end belong to it, the next one opens another episode.
 */
final class Episodes {

    private static final Pattern WEEKS = Pattern.compile("\\d{1,2}");
    private static final int DAYS_PER_WEEK = 7;

    private Episodes() {}

    static List<Episode> of(PersonRecords person) {
        NavigableMap<LocalDate, EventRef> candidates = candidates(person.individualCare());
        NavigableSet<LocalDate> outcomes = outcomeDates(person.outcomes());
        NavigableSet<LocalDate> resolutions = resolutionDates(person.conditions());
        List<Episode> episodes = new ArrayList<>();
        while (!candidates.isEmpty()) {
            LocalDate first = candidates.firstKey();
            EventRef anchor = candidates.firstEntry().getValue();
            GestationWindow primary = window(first, outcomes, resolutions);
            NavigableMap<LocalDate, EventRef> members = candidates.headMap(primary.end(), true);
            LocalDate last = members.lastKey();
            List<GestationWindow> readings = new ArrayList<>();
            readings.add(primary);
            boolean substitute = primary.endSource() == GestationWindow.EndSource.SUBSTITUTE_294;
            if (last.isAfter(first)) {
                readings.add(
                        substitute
                                ? GestationWindow.substitute(last)
                                : new GestationWindow(last, primary.end(), primary.endSource()));
            }
            // AMB-C3-05: an outcome or LPC resolution only in (c0 + 294, c0 + 336].
            boolean late = substitute
                    && (within(outcomes, primary.end(), GestationWindow.PUERPERIUM_DAYS) != null
                            || within(resolutions, primary.end(), GestationWindow.PUERPERIUM_DAYS) != null);
            episodes.add(new Episode(person.personKey() + "#" + first, readings, anchor, late));
            members.clear();
        }
        return episodes;
    }

    /** The end D by precedence: recorded outcome, LPC resolution, DUM + 294 (EMENDA 1). */
    private static GestationWindow window(
            LocalDate dum, NavigableSet<LocalDate> outcomes, NavigableSet<LocalDate> resolutions) {
        LocalDate outcome = within(outcomes, dum, GestationWindow.MAX_PREGNANCY_DAYS);
        if (outcome != null) {
            return new GestationWindow(dum, outcome, GestationWindow.EndSource.RECORDED_OUTCOME);
        }
        LocalDate resolved = within(resolutions, dum, GestationWindow.MAX_PREGNANCY_DAYS);
        return resolved == null
                ? GestationWindow.substitute(dum)
                : new GestationWindow(dum, resolved, GestationWindow.EndSource.LPC_RESOLUTION);
    }

    /** Each candidate DUM with the first record (in evidence order) that gives it. */
    private static NavigableMap<LocalDate, EventRef> candidates(List<CanonicalCareEvent> care) {
        NavigableMap<LocalDate, EventRef> candidates = new TreeMap<>();
        for (CanonicalCareEvent event : care) {
            EventRef ref = EventRef.of(event);
            LocalDate recorded = C3Dates.parse(event.lmpDate());
            if (recorded != null) {
                candidates.merge(recorded, ref, Episodes::earlier);
            }
            LocalDate derived = fromGestationalAge(ref.date(), event.gestationalAgeWeeks());
            if (derived != null) {
                candidates.merge(derived, ref, Episodes::earlier);
            }
        }
        return candidates;
    }

    private static LocalDate fromGestationalAge(LocalDate careDate, String weeks) {
        if (careDate == null || weeks == null || !WEEKS.matcher(weeks.strip()).matches()) {
            return null;
        }
        return careDate.minusDays((long) DAYS_PER_WEEK * Integer.parseInt(weeks.strip()));
    }

    private static EventRef earlier(EventRef a, EventRef b) {
        return EventRef.ORDER.compare(a, b) <= 0 ? a : b;
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
     * The resolution dates of pregnancy conditions (24 f, exact code) marked resolved in the LPC:
     * the PEC writes the outcome date there (gap L2).
     */
    private static NavigableSet<LocalDate> resolutionDates(List<CanonicalCondition> conditions) {
        NavigableSet<LocalDate> dates = new TreeSet<>();
        for (CanonicalCondition condition : conditions) {
            LocalDate date = C3Dates.parse(condition.resolvedDate());
            boolean resolved = condition.status() != null
                    && C3Codes.CONDITION_RESOLVED.equals(condition.status().strip());
            CodeMatch match = CodeMatch.of(condition, C3Codes.PREGNANCY_CIAP, C3Codes.PREGNANCY_CID);
            if (date != null && resolved && match == CodeMatch.EXACT) {
                dates.add(date);
            }
        }
        return dates;
    }

    /** The first date in {@code (from, from + days]}, or {@code null}. */
    private static LocalDate within(NavigableSet<LocalDate> outcomes, LocalDate from, int days) {
        LocalDate next = outcomes.higher(from);
        return next != null && !next.isAfter(from.plusDays(days)) ? next : null;
    }
}
