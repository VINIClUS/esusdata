package esusdata.indicator.model;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.TestGates;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/** §4.4: an unreleased pack keeps its exact counts but never shows a value or a band. */
class RuleOutcomesTest {

    private static PackDescriptor descriptor(List<String> standing) {
        return new PackDescriptor(
                "c9-teste",
                "c9-teste@0.1.0",
                "qualidade-esf-eap-2026-06",
                "QUALIDADE_ESF_EAP",
                "C9",
                "Teste",
                ValueKind.SCORE,
                "percentual",
                "PESSOAS",
                "c9-exact-score@1",
                List.of("citizen"),
                List.of(ComponentSpec.practice("A", "A", 100, "12 meses")),
                standing,
                MonthlyEligibility.ALL_MONTHS,
                BudgetHint.engineeringDefault(),
                List.of(),
                List.of());
    }

    private static IndicatorResult computed() {
        ResultComponent a =
                ResultComponent.of(ComponentSpec.practice("A", "A", 100, "12 meses"), BigInteger.ONE, BigInteger.TWO);
        return new IndicatorResult(
                IndicatorStatus.COMPUTED,
                "50.0000",
                BigInteger.valueOf(100),
                BigInteger.TWO,
                "PESSOAS",
                Classification.SUFICIENTE,
                "2026-03",
                "c9-teste@0.1.0",
                "2026-03-31",
                "3541307",
                List.of("limitação própria"),
                "c9-exact-score@1",
                ValueKind.SCORE,
                ExactRatio.of(50, 1),
                List.of(a),
                true);
    }

    @Test
    void anUnreleasedPackBlocksTheValueAndKeepsTheCounts() {
        IndicatorResult gated = RuleOutcomes.gate(pending(), computed());
        assertThat(gated.status()).isEqualTo(IndicatorStatus.BLOCKED);
        assertThat(gated.valueText()).isNull();
        assertThat(gated.valueExact()).isNull();
        assertThat(gated.classification()).isNull();
        assertThat(gated.numerator()).isEqualTo(BigInteger.valueOf(100));
        assertThat(gated.components()).hasSize(1);
        assertThat(gated.limitations()).contains("limitação própria").hasSize(5);
    }

    @Test
    void aReleasedPackPublishesAsComputed() {
        IndicatorResult result = computed();
        assertThat(RuleOutcomes.gate(passed(), result)).isSameAs(result);
    }

    @Test
    void aBlockingStandingLimitationFailsPortaoBEvenWithEveryRegisteredGatePassed() {
        PackDescriptor withLimitation = descriptor(List.of("pendência"));
        GateCheck registered = new GateCheck(GateCheck.State.PASSED, "x@1", "2026-10-06", List.of(), null);
        GateStatus status = GateStatus.pending(withLimitation)
                .withEvaluated(
                        GateChecks.calculationModel(withLimitation, LocalDate.of(2026, 10, 6)),
                        GateChecks.adapter(List.of(), null));
        assertThat(status.check(GateId.B).state()).isEqualTo(GateCheck.State.FAILED);
        assertThat(RuleOutcomes.gate(status, computed()).status()).isEqualTo(IndicatorStatus.BLOCKED);
        assertThat(registered.isPassed()).isTrue();
    }

    private static GateStatus pending() {
        return GateStatus.pending(descriptor(List.of()));
    }

    private static GateStatus passed() {
        return TestGates.allPassed(descriptor(List.of()));
    }

    @Test
    void aResultThatWasNotComputedKeepsItsOwnStatus() {
        IndicatorResult noDenominator = new IndicatorResult(
                IndicatorStatus.NO_DENOMINATOR,
                null,
                BigInteger.ZERO,
                BigInteger.ZERO,
                "PESSOAS",
                null,
                "2026-03",
                "c9-teste@0.1.0",
                "2026-03-31",
                "3541307",
                List.of(),
                "c9-exact-score@1");
        IndicatorResult gated = RuleOutcomes.gate(pending(), noDenominator);
        assertThat(gated.status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
        assertThat(gated.limitations()).hasSize(4);
    }

    @Test
    void gatingAnOutcomeGatesEveryTeam() {
        RuleOutcome outcome =
                new RuleOutcome(computed(), List.of(new TeamResult("0000000001", null, computed())), List.of());
        RuleOutcome gated = RuleOutcomes.gate(pending(), outcome);
        assertThat(gated.teams())
                .singleElement()
                .satisfies(t -> assertThat(t.result().status()).isEqualTo(IndicatorStatus.BLOCKED));
    }

    @Test
    void aPendingPackSaysWhyAndNeverCounts() {
        RuleOutcome pending = RuleOutcomes.pending(
                descriptor(List.of("pendente")),
                pending(),
                EvaluationContext.endOfMonth("3541307", YearMonth.of(2026, 3)),
                "em implementação");
        assertThat(pending.result().status()).isEqualTo(IndicatorStatus.BLOCKED);
        assertThat(pending.result().numerator()).isNull();
        assertThat(pending.result().consolidationEligible()).isFalse();
        assertThat(pending.result().limitations()).startsWith("em implementação", "pendente");
        assertThat(pending.teams()).isEmpty();
    }
}
