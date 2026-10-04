package esusdata.indicator.pack.c3;

import static esusdata.indicator.pack.c3.C3Fixtures.NOVEMBER;
import static esusdata.indicator.pack.c3.C3Fixtures.context;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.RuleOutcome;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * §1.6: a capability the rule asked for that was not read, or was read for a shorter window, makes
 * the result {@code UNSUPPORTED_SOURCE} — no value and no counts, never zeros.
 */
class C3SourceCoverageTest {

    private static final DateWindow CARE = DateWindow.lastCivilMonths(NOVEMBER, 13);

    private static CanonicalDataset.Builder allButImmunization() {
        return CanonicalDataset.builder()
                .window(Capabilities.CITIZEN, CARE)
                .window(Capabilities.INDIVIDUAL_REGISTRATION, DateWindow.lastCivilMonths(NOVEMBER, 24))
                .window(Capabilities.CONDITION_LIST, CARE)
                .window(Capabilities.CARE_ENCOUNTER, CARE)
                .window(Capabilities.DENTAL_ENCOUNTER, CARE)
                .window(Capabilities.PROCEDURE_PERFORMED, CARE)
                .window(Capabilities.EXAM_REQUEST_EVALUATION, CARE)
                .window(Capabilities.HOME_VISIT, CARE)
                .window(Capabilities.MEASUREMENT_RECORD, CARE);
    }

    @Test
    void aCapabilityNotReadIsUnsupportedSourceWithoutCounts() {
        RuleOutcome outcome = new C3Pack().evaluate(allButImmunization().build(), context(NOVEMBER));

        assertUnsupported(outcome, "immunization_history");
    }

    @Test
    void aCapabilityReadForAShorterWindowIsUnsupportedSource() {
        CanonicalDataset data = allButImmunization()
                .window(Capabilities.IMMUNIZATION_HISTORY, DateWindow.lastCivilMonths(NOVEMBER, 12))
                .build();

        RuleOutcome outcome = new C3Pack().evaluate(data, context(NOVEMBER));

        assertUnsupported(outcome, "immunization_history [2024-11-01, 2025-12-01)");
    }

    @Test
    void everyPartReadForItsWindowIsComputed() {
        RuleOutcome outcome = C3Fixtures.computeNovember(new C3Pack(), List.of());

        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
    }

    private static void assertUnsupported(RuleOutcome outcome, String gap) {
        IndicatorResult result = outcome.result();
        assertThat(result.status()).isEqualTo(IndicatorStatus.UNSUPPORTED_SOURCE);
        assertThat(result.valueText()).isNull();
        assertThat(result.valueExact()).isNull();
        assertThat(result.numerator()).isNull();
        assertThat(result.denominator()).isNull();
        assertThat(result.components()).isEmpty();
        assertThat(result.limitations()).anySatisfy(l -> assertThat(l).contains(gap));
        assertThat(outcome.teams()).isEmpty();
        assertThat(outcome.evidence()).isEmpty();
    }
}
