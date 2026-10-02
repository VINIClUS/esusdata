package esusdata.indicator.pack.c3;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The tri-state count of a practice with threshold n: certain marks {@code >= n} ⇒ met; certain
 * plus "talvez" {@code < n} ⇒ not met; otherwise the decision depends on a reading of the ficha
 * and stays ambiguous, citing the first undecided mark.
 */
final class Tally {

    private Tally() {}

    static PracticeOutcome decide(int threshold, List<TallyMark> marks) {
        List<TallyMark> certain = new ArrayList<>();
        Ambiguity first = null;
        for (TallyMark mark : marks) {
            if (mark.certain()) {
                certain.add(mark);
            } else if (first == null) {
                first = mark.ambiguity();
            }
        }
        if (certain.size() >= threshold) {
            return PracticeOutcome.met(supports(certain));
        }
        if (marks.size() < threshold) {
            return PracticeOutcome.NOT_MET;
        }
        return PracticeOutcome.ambiguous(first, supports(marks));
    }

    /**
     * The events of the marks, each source record once (MET-32): certain when any certain mark
     * holds it, in {@link EventRef#ORDER}.
     */
    static List<Support> supports(List<TallyMark> marks) {
        Map<String, Support> byRef = new LinkedHashMap<>();
        for (TallyMark mark : marks) {
            for (EventRef event : mark.events()) {
                Support held = byRef.get(event.refKey());
                if (held == null || mark.certain()) {
                    byRef.put(event.refKey(), new Support(event, mark.ambiguity()));
                }
            }
        }
        return sorted(byRef.values());
    }

    private static List<Support> sorted(Iterable<Support> supports) {
        List<Support> list = new ArrayList<>();
        supports.forEach(list::add);
        list.sort((a, b) -> EventRef.ORDER.compare(a.event(), b.event()));
        return List.copyOf(list);
    }
}
