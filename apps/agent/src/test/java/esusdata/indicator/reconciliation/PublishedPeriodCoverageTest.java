package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.reconciliation.PeriodExecutionPlan.Decision;
import esusdata.source.SourceCoverageCheck;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The plan of a diagnostic run has an entry for every published period: it runs, or it names the
 * local months it lacks. Nothing a period needs, or the PEC happens to hold, drops one.
 */
class PublishedPeriodCoverageTest {

    private static final Quadrimestre Q2_2025 = new Quadrimestre(2025, 2);
    private static final Quadrimestre Q3_2025 = new Quadrimestre(2025, 3);
    private static final Quadrimestre Q1_2026 = new Quadrimestre(2026, 1);

    private static Set<YearMonth> months(YearMonth from, YearMonth to) {
        Set<YearMonth> months = new HashSet<>();
        for (YearMonth month = from; !month.isAfter(to); month = month.plusMonths(1)) {
            months.add(month);
        }
        return months;
    }

    @Test
    void everyPublishedPeriodIsInThePlanInTheOrderGivenWhateverTheCoverageIs() {
        List<Quadrimestre> published = List.of(Q1_2026, Q2_2025, Q3_2025);
        Set<YearMonth> coverage = months(YearMonth.of(2025, 9), YearMonth.of(2026, 4));

        List<PeriodExecutionPlan> plan = PublishedPeriodCoverage.plan(published, coverage);

        assertThat(plan).extracting(PeriodExecutionPlan::quadrimestre).containsExactly(Q1_2026, Q2_2025, Q3_2025);
        assertThat(plan)
                .extracting(PeriodExecutionPlan::decision)
                .containsExactly(Decision.RUN, Decision.MISSING_LOCAL_MONTHS, Decision.RUN);
    }

    @Test
    void aPeriodWhoseFourMonthsAreHeldRunsEvenWhenThePecHoldsMore() {
        Set<YearMonth> coverage = months(YearMonth.of(2024, 1), YearMonth.of(2026, 12));

        List<PeriodExecutionPlan> plan = PublishedPeriodCoverage.plan(List.of(Q1_2026), coverage);

        assertThat(plan).singleElement().satisfies(entry -> {
            assertThat(entry.runs()).isTrue();
            assertThat(entry.missingMonths()).isEmpty();
        });
    }

    @Test
    void aPeriodMissingMonthsIsReportedWithExactlyTheMonthsItLacks() {
        Set<YearMonth> coverage = months(YearMonth.of(2026, 2), YearMonth.of(2026, 3));

        List<PeriodExecutionPlan> plan = PublishedPeriodCoverage.plan(List.of(Q1_2026), coverage);

        assertThat(plan).singleElement().satisfies(entry -> {
            assertThat(entry.decision()).isEqualTo(Decision.MISSING_LOCAL_MONTHS);
            assertThat(entry.runs()).isFalse();
            assertThat(entry.missingMonths()).containsExactly(YearMonth.of(2026, 1), YearMonth.of(2026, 4));
        });
    }

    @Test
    void withNoCoverageEveryPeriodIsReportedWithAllFourMonths() {
        List<PeriodExecutionPlan> plan = PublishedPeriodCoverage.plan(List.of(Q2_2025, Q1_2026), Set.of());

        assertThat(plan).hasSize(2).allSatisfy(entry -> {
            assertThat(entry.decision()).isEqualTo(Decision.MISSING_LOCAL_MONTHS);
            assertThat(entry.missingMonths())
                    .containsExactlyElementsOf(entry.quadrimestre().months());
        });
    }

    @Test
    void monthsOfNoPublishedPeriodAddNoEntry() {
        Set<YearMonth> coverage = months(YearMonth.of(2023, 1), YearMonth.of(2026, 4));

        assertThat(PublishedPeriodCoverage.plan(List.of(Q1_2026), coverage)).hasSize(1);
    }

    @Test
    void noPublishedPeriodIsNoPlan() {
        assertThat(PublishedPeriodCoverage.plan(List.of(), Set.of(YearMonth.of(2026, 1))))
                .isEmpty();
    }

    @Test
    void aPeriodListedTwiceIsRefusedNotRunTwiceNorMergedSilently() {
        List<Quadrimestre> published = List.of(Q1_2026, Q3_2025, Q1_2026);
        Set<YearMonth> coverage = Set.of();

        assertThatThrownBy(() -> PublishedPeriodCoverage.plan(published, coverage))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("2026-Q1");
    }

    @Test
    void theMonthsHeldAreThoseOfTheRegisteredMunicipalityWithAtLeastOneAtendimento() {
        List<SourceCoverageCheck.PeriodCount> counts = List.of(
                new SourceCoverageCheck.PeriodCount("3541307", "2026-01", 12),
                new SourceCoverageCheck.PeriodCount("3541307", "2026-02", 0),
                new SourceCoverageCheck.PeriodCount("3541307", "2026-03", 1),
                new SourceCoverageCheck.PeriodCount("3550308", "2026-04", 500),
                new SourceCoverageCheck.PeriodCount(null, "2026-05", 500),
                new SourceCoverageCheck.PeriodCount(" 3541307 ", "2025-12", 3));

        assertThat(PublishedPeriodCoverage.heldMonths("3541307", counts))
                .containsExactlyInAnyOrder(YearMonth.of(2026, 1), YearMonth.of(2026, 3), YearMonth.of(2025, 12));
    }

    @Test
    void aMonthSplitAcrossRowsIsHeldWhenAnyOfThemHoldsSomething() {
        List<SourceCoverageCheck.PeriodCount> counts = List.of(
                new SourceCoverageCheck.PeriodCount("3541307", "2026-01", 0),
                new SourceCoverageCheck.PeriodCount("3541307", "2026-01", 4));

        assertThat(PublishedPeriodCoverage.heldMonths("3541307", counts)).containsExactly(YearMonth.of(2026, 1));
    }

    @Test
    void noCountsHoldNoMonthAndAMalformedPeriodIsNotGuessedAt() {
        List<SourceCoverageCheck.PeriodCount> malformed =
                List.of(new SourceCoverageCheck.PeriodCount("3541307", "janeiro", 4));

        assertThat(PublishedPeriodCoverage.heldMonths("3541307", List.of())).isEmpty();
        assertThatThrownBy(() -> PublishedPeriodCoverage.heldMonths("3541307", malformed))
                .isInstanceOf(DateTimeParseException.class);
    }

    @Test
    void thePlanOfTheHeldMonthsMissesTheMonthsThePecDoesNotHold() {
        List<SourceCoverageCheck.PeriodCount> counts = List.of(
                new SourceCoverageCheck.PeriodCount("3541307", "2026-01", 9),
                new SourceCoverageCheck.PeriodCount("3541307", "2026-02", 9),
                new SourceCoverageCheck.PeriodCount("3541307", "2026-03", 9));

        List<PeriodExecutionPlan> plan =
                PublishedPeriodCoverage.plan(List.of(Q1_2026), PublishedPeriodCoverage.heldMonths("3541307", counts));

        assertThat(plan).singleElement().satisfies(entry -> {
            assertThat(entry.decision()).isEqualTo(Decision.MISSING_LOCAL_MONTHS);
            assertThat(entry.missingMonths()).containsExactly(YearMonth.of(2026, 4));
        });
    }

    @Test
    void thePlanCannotBeChangedAfterwards() {
        List<PeriodExecutionPlan> plan = PublishedPeriodCoverage.plan(List.of(Q1_2026), Set.of());
        PeriodExecutionPlan entry = plan.getFirst();
        List<YearMonth> missing = entry.missingMonths();

        assertThatThrownBy(() -> plan.add(entry)).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(missing::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void anEntryIsRunWithNoMissingMonthOrMissingWithSomeOfThePeriodInOrder() {
        List<YearMonth> january = List.of(YearMonth.of(2026, 1));
        List<YearMonth> outOfOrder = List.of(YearMonth.of(2026, 3), YearMonth.of(2026, 2));
        List<YearMonth> another = List.of(YearMonth.of(2026, 5));
        List<YearMonth> repeated = List.of(YearMonth.of(2026, 2), YearMonth.of(2026, 2));
        List<YearMonth> none = List.of();

        assertThatThrownBy(() -> new PeriodExecutionPlan(Q1_2026, Decision.RUN, january))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PeriodExecutionPlan(Q1_2026, Decision.MISSING_LOCAL_MONTHS, none))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PeriodExecutionPlan(Q1_2026, Decision.MISSING_LOCAL_MONTHS, outOfOrder))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PeriodExecutionPlan(Q1_2026, Decision.MISSING_LOCAL_MONTHS, another))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PeriodExecutionPlan(Q1_2026, Decision.MISSING_LOCAL_MONTHS, repeated))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
