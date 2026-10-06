package esusdata.indicator.pack.c3;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * The count of a practice with threshold n: met when the units counted reach n. A unit is one
 * record, one pair of records (D) or one day with at least one record (B, C, D, E): several
 * records of the same day are one unit and the surplus is discarded, never counted.
 */
final class Tally {

    /** One countable unit: the events behind it (a record, or the pair of weight and height). */
    record Unit(LocalDate day, List<EventRef> events) {
        Unit {
            events = List.copyOf(events);
        }

        static Unit of(EventRef event) {
            return new Unit(event.date(), List.of(event));
        }
    }

    private Tally() {}

    /** Met when {@code units} number at least {@code threshold}. */
    static PracticeOutcome decide(int threshold, List<Unit> units) {
        return units.size() >= threshold ? PracticeOutcome.met(supports(units)) : PracticeOutcome.NOT_MET;
    }

    /** One unit per distinct day: the first of the day in {@link EventRef#ORDER}. */
    static List<Unit> distinctDays(List<Unit> units) {
        SortedMap<LocalDate, Unit> byDay = new TreeMap<>();
        for (Unit unit : units) {
            byDay.merge(unit.day(), unit, Tally::first);
        }
        return List.copyOf(byDay.values());
    }

    private static Unit first(Unit a, Unit b) {
        return EventRef.ORDER.compare(a.events().get(0), b.events().get(0)) <= 0 ? a : b;
    }

    /** The events of the units, each source record once (MET-32), in {@link EventRef#ORDER}. */
    static List<EventRef> supports(List<Unit> units) {
        Map<String, EventRef> byRef = new LinkedHashMap<>();
        for (Unit unit : units) {
            for (EventRef event : unit.events()) {
                byRef.putIfAbsent(event.refKey(), event);
            }
        }
        List<EventRef> list = new ArrayList<>(byRef.values());
        list.sort(EventRef.ORDER);
        return List.copyOf(list);
    }
}
