package esusdata.indicator.sensitivity;

import esusdata.indicator.model.ExactRatio;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * The readings that act on subjects of a score pack (C2, C3): the aggregation per unit, the
 * frequency of each ambiguity code, the cohort readings (include, include without marking,
 * exclude) and the bounds of the practice-level ambiguities. Everything is computed from
 * {@link SubjectScore}s, so it never differs from what production decided for each subject.
 */
public final class SubjectReadings {

    /** The reading that keeps every ambiguity open, as production does. */
    public static final String PRODUCTION = "produção (ambiguidade em aberto)";

    private static final int SCALE = 4;

    private SubjectReadings() {}

    /** Denominator, ambiguous subjects and certain points of a group of subjects. */
    public record Tally(long denominator, long ambiguous, BigInteger points) {

        public static Tally of(Collection<SubjectScore> subjects) {
            long denominator = 0;
            long ambiguous = 0;
            BigInteger points = BigInteger.ZERO;
            for (SubjectScore subject : subjects) {
                if (subject.eligible()) {
                    denominator++;
                    points = points.add(subject.certainPoints());
                }
                if (subject.ambiguous()) {
                    ambiguous++;
                }
            }
            return new Tally(denominator, ambiguous, points);
        }

        /** Mean points with four decimals; {@code null} without a denominator. */
        public String mean() {
            if (denominator == 0) {
                return null;
            }
            return new ExactRatio(points, BigInteger.valueOf(denominator))
                    .toScaledBigDecimal(SCALE)
                    .toPlainString();
        }
    }

    /** The subjects of the municipality, then of each INE in order; subjects without a team only count in the first. */
    public static Map<String, List<SubjectScore>> byUnit(List<SubjectScore> subjects) {
        Map<String, List<SubjectScore>> units = new LinkedHashMap<>();
        units.put(ReadingRow.MUNICIPALITY, subjects);
        Map<String, List<SubjectScore>> byIne = new TreeMap<>();
        for (SubjectScore subject : subjects) {
            if (subject.ine() != null && !subject.ine().isBlank()) {
                byIne.computeIfAbsent(subject.ine(), k -> new ArrayList<>()).add(subject);
            }
        }
        units.putAll(byIne);
        return units;
    }

    /** How many of {@code subjects} depend on {@code code}. */
    public static long affected(Collection<SubjectScore> subjects, String code) {
        return subjects.stream().filter(s -> s.codes().contains(code)).count();
    }

    /** The row of one reading for one unit. */
    public static ReadingRow row(
            String pack, String code, String reading, String unit, long affected, Collection<SubjectScore> subjects) {
        Tally tally = Tally.of(subjects);
        return new ReadingRow(
                pack,
                code,
                reading,
                unit,
                null,
                affected,
                tally.points(),
                BigInteger.valueOf(tally.denominator()),
                tally.mean(),
                tally.ambiguous());
    }

    /** Every ambiguity code present, with the number of subjects that depend on it, per unit. */
    public static List<ReadingRow> frequencies(String pack, List<SubjectScore> subjects) {
        List<ReadingRow> rows = new ArrayList<>();
        byUnit(subjects).forEach((unit, members) -> {
            for (String code : allCodes(members, SubjectScore::codes)) {
                rows.add(new ReadingRow(
                        pack, code, ReadingRow.FREQUENCY, unit, null, affected(members, code), null, null, null, null));
            }
        });
        return rows;
    }

    /** The production tally per unit, the baseline every reading is read against. */
    public static List<ReadingRow> baseline(String pack, List<SubjectScore> subjects) {
        List<ReadingRow> rows = new ArrayList<>();
        byUnit(subjects)
                .forEach((unit, members) ->
                        rows.add(row(pack, "(todas)", PRODUCTION, unit, ambiguousCount(members), members)));
        return rows;
    }

    /**
     * For each cohort ambiguity (AMB-C2-02, AMB-C2-03, AMB-C3-03…): the cohort with the affected
     * subjects kept and marked (production), kept without the mark, and left out.
     */
    public static List<ReadingRow> cohortReadings(String pack, List<SubjectScore> subjects) {
        List<ReadingRow> rows = new ArrayList<>();
        for (String code : allCodes(subjects, SubjectScore::cohortCodes)) {
            Map<String, Function<List<SubjectScore>, List<SubjectScore>>> readings = new LinkedHashMap<>();
            readings.put("incluir e marcar ambígua (produção)", members -> members);
            readings.put(
                    "incluir sem marcar ambiguidade",
                    members ->
                            members.stream().map(s -> s.withoutCohortCode(code)).toList());
            readings.put(
                    "excluir da coorte",
                    members -> members.stream()
                            .filter(s -> !s.cohortCodes().contains(code))
                            .toList());
            byUnit(subjects).forEach((unit, members) -> {
                long affected = affected(members, code);
                readings.forEach(
                        (name, transform) -> rows.add(row(pack, code, name, unit, affected, transform.apply(members))));
            });
        }
        return rows;
    }

    /**
     * For each practice-level ambiguity: the unit's score with every practice that depends on the
     * code not met (lower bound) and met (upper bound). Subjects ambiguous for another code stay
     * ambiguous ({@code remaining}). These are bounds, not a reading: different subjects flip in
     * different directions under a real reading.
     */
    public static List<ReadingRow> practiceBounds(String pack, List<SubjectScore> subjects) {
        List<ReadingRow> rows = new ArrayList<>();
        for (String code : allCodes(subjects, SubjectReadings::practiceCodes)) {
            byUnit(subjects).forEach((unit, members) -> {
                long affected = affected(members, code);
                rows.add(row(
                        pack,
                        code,
                        "limite inferior (práticas que dependem do código não cumpridas)",
                        unit,
                        affected,
                        resolved(members, code, false)));
                rows.add(row(
                        pack,
                        code,
                        "limite superior (práticas que dependem do código cumpridas)",
                        unit,
                        affected,
                        resolved(members, code, true)));
            });
        }
        return rows;
    }

    private static List<SubjectScore> resolved(List<SubjectScore> members, String code, boolean met) {
        return members.stream().map(s -> s.resolving(code, met)).toList();
    }

    private static SortedSet<String> practiceCodes(SubjectScore subject) {
        SortedSet<String> codes = new TreeSet<>();
        subject.openPractices().values().forEach(open -> codes.addAll(open.codes()));
        return codes;
    }

    private static long ambiguousCount(Collection<SubjectScore> members) {
        return members.stream().filter(SubjectScore::ambiguous).count();
    }

    private static SortedSet<String> allCodes(
            Collection<SubjectScore> members, Function<SubjectScore, SortedSet<String>> codesOf) {
        SortedSet<String> codes = new TreeSet<>();
        members.forEach(s -> codes.addAll(codesOf.apply(s)));
        return codes;
    }
}
