package esusdata.indicator.pack.c3;

import java.time.LocalDate;

/**
 * One pregnancy of a person (4.1): its key ({@code personKey#DUM}), its dates — the DUM of the
 * first record that gives one (AMB-C3-03 (i)) — and that record.
 */
record Episode(String key, GestationWindow window, EventRef anchor) {

    /** The last day this episode still counts, D + 42. */
    LocalDate coverageEnd() {
        return window.lastDay();
    }
}
