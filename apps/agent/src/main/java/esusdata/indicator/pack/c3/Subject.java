package esusdata.indicator.pack.c3;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * One subject of the cohort: an episode (or a person's pregnancy code without a DUM, {@code
 * personKey#sem-dum}), its link, its cohort verdict and, when eligible, its practices.
 * {@code episode} is {@code null} for a subject without a DUM.
 */
record Subject(
        String key, RegistrationLink link, Verdict verdict, Episode episode, Map<Practice, PracticeOutcome> practices) {
    Subject {
        practices = practices.isEmpty() ? Map.of() : Collections.unmodifiableMap(new EnumMap<>(practices));
    }

    boolean eligible() {
        return verdict.eligible();
    }

    /** The decision of a practice; {@code null} for a subject that was not scored. */
    PracticeOutcome practice(Practice practice) {
        return practices.get(practice);
    }

    /** The team of the link at the cutoff; {@code null} without a team. */
    String ine() {
        return link.ine();
    }
}
