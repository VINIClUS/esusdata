package esusdata.indicator.sensitivity;

import java.math.BigInteger;
import java.util.Collections;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * One subject (a person or an episode) of a score pack as the sensitivity harness sees it: the
 * team, whether it is in the denominator, the points of the practices decided for certain and
 * the ambiguity codes it depends on. {@code key} is the source's opaque key; it only groups rows
 * in memory and is never printed (see {@link #toString()}).
 *
 * @param cohortCodes ambiguities of the subject's inclusion in the cohort (AMB-C2-02, AMB-C2-03…)
 * @param openPractices practices the ficha leaves undecided for this subject, by practice code
 */
public record SubjectScore(
        String key,
        String ine,
        boolean eligible,
        BigInteger certainPoints,
        SortedSet<String> cohortCodes,
        SortedMap<String, OpenPractice> openPractices) {

    /** A practice whose outcome depends on {@code codes}; {@code weight} is what it would add if met. */
    public record OpenPractice(BigInteger weight, SortedSet<String> codes) {
        public OpenPractice {
            codes = Collections.unmodifiableSortedSet(new TreeSet<>(codes));
        }
    }

    public SubjectScore {
        cohortCodes = Collections.unmodifiableSortedSet(new TreeSet<>(cohortCodes));
        openPractices = Collections.unmodifiableSortedMap(new TreeMap<>(openPractices));
    }

    /** Undecided in the cohort or in at least one practice. */
    public boolean ambiguous() {
        return !cohortCodes.isEmpty() || !openPractices.isEmpty();
    }

    /** Every ambiguity code this subject depends on. */
    public SortedSet<String> codes() {
        SortedSet<String> all = new TreeSet<>(cohortCodes);
        openPractices.values().forEach(open -> all.addAll(open.codes()));
        return all;
    }

    /** The same subject with {@code code} no longer marked as a cohort ambiguity (included silently). */
    public SubjectScore withoutCohortCode(String code) {
        SortedSet<String> remaining = new TreeSet<>(cohortCodes);
        remaining.remove(code);
        return new SubjectScore(key, ine, eligible, certainPoints, remaining, openPractices);
    }

    /** The same subject with every practice that depends on {@code code} decided as met or not met. */
    public SubjectScore resolving(String code, boolean met) {
        SortedMap<String, OpenPractice> remaining = new TreeMap<>();
        BigInteger points = certainPoints;
        for (var entry : openPractices.entrySet()) {
            if (entry.getValue().codes().contains(code)) {
                points = met ? points.add(entry.getValue().weight()) : points;
            } else {
                remaining.put(entry.getKey(), entry.getValue());
            }
        }
        return new SubjectScore(key, ine, eligible, points, cohortCodes, remaining);
    }

    /** The same subject with extra cohort ambiguities (those the evidence does not carry). */
    public SubjectScore withCohortCodes(SortedSet<String> extra) {
        SortedSet<String> merged = new TreeSet<>(cohortCodes);
        merged.addAll(extra);
        return new SubjectScore(key, ine, eligible, certainPoints, merged, openPractices);
    }

    @Override
    public String toString() {
        return "SubjectScore[ine=" + ine + ", eligible=" + eligible + "]";
    }
}
