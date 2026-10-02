package esusdata.indicator.pack.c3;

import java.time.LocalDate;
import java.util.List;

/**
 * One pregnancy of a person (4.1): its key ({@code personKey#menorDUM}), the readings of its dates
 * — the smallest candidate DUM first and, when the records disagree, the largest (AMB-C3-03 (i)) —
 * the record the smallest DUM came from, and the ambiguity its end date depends on, if any: an
 * outcome recorded only after DUM+294 (AMB-C3-05) or an LPC resolution whose code matches only by
 * prefix (AMB-C3-08).
 */
record Episode(String key, List<GestationWindow> readings, EventRef anchor, Ambiguity datesAmbiguity) {
    Episode {
        readings = List.copyOf(readings);
        if (readings.isEmpty()) {
            throw new IllegalArgumentException("an episode has at least one reading of its dates");
        }
    }

    /** The reading by the smallest DUM: the episode's key, D and evidence dates. */
    GestationWindow primary() {
        return readings.get(0);
    }

    /** The last day any reading still counts as this episode: the latest D + 42. */
    LocalDate coverageEnd() {
        LocalDate last = primary().boundaryDay();
        for (GestationWindow reading : readings) {
            if (reading.boundaryDay().isAfter(last)) {
                last = reading.boundaryDay();
            }
        }
        return last;
    }
}
