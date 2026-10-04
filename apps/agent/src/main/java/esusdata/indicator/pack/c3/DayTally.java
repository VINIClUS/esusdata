package esusdata.indicator.pack.c3;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Counting by distinct day (B, C, D, E): a day with a certain item is one certain mark; every
 * other item of the same day is one "talvez" mark under the practice's same-day ambiguity; a day
 * with only undecided items gives one mark per item.
 */
final class DayTally {

    /** One countable item of a day: a record, or a pair of records (D). */
    record DayItem(LocalDate day, List<EventRef> events, Ambiguity ambiguity) {
        DayItem {
            events = List.copyOf(events);
        }

        static DayItem of(EventRef event, Ambiguity ambiguity) {
            return new DayItem(event.date(), List.of(event), ambiguity);
        }
    }

    private DayTally() {}

    static List<TallyMark> marks(List<DayItem> items, Ambiguity sameDay) {
        SortedMap<LocalDate, List<DayItem>> byDay = new TreeMap<>();
        for (DayItem item : items) {
            byDay.computeIfAbsent(item.day(), d -> new ArrayList<>()).add(item);
        }
        List<TallyMark> marks = new ArrayList<>();
        for (Map.Entry<LocalDate, List<DayItem>> day : byDay.entrySet()) {
            marks.addAll(dayMarks(day.getValue(), sameDay));
        }
        return marks;
    }

    private static List<TallyMark> dayMarks(List<DayItem> items, Ambiguity sameDay) {
        List<DayItem> ordered = new ArrayList<>(items);
        ordered.sort(
                (a, b) -> EventRef.ORDER.compare(a.events().get(0), b.events().get(0)));
        int lead = 0;
        for (int i = ordered.size() - 1; i >= 0; i--) {
            if (ordered.get(i).ambiguity() == null) {
                lead = i;
            }
        }
        List<TallyMark> marks = new ArrayList<>();
        marks.add(new TallyMark(ordered.get(lead).ambiguity(), ordered.get(lead).events()));
        for (int i = 0; i < ordered.size(); i++) {
            if (i != lead) {
                marks.add(new TallyMark(sameDay, ordered.get(i).events()));
            }
        }
        return marks;
    }
}
