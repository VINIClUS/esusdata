package esusdata.indicator.pack.c1;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalModality;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.SourceRef;
import esusdata.indicator.model.TeamResult;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * What C1 publishes with no gate approved, pinned from v0.1.9 (ADR 0032): every C1 result,
 * municipal and per team, is BLOCKED with its exact counts — including a team whose encounters all
 * fall outside the ficha's CBO list (a 0/0 that was BLOCKED, never NO_DENOMINATOR). The gate
 * reasons are the only thing S1 adds (Portão C, which C1 used to take as passed).
 */
class C1PublishedBaselineTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);
    private static final DateWindow MARCH_WINDOW = new DateWindow(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1));

    private static CanonicalEncounter encounter(int id, CanonicalModality modality, String ine, String cbo) {
        return new CanonicalEncounter(
                new SourceRef("pec-1", "tb_fat_atendimento_individual", String.valueOf(id)),
                "3541307",
                "2026-03-10",
                modality,
                "1234567",
                ine,
                cbo);
    }

    private static RuleOutcome published() {
        List<CanonicalEncounter> encounters = new ArrayList<>();
        encounters.add(encounter(1, CanonicalModality.PROGRAMADO, "0000000001", "225142"));
        encounters.add(encounter(2, CanonicalModality.ESPONTANEO, "0000000001", "225142"));
        encounters.add(encounter(3, CanonicalModality.PROGRAMADO, "0000000002", "225142"));
        encounters.add(encounter(4, CanonicalModality.UNMAPPED, "0000000002", "225142"));
        encounters.add(encounter(5, CanonicalModality.ESPONTANEO, "0000000003", "322205"));
        return PublishedC1.of(
                CanonicalDataset.ofEncounters(C1Pack.CAPABILITY, MARCH_WINDOW, encounters),
                EvaluationContext.endOfMonth("3541307", MARCH));
    }

    private static void assertBlocked(IndicatorResult result, long numerator, long denominator) {
        assertThat(result.status()).isEqualTo(IndicatorStatus.BLOCKED);
        assertThat(result.valueText()).isNull();
        assertThat(result.classification()).isNull();
        assertThat(result.numerator()).isEqualTo(BigInteger.valueOf(numerator));
        assertThat(result.denominator()).isEqualTo(BigInteger.valueOf(denominator));
        assertThat(result.limitations()).containsAll(C1Rule.standingLimitations());
        assertThat(result.limitations()).contains("Portão A (fonte e vigência) incompleto");
    }

    @Test
    void everyResultIsBlockedWithItsExactCounts() {
        RuleOutcome outcome = published();

        assertBlocked(outcome.result(), 2, 3);
        assertThat(outcome.teams())
                .extracting(TeamResult::ine)
                .containsExactly("0000000001", "0000000002", "0000000003");
        assertBlocked(outcome.teams().get(0).result(), 1, 2);
        assertBlocked(outcome.teams().get(1).result(), 1, 1);
        assertBlocked(outcome.teams().get(2).result(), 0, 0);
    }
}
