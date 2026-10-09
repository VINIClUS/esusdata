package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.Classification;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.reconciliation.ProbeDiff.Divergence;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The divergence a probe reports: teams of the revision whose result changed. Invented INEs only. */
class ProbeDiffTest {

    private static final String REVISION_TEAM = "0000000011";
    private static final String OTHER_TEAM = "0000000012";
    private static final String OUTSIDE_TEAM = "0000000099";
    private static final Map<String, String> REVISION =
            Map.of(REVISION_TEAM, SiapsParser.ESF, OTHER_TEAM, SiapsParser.EAP);
    private static final String JANUARY = "2026-01";
    private static final String FEBRUARY = "2026-02";

    private static IndicatorResult result(
            String month, long numerator, long denominator, Classification classification) {
        return new IndicatorResult(
                IndicatorStatus.COMPUTED,
                numerator + "/" + denominator,
                BigInteger.valueOf(numerator),
                BigInteger.valueOf(denominator),
                "PEOPLE",
                classification,
                month,
                "fixture@1.0.0",
                month + "-28",
                "3541307",
                List.of(),
                "policy@1");
    }

    private static TeamResult team(String ine, String month, long numerator, long denominator) {
        return new TeamResult(ine, "0000001", result(month, numerator, denominator, Classification.BOM));
    }

    private static EvidenceItem person(String key, String ine, EvidenceDecision decision) {
        return new EvidenceItem(
                EvidenceSubjectKind.PERSON, key, null, null, "A", decision, null, null, null, ine, null, null);
    }

    private static RuleOutcome month(String month, List<TeamResult> teams, List<EvidenceItem> evidence) {
        return new RuleOutcome(result(month, 0, 0, null), teams, evidence);
    }

    @Test
    void theSameOutcomesDivergeNowhere() {
        List<RuleOutcome> outcomes = List.of(month(JANUARY, List.of(team(REVISION_TEAM, JANUARY, 3, 10)), List.of()));

        Divergence divergence = ProbeDiff.compare(outcomes, outcomes, REVISION);

        assertThat(divergence.teams()).isZero();
        assertThat(divergence.subjects()).isZero();
        assertThat(divergence.localDetail()).isEmpty();
    }

    @Test
    void aTeamOfTheRevisionWhoseNumeratorChangesInAnyMonthIsDivergentOnce() {
        List<RuleOutcome> baseline = List.of(
                month(JANUARY, List.of(team(REVISION_TEAM, JANUARY, 3, 10)), List.of()),
                month(FEBRUARY, List.of(team(REVISION_TEAM, FEBRUARY, 3, 10)), List.of()));
        List<RuleOutcome> other = List.of(
                month(JANUARY, List.of(team(REVISION_TEAM, JANUARY, 4, 10)), List.of()),
                month(FEBRUARY, List.of(team(REVISION_TEAM, FEBRUARY, 5, 10)), List.of()));

        Divergence divergence = ProbeDiff.compare(baseline, other, REVISION);

        assertThat(divergence.teams()).isEqualTo(1);
        assertThat(divergence.localDetail()).hasSize(2).allMatch(line -> line.startsWith(REVISION_TEAM));
    }

    @Test
    void aTeamOutsideTheRevisionIsNotCounted() {
        List<RuleOutcome> baseline = List.of(month(JANUARY, List.of(team(OUTSIDE_TEAM, JANUARY, 3, 10)), List.of()));
        List<RuleOutcome> other = List.of(month(JANUARY, List.of(team(OUTSIDE_TEAM, JANUARY, 9, 10)), List.of()));

        assertThat(ProbeDiff.compare(baseline, other, REVISION).teams()).isZero();
    }

    @Test
    void aTeamOfTheRevisionWithAResultOnOneSideOnlyIsDivergent() {
        List<RuleOutcome> baseline = List.of(month(JANUARY, List.of(team(OTHER_TEAM, JANUARY, 1, 2)), List.of()));
        List<RuleOutcome> other = List.of(month(JANUARY, List.of(), List.of()));

        assertThat(ProbeDiff.compare(baseline, other, REVISION).teams()).isEqualTo(1);
    }

    @Test
    void subjectsThatFlipInOppositeDirectionsChangeNoPublishedResult() {
        List<TeamResult> unchanged = List.of(team(REVISION_TEAM, JANUARY, 1, 2));
        List<RuleOutcome> baseline = List.of(month(
                JANUARY,
                unchanged,
                List.of(
                        person("p1", REVISION_TEAM, EvidenceDecision.IN_NUMERATOR),
                        person("p2", REVISION_TEAM, EvidenceDecision.DENOMINATOR_ONLY))));
        List<RuleOutcome> other = List.of(month(
                JANUARY,
                unchanged,
                List.of(
                        person("p1", REVISION_TEAM, EvidenceDecision.DENOMINATOR_ONLY),
                        person("p2", REVISION_TEAM, EvidenceDecision.IN_NUMERATOR))));

        Divergence divergence = ProbeDiff.compare(baseline, other, REVISION);

        assertThat(divergence.teams()).isZero();
        assertThat(divergence.subjects()).isEqualTo(2);
    }

    @Test
    void readingsOfDifferentMonthsAreRefused() {
        List<RuleOutcome> january = List.of(month(JANUARY, List.of(), List.of()));
        List<RuleOutcome> february = List.of(month(FEBRUARY, List.of(), List.of()));

        assertThatThrownBy(() -> ProbeDiff.compare(january, february, REVISION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2026-01 in the baseline");
    }

    @Test
    void readingsOfADifferentNumberOfMonthsAreRefused() {
        List<RuleOutcome> one = List.of(month(JANUARY, List.of(), List.of()));
        List<RuleOutcome> none = List.of();

        assertThatThrownBy(() -> ProbeDiff.compare(one, none, REVISION))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same months");
    }
}
