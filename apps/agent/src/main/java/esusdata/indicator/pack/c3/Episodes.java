package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
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
        List<Episode> episodes = new ArrayList<>();
        while (!candidates.isEmpty()) {
            LocalDate first = candidates.firstKey();
            EventRef anchor = candidates.firstEntry().getValue();
            LocalDate outcome = outcomeWithin(outcomes, first, GestationWindow.MAX_PREGNANCY_DAYS);
            GestationWindow primary =
                    outcome == null ? GestationWindow.substitute(first) : GestationWindow.recorded(first, outcome);
            NavigableMap<LocalDate, EventRef> members = candidates.headMap(primary.end(), true);
            LocalDate last = members.lastKey();
            List<GestationWindow> readings = new ArrayList<>();
            readings.add(primary);
            if (last.isAfter(first)) {
                readings.add(
                        outcome == null ? GestationWindow.substitute(last) : GestationWindow.recorded(last, outcome));
            }
            // AMB-C3-05: an outcome recorded in (c0 + 294, c0 + 336].
            boolean late =
                    outcome == null && outcomeWithin(outcomes, primary.end(), GestationWindow.PUERPERIUM_DAYS) != null;
            episodes.add(new Episode(person.personKey() + "#" + first, readings, anchor, late));
            members.clear();
        }
        return episodes;
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

    /** The first outcome in {@code (from, from + days]}, or {@code null}. */
    private static LocalDate outcomeWithin(NavigableSet<LocalDate> outcomes, LocalDate from, int days) {
        LocalDate next = outcomes.higher(from);
        return next != null && !next.isAfter(from.plusDays(days)) ? next : null;
    }
}
