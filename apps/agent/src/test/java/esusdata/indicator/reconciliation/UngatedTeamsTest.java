package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalModality;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.SourceRef;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.pack.c1.C1Pack;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class UngatedTeamsTest {

    private static final String MUNICIPALITY = "9999999";
    private static final YearMonth MONTH = YearMonth.of(2026, 5);

    private static CanonicalEncounter encounter(String id, String ine, CanonicalModality modality) {
        return new CanonicalEncounter(
                new SourceRef("invented", "encounter", id),
                MUNICIPALITY,
                "2026-05-10",
                modality,
                "0000001",
                ine,
                "225125");
    }

    @Test
    void c1IsComputedPerTeamWithoutTheGate() {
        IndicatorRule c1 = new C1Pack();
        CanonicalDataset data = CanonicalDataset.ofEncounters(
                C1Pack.CAPABILITY,
                new DateWindow(MONTH.atDay(1), MONTH.plusMonths(1).atDay(1)),
                List.of(
                        encounter("1", "0000000011", CanonicalModality.PROGRAMADO),
                        encounter("2", "0000000011", CanonicalModality.ESPONTANEO),
                        encounter("3", "0000000012", CanonicalModality.ESPONTANEO),
                        encounter("4", null, CanonicalModality.ESPONTANEO)));
        EvaluationContext context = EvaluationContext.endOfMonth(MUNICIPALITY, MONTH);

        // what evaluate() gives on main today: the value is hidden
        assertThat(c1.evaluate(data, context).teams().getFirst().result().status())
                .isEqualTo(IndicatorStatus.BLOCKED);

        List<TeamResult> teams = UngatedTeams.of(c1, data, context);

        assertThat(teams).extracting(TeamResult::ine).containsExactly("0000000011", "0000000012");
        assertThat(teams.getFirst().result().status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(teams.getFirst().result().numerator()).isNotNull();
    }
}
