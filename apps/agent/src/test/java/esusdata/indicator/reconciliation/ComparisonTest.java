package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.Classification;
import esusdata.indicator.reconciliation.Comparison.RowResult;
import esusdata.indicator.reconciliation.Comparison.TeamSplit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ComparisonTest {

    @Test
    void equalDistributionsAreAtDistanceZero() {
        ClassCounts counts = new ClassCounts(1, 2, 3, 4);

        assertThat(Comparison.distance(counts, counts)).isZero();
        assertThat(Comparison.row("eSF", counts, counts, 0).passed()).isTrue();
    }

    @Test
    void oneTeamOneStepOffCostsOne() {
        ClassCounts siaps = new ClassCounts(0, 0, 3, 0);
        ClassCounts local = new ClassCounts(0, 1, 2, 0);

        assertThat(Comparison.distance(local, siaps)).isEqualTo(1);
        assertThat(Comparison.distance(siaps, local)).isEqualTo(1);
    }

    @Test
    void oneTeamTwoStepsOffCostsTwo() {
        assertThat(Comparison.distance(new ClassCounts(1, 0, 0, 0), new ClassCounts(0, 0, 1, 0)))
                .isEqualTo(2);
    }

    @Test
    void aMissingTeamCostsAccordingToItsClass() {
        ClassCounts siaps = new ClassCounts(0, 0, 3, 0);

        assertThat(Comparison.distance(new ClassCounts(0, 0, 2, 0), siaps)).isEqualTo(2);
        assertThat(Comparison.distance(new ClassCounts(0, 0, 3, 0), new ClassCounts(0, 0, 3, 1)))
                .isEqualTo(1);
    }

    @Test
    void anExtraTeamCostsAccordingToItsClass() {
        ClassCounts siaps = new ClassCounts(0, 0, 3, 0);

        assertThat(Comparison.distance(new ClassCounts(0, 0, 3, 1), siaps)).isEqualTo(1);
        assertThat(Comparison.distance(new ClassCounts(1, 0, 3, 0), siaps)).isEqualTo(4);
    }

    @Test
    void thresholdIsTheLargerOfTwoAndFifteenPercentRoundedUp() {
        assertThat(Comparison.threshold(0)).isEqualTo(2);
        assertThat(Comparison.threshold(12)).isEqualTo(2);
        assertThat(Comparison.threshold(14)).isEqualTo(3); // 2.1 rounds up
        assertThat(Comparison.threshold(20)).isEqualTo(3);
        assertThat(Comparison.threshold(21)).isEqualTo(4);
        assertThat(Comparison.threshold(100)).isEqualTo(15);
    }

    @Test
    void aRowPassesExactlyAtTheThreshold() {
        ClassCounts siaps = new ClassCounts(0, 0, 12, 0);

        assertThat(Comparison.row("eSF", siaps, new ClassCounts(0, 0, 11, 0), 0).passed())
                .isTrue(); // D = 2 = T
        RowResult over = Comparison.row("eSF", siaps, new ClassCounts(0, 0, 10, 0), 0);
        assertThat(over.distance()).isEqualTo(4);
        assertThat(over.passed()).isFalse();
    }

    @Test
    void aRowWithNoTeamOnEitherSideIsNotEvaluated() {
        RowResult row = Comparison.row("eAP", ClassCounts.EMPTY, ClassCounts.EMPTY, 0);

        assertThat(row.evaluated()).isFalse();
        assertThat(row.passed()).isFalse();
    }

    @Test
    void aRowPresentOnOneSideOnlyIsEvaluatedWithTheSameFormula() {
        RowResult localOnly = Comparison.row("eAP", ClassCounts.EMPTY, new ClassCounts(0, 0, 0, 2), 0);
        RowResult siapsOnly = Comparison.row("eAP", new ClassCounts(1, 0, 0, 2), ClassCounts.EMPTY, 3);

        assertThat(localOnly.evaluated()).isTrue();
        assertThat(localOnly.distance()).isEqualTo(2);
        assertThat(localOnly.passed()).isTrue();
        assertThat(siapsOnly.distance()).isEqualTo(1 + 1 + 1 + 3);
        assertThat(siapsOnly.passed()).isFalse();
    }

    @Test
    void splitCountsTheListedTeamsOfOneTypeAndReportsThoseWithoutAClass() {
        List<SiapsSnapshot.Team> listed = List.of(
                new SiapsSnapshot.Team("0000000011", "eSF"),
                new SiapsSnapshot.Team("0000000012", "eSF"),
                new SiapsSnapshot.Team("0000000013", "eSF"),
                new SiapsSnapshot.Team("0000000014", "eAP"));
        Map<String, Classification> local = Map.of(
                "0000000011", Classification.BOM,
                "0000000012", Classification.OTIMO,
                "0000000014", Classification.REGULAR,
                "0000000099", Classification.BOM);

        TeamSplit esf = Comparison.split("eSF", listed, local);

        assertThat(esf.local()).isEqualTo(new ClassCounts(0, 0, 1, 1));
        assertThat(esf.semClasseLocal()).isEqualTo(1);
        assertThat(esf.inesWithoutClass()).containsExactly("0000000013");
        assertThat(Comparison.split("eAP", listed, local).local()).isEqualTo(new ClassCounts(1, 0, 0, 0));
    }

    @Test
    void cumulativeCountsFollowTheClassOrder() {
        ClassCounts counts = new ClassCounts(1, 2, 3, 4);

        assertThat(counts.cumulative(1)).isEqualTo(1);
        assertThat(counts.cumulative(2)).isEqualTo(3);
        assertThat(counts.cumulative(3)).isEqualTo(6);
        assertThat(counts.cumulative(4)).isEqualTo(10);
        assertThat(counts.total()).isEqualTo(10);
        assertThatThrownBy(() -> counts.cumulative(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ClassCounts(-1, 0, 0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(ClassCounts.EMPTY.plus(Classification.OTIMO).count(Classification.OTIMO))
                .isEqualTo(1);
    }
}
