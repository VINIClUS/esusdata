package esusdata.indicator.pack.c2;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Predicate;

/** Decides a practice over every combination of the readings that touch it. */
final class Readings {

    private Readings() {}

    /** The verdict over all combinations: met, not met, or the ambiguities that flip it. */
    record Verdict(boolean met, SortedSet<String> ambiguities) {
        boolean ambiguous() {
            return !ambiguities.isEmpty();
        }
    }

    static Verdict decide(List<Reading> readings, Predicate<Set<Reading>> satisfied) {
        int combinations = 1 << readings.size();
        boolean[] results = new boolean[combinations];
        boolean any = false;
        boolean all = true;
        for (int mask = 0; mask < combinations; mask++) {
            results[mask] = satisfied.test(select(readings, mask));
            any |= results[mask];
            all &= results[mask];
        }
        SortedSet<String> flips = new TreeSet<>();
        if (any && !all) {
            for (int bit = 0; bit < readings.size(); bit++) {
                if (flips(results, 1 << bit)) {
                    flips.add(readings.get(bit).code());
                }
            }
        }
        return new Verdict(all, flips);
    }

    private static boolean flips(boolean[] results, int bit) {
        for (int mask = 0; mask < results.length; mask++) {
            if ((mask & bit) == 0 && results[mask] != results[mask | bit]) {
                return true;
            }
        }
        return false;
    }

    private static Set<Reading> select(List<Reading> readings, int mask) {
        Set<Reading> chosen = EnumSet.noneOf(Reading.class);
        for (int bit = 0; bit < readings.size(); bit++) {
            if ((mask & (1 << bit)) != 0) {
                chosen.add(readings.get(bit));
            }
        }
        return chosen;
    }
}
