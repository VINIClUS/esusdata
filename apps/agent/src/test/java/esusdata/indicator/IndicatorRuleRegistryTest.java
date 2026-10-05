package esusdata.indicator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.BudgetHint;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.ValueKind;
import java.math.BigInteger;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Every compiled rule describes itself consistently (ADR 0030, §4.7, ENG-34). */
class IndicatorRuleRegistryTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);

    static Stream<IndicatorRule> rules() {
        return IndicatorRuleRegistry.all().stream();
    }

    @ParameterizedTest
    @MethodSource("rules")
    void describesItselfWithAVersionedIdAndNoExecutionBeforeTheGates(IndicatorRule rule) {
        PackDescriptor d = rule.descriptor();
        assertThat(d.ruleVersion()).startsWith(d.id() + "@");
        assertThat(d.packageId()).isEqualTo("qualidade-esf-eap-2026-06");
        assertThat(d.family()).isEqualTo("QUALIDADE_ESF_EAP");
        assertThat(d.executionEnabled()).isFalse();
        assertThat(d.blockedGates()).isNotEmpty();
        assertThat(d.methodologySources()).isNotEmpty();
        assertThat(d.requiredCapabilities()).isNotEmpty();
    }

    /** C1 reads one month through v1; the packs by practice read up to 36 months (BudgetHint). */
    @ParameterizedTest
    @MethodSource("rules")
    void packsByPracticeAskForTheBudgetMeasuredOnARealPec(IndicatorRule rule) {
        PackDescriptor d = rule.descriptor();
        if (d.valueKind() != ValueKind.PERCENTAGE) {
            assertThat(d.budget()).isEqualTo(BudgetHint.practicesPack());
        }
    }

    @ParameterizedTest
    @MethodSource("rules")
    void scoredPacksWeighTheirComponentsToOneHundred(IndicatorRule rule) {
        PackDescriptor d = rule.descriptor();
        if (d.valueKind() == ValueKind.PERCENTAGE) {
            assertThat(d.components()).isEmpty();
            return;
        }
        BigInteger total = d.components().stream().map(ComponentSpec::weight).reduce(BigInteger.ZERO, BigInteger::add);
        assertThat(total).isEqualTo(BigInteger.valueOf(100));
        assertThat(d.components().stream().map(ComponentSpec::code).distinct()).hasSameSizeAs(d.components());
    }

    @ParameterizedTest
    @MethodSource("rules")
    void readsOnlyTheCapabilitiesItDeclares(IndicatorRule rule) {
        DataRequirements requirements = rule.requirements(MARCH);
        List<String> read =
                requirements.parts().stream().map(PartRequirement::capability).toList();
        assertThat(new HashSet<>(rule.descriptor().requiredCapabilities())).isEqualTo(new HashSet<>(read));
        for (PartRequirement part : requirements.parts()) {
            assertThat(part.periodEndExclusive())
                    .isBeforeOrEqualTo(MARCH.plusMonths(1).atDay(1));
        }
    }

    @ParameterizedTest
    @MethodSource("rules")
    void neverReturnsAValueBeforeItsGates(IndicatorRule rule) {
        DataRequirements requirements = rule.requirements(MARCH);
        CanonicalDataset.Builder empty = CanonicalDataset.builder();
        for (PartRequirement part : requirements.parts()) {
            empty.window(
                    part.capability(),
                    new esusdata.indicator.model.DateWindow(part.periodStart(), part.periodEndExclusive()));
        }
        var outcome = rule.evaluate(empty.build(), EvaluationContext.endOfMonth("3541307", MARCH));
        assertThat(outcome.result().status()).isIn(IndicatorStatus.BLOCKED, IndicatorStatus.NO_DENOMINATOR);
        assertThat(outcome.result().valueText()).isNull();
        assertThat(outcome.result().classification()).isNull();
        assertThat(outcome.result().ruleVersion()).isEqualTo(rule.descriptor().ruleVersion());
    }

    @ParameterizedTest
    @MethodSource("rules")
    void classifiesOnItsOwnScale(IndicatorRule rule) {
        assertThat(rule.classify(ExactRatio.of(60, 1))).isPresent();
        if (rule.descriptor().valueKind() != ValueKind.PERCENTAGE) {
            assertThat(rule.classify(ExactRatio.of(76, 1))).contains(Classification.OTIMO);
            assertThat(rule.classify(ExactRatio.of(25, 1))).contains(Classification.REGULAR);
        }
    }

    @Test
    void requireRefusesUnknownPacksAndOtherVersions() {
        assertThat(IndicatorRuleRegistry.require("c1-mais-acesso", "c1-mais-acesso@0.2.0")
                        .descriptor()
                        .code())
                .isEqualTo("C1");
        assertThatThrownBy(() -> IndicatorRuleRegistry.require("c9-inexistente", "c9-inexistente@0.1.0"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> IndicatorRuleRegistry.require("c1-mais-acesso", "c1-mais-acesso@9.9.9"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(IndicatorRuleRegistry.all())
                .extracting(r -> r.descriptor().code())
                .containsExactly("C1", "C2", "C3", "C4", "C5", "C6", "C7");
    }
}
