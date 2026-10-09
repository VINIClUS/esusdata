package esusdata.indicator.reconciliation;

import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * How much another reading of a ficha changes the local result of one quadrimestre (spec §9.4,
 * §9.5). A probe gets the outcomes of the other reading (by rewriting the dataset, or through a
 * hook of its pack), compares them with the baseline here, and reports {@link Divergence#teams()}
 * as its {@code divergent} count.
 *
 * <p>The unit of divergence is the team of the revision, because that is what an official revision
 * publishes: a team whose monthly status, NM, DN, value or class is not the same under the two
 * readings. A local team outside the revision is reported and excluded by the comparison, so it is
 * not counted. Subjects whose evidence changes are counted too, over every team, but only as local
 * detail: two subjects of a team that flip in opposite directions leave its published result as it
 * was.
 */
public final class ProbeDiff {

    private static final String ARROW = " -> ";

    private ProbeDiff() {}

    /**
     * What the other reading changed. Its parts name teams and subjects, so it stays local: a
     * probe reports {@link #teams()} as its {@code divergent} count and puts {@link #localDetail()}
     * in its local detail, and {@link #toString()} says the counts only.
     *
     * @param changedTeams the INEs of the teams of the revision whose result changed in at least
     *     one month
     * @param changedSubjects the subjects (people, episodes, or source records for C1) whose
     *     evidence changed in at least one month, in any team
     * @param localDetail one line per changed team and month
     */
    public record Divergence(Set<String> changedTeams, Set<String> changedSubjects, List<String> localDetail) {

        /** Nothing changed. */
        public static final Divergence NONE = new Divergence(Set.of(), Set.of(), List.of());

        public Divergence {
            changedTeams = Collections.unmodifiableSet(new TreeSet<>(changedTeams));
            changedSubjects = Collections.unmodifiableSet(new TreeSet<>(changedSubjects));
            localDetail = List.copyOf(localDetail);
        }

        /** The teams of the revision whose result changed: the {@code divergent} count of a probe. */
        public int teams() {
            return changedTeams.size();
        }

        /** The subjects whose evidence changed, in any team; local detail only. */
        public int subjects() {
            return changedSubjects.size();
        }

        /**
         * What changed under this reading or under {@code other}: a probe with several candidate
         * readings of the official side diverges wherever any of them does.
         */
        public Divergence plus(Divergence other) {
            Set<String> teams = new TreeSet<>(changedTeams);
            teams.addAll(other.changedTeams);
            Set<String> subjects = new TreeSet<>(changedSubjects);
            subjects.addAll(other.changedSubjects);
            List<String> detail = new ArrayList<>(localDetail);
            detail.addAll(other.localDetail);
            return new Divergence(teams, subjects, detail);
        }

        /** The counts only: a log line or an assertion message must not carry an INE. */
        @Override
        public String toString() {
            return "Divergence[teams=" + teams() + ", subjects=" + subjects() + "]";
        }
    }

    /**
     * Compares the baseline with the other reading, month by month.
     *
     * @param baseline the outcomes of the rule as it is, one per month in order
     * @param alternative the outcomes under the other reading, for the same months in the same order
     * @param revisionTeams the teams of the revision ({@link ProbeContext#revisionTeams()})
     * @throws IllegalArgumentException when the two lists do not cover the same months
     */
    public static Divergence compare(
            List<RuleOutcome> baseline, List<RuleOutcome> alternative, Map<String, String> revisionTeams) {
        if (baseline.size() != alternative.size()) {
            throw new IllegalArgumentException("the two readings must cover the same months");
        }
        Set<String> teams = new TreeSet<>();
        Set<String> subjects = new TreeSet<>();
        List<String> detail = new ArrayList<>();
        for (int month = 0; month < baseline.size(); month++) {
            RuleOutcome before = baseline.get(month);
            RuleOutcome after = alternative.get(month);
            String period = before.result().referencePeriod();
            if (!Objects.equals(period, after.result().referencePeriod())) {
                throw new IllegalArgumentException("month " + month + " is " + period + " in the baseline and "
                        + after.result().referencePeriod() + " in the other reading");
            }
            changedTeams(before, after, revisionTeams.keySet(), period, teams, detail);
            subjects.addAll(changedSubjects(before, after));
        }
        return new Divergence(teams, subjects, detail);
    }

    private static void changedTeams(
            RuleOutcome before,
            RuleOutcome after,
            Set<String> revision,
            String period,
            Set<String> changed,
            List<String> detail) {
        Map<String, IndicatorResult> was = byIne(before.teams());
        Map<String, IndicatorResult> is = byIne(after.teams());
        for (String ine : revision) {
            IndicatorResult a = was.get(ine);
            IndicatorResult b = is.get(ine);
            if (!sameResult(a, b)) {
                changed.add(ine);
                detail.add(ine + " " + period + ": " + describe(a) + ARROW + describe(b));
            }
        }
    }

    /** The results by INE; a bucket without an INE is no team of any revision and is left out. */
    private static Map<String, IndicatorResult> byIne(List<TeamResult> teams) {
        Map<String, IndicatorResult> results = new TreeMap<>();
        for (TeamResult team : teams) {
            if (team.ine() != null) {
                results.put(team.ine(), team.result());
            }
        }
        return results;
    }

    /**
     * Same status, NM, DN, value and class. A team with no denominator publishes no value, as a
     * team with no result does, so the two are the same; a value on one side only is a change.
     */
    private static boolean sameResult(IndicatorResult a, IndicatorResult b) {
        if (publishesNothing(a) || publishesNothing(b)) {
            return publishesNothing(a) && publishesNothing(b);
        }
        return a.status() == b.status()
                && Objects.equals(a.numerator(), b.numerator())
                && Objects.equals(a.denominator(), b.denominator())
                && a.classification() == b.classification()
                && sameValue(a.valueExact(), b.valueExact());
    }

    private static boolean publishesNothing(IndicatorResult result) {
        return result == null || result.status() == IndicatorStatus.NO_DENOMINATOR;
    }

    private static boolean sameValue(ExactRatio a, ExactRatio b) {
        if (a == null || b == null) {
            return a == null && b == null;
        }
        return a.compareTo(b) == 0;
    }

    private static String describe(IndicatorResult result) {
        if (result == null) {
            return "absent";
        }
        return result.status() + " NM " + result.numerator() + " DN " + result.denominator() + " "
                + result.classification();
    }

    /** The subjects whose evidence rows are not the same in the two outcomes of one month. */
    private static Set<String> changedSubjects(RuleOutcome before, RuleOutcome after) {
        Map<String, List<String>> was = evidenceBySubject(before.evidence());
        Map<String, List<String>> is = evidenceBySubject(after.evidence());
        Set<String> keys = new TreeSet<>(was.keySet());
        keys.addAll(is.keySet());
        Set<String> changed = new TreeSet<>();
        for (String key : keys) {
            if (!Objects.equals(was.get(key), is.get(key))) {
                changed.add(key);
            }
        }
        return changed;
    }

    private static Map<String, List<String>> evidenceBySubject(List<EvidenceItem> evidence) {
        Map<String, List<String>> bySubject = new TreeMap<>();
        for (EvidenceItem item : evidence) {
            String key = item.subjectKind() == EvidenceSubjectKind.EVENT
                    ? "event " + item.sourceRef()
                    : item.subjectKind() + " " + item.subjectKey();
            bySubject
                    .computeIfAbsent(key, ignored -> new ArrayList<>())
                    .add(item.component() + "|" + item.decision() + "|" + item.points() + "|" + item.ine());
        }
        bySubject.values().forEach(rows -> rows.sort(null));
        return bySubject;
    }
}
