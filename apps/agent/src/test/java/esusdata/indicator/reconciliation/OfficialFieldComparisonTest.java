package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.Classification;
import esusdata.indicator.reconciliation.OfficialFieldComparison.TeamPair;
import esusdata.indicator.reconciliation.OfficialFieldComparison.Values;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class OfficialFieldComparisonTest {

    private static final String INE = "0000000011";

    private static Values indicator(String score, Classification concept) {
        return new Values(new BigDecimal(score), concept, null, null);
    }

    @Test
    void identicalTeamsDifferInNothing() {
        OfficialFieldComparison comparison = OfficialFieldComparison.of(List.of(
                new TeamPair(INE, indicator("21.2", Classification.BOM), indicator("21.20", Classification.BOM))));

        assertThat(comparison.teamsCompared()).isEqualTo(1);
        assertThat(comparison.scoreDiffers()).isZero();
        assertThat(comparison.conceptDiffers()).isZero();
        assertThat(comparison.maxAbsScoreDifference()).isEqualByComparingTo("0");
        assertThat(comparison.localDetail()).isEmpty();
    }

    @Test
    void eachFieldIsCountedAndTheLargestScoreDifferenceKept() {
        Values localNota = new Values(null, null, new BigDecimal("7.5"), Classification.REGULAR);
        Values officialNota = new Values(null, null, new BigDecimal("8"), Classification.BOM);
        OfficialFieldComparison comparison = OfficialFieldComparison.of(List.of(
                new TeamPair(INE, indicator("10", Classification.BOM), indicator("12.5", Classification.OTIMO)),
                new TeamPair("0000000012", indicator("30", Classification.BOM), indicator("29", Classification.BOM)),
                new TeamPair("0000000013", localNota, officialNota)));

        assertThat(comparison.teamsCompared()).isEqualTo(3);
        assertThat(comparison.scoreDiffers()).isEqualTo(2);
        assertThat(comparison.conceptDiffers()).isEqualTo(1);
        assertThat(comparison.finalNoteDiffers()).isEqualTo(1);
        assertThat(comparison.finalClassDiffers()).isEqualTo(1);
        assertThat(comparison.maxAbsScoreDifference()).isEqualByComparingTo("2.5");
        assertThat(comparison.localDetail()).hasSize(3).anyMatch(line -> line.startsWith(INE));
    }

    @Test
    void theVersionedFormAndTheTextHoldMaskedCountsAndNoIne() {
        OfficialFieldComparison comparison = OfficialFieldComparison.of(
                List.of(new TeamPair(INE, indicator("10", Classification.BOM), indicator("12", Classification.BOM))));

        assertThat(comparison.versionedForm())
                .containsEntry("teams_compared", "<10")
                .containsEntry("score_differs", "<10")
                .containsEntry("max_abs_score_difference", "2");
        assertThat(comparison.versionedForm().values()).noneMatch(value -> value.contains(INE));
        assertThat(comparison.toString()).doesNotContain(INE);
    }
}
