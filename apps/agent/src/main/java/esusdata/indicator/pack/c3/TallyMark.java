package esusdata.indicator.pack.c3;

import java.util.List;

/**
 * One unit a practice counts toward its threshold: certain ({@code ambiguity == null}) or only
 * under one reading of the ficha ("talvez"), with the events behind it.
 */
record TallyMark(Ambiguity ambiguity, List<EventRef> events) {
    TallyMark {
        events = List.copyOf(events);
    }

    static TallyMark of(EventRef event, Ambiguity ambiguity) {
        return new TallyMark(ambiguity, List.of(event));
    }

    boolean certain() {
        return ambiguity == null;
    }
}
