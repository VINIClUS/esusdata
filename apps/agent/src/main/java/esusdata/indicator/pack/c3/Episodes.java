package esusdata.indicator.pack.c3;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalPregnancyOutcome;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
        Ends ends = new Ends(outcomeDates(person.outcomes()), resolutionDates(person.conditions()));
        List<Episode> episodes = new ArrayList<>();
        while (!candidates.isEmpty()) {
            LocalDate first = candidates.firstKey();
            EventRef anchor = candidates.firstEntry().getValue();
            GestationWindow primary = ends.window(first);
            NavigableMap<LocalDate, EventRef> members = candidates.headMap(primary.end(), true);
            LocalDate last = members.lastKey();
            List<GestationWindow> readings = new ArrayList<>();
            readings.add(primary);
            if (last.isAfter(first)) {
                readings.add(
                        primary.endSource() == GestationWindow.EndSource.SUBSTITUTE_294
                                ? GestationWindow.substitute(last)
                                : new GestationWindow(last, primary.end(), primary.endSource()));
            }
            episodes.add(new Episode(person.personKey() + "#" + first, readings, anchor, ends.ambiguity(primary)));
            members.clear();
        }
        return episodes;
    }

    /** The dates that can end a pregnancy: recorded outcomes and resolutions of W78 in the LPC. */
    private record Ends(NavigableSet<LocalDate> outcomes, NavigableSet<LocalDate> resolutions) {

        /** The end D by precedence: recorded outcome, LPC resolution, DUM + 294 (EMENDA 1). */
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
         * AMB-C3-05: without an outcome recorded in {@code (c0, c0 + 294]}, one recorded in {@code
         * (c0 + 294, c0 + 336]} — whatever the end used — or, on the substitute end, a late LPC
         * resolution.
         */
        Ambiguity ambiguity(GestationWindow primary) {
            LocalDate limit = primary.dum().plusDays(GestationWindow.MAX_PREGNANCY_DAYS);
            boolean lateOutcome = primary.endSource() != GestationWindow.EndSource.RECORDED_OUTCOME
                    && within(outcomes, limit, GestationWindow.PUERPERIUM_DAYS) != null;
            boolean lateResolution = primary.endSource() == GestationWindow.EndSource.SUBSTITUTE_294
                    && within(resolutions, limit, GestationWindow.PUERPERIUM_DAYS) != null;
            return lateOutcome || lateResolution ? Ambiguity.AMB_C3_05 : null;
        }
    }

    /**
     * Each candidate DUM with the first record (in evidence order) that gives it. A DUM derived from
     * the IG (whole weeks) 0 to 6 days after a recorded DUM is the same date read coarsely, not
     * another candidate.
     */
    private static NavigableMap<LocalDate, EventRef> candidates(List<CanonicalCareEvent> care) {
        NavigableMap<LocalDate, EventRef> recorded = new TreeMap<>();
        NavigableMap<LocalDate, EventRef> derived = new TreeMap<>();
        for (CanonicalCareEvent event : care) {
            EventRef ref = EventRef.of(event);
            LocalDate lmp = C3Dates.parse(event.lmpDate());
            if (lmp != null) {
                recorded.merge(lmp, ref, Episodes::earlier);
            }
            LocalDate fromAge = fromGestationalAge(ref.date(), event.gestationalAgeWeeks());
            if (fromAge != null) {
                derived.merge(fromAge, ref, Episodes::earlier);
            }
        }
        NavigableMap<LocalDate, EventRef> candidates = new TreeMap<>(recorded);
        for (Map.Entry<LocalDate, EventRef> entry : derived.entrySet()) {
            LocalDate date = entry.getKey();
            if (recorded.subMap(date.minusDays(DAYS_PER_WEEK - 1L), true, date, true)
                    .isEmpty()) {
                candidates.merge(date, entry.getValue(), Episodes::earlier);
            }
        }
        return candidates;
    }

    private static LocalDate fromGestationalAge(LocalDate careDate, String weeks) {
        if (careDate == null || weeks == null || !WEEKS.matcher(weeks.strip()).matches()) {
            return null;
        }
        int value = Integer.parseInt(weeks.strip());
        if (value < 1 || value > C3Codes.MAX_GESTATIONAL_WEEKS) {
            return null; // LEDI accepts 1 to 42 weeks
        }
        return careDate.minusDays((long) DAYS_PER_WEEK * value);
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
    private static LocalDate within(NavigableSet<LocalDate> outcomes, LocalDate from, int days) {
        LocalDate next = outcomes.higher(from);
        return next != null && !next.isAfter(from.plusDays(days)) ? next : null;
    }
}
