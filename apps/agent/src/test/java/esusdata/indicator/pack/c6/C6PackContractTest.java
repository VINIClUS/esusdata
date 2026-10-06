package esusdata.indicator.pack.c6;

import static esusdata.indicator.pack.c6.C6Scenario.INE_A;
import static esusdata.indicator.pack.c6.C6Scenario.INE_B;
import static esusdata.indicator.pack.c6.C6Scenario.OTHER_IBGE;
import static esusdata.indicator.pack.c6.C6Scenario.scenario;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.GateFixtures;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.ValueKind;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** C6 as an {@code IndicatorRule}: bands, release gate, municipal isolation, requirements, descriptor. */
class C6PackContractTest {

    private static final long MILLION = 1_000_000;
    private static final LocalDate PRACTICES_FROM = LocalDate.of(2025, 4, 1);
    private static final LocalDate PRACTICES_UNTIL = LocalDate.of(2026, 4, 1);

    private final C6Pack pack = new C6Pack();

    // ---- ENG-25 / T-C6-22: exact band edges ----------------------------------------------------

    @Test
    void eng25_bandEdgesAreExactAtOneMillionth() {
        assertBand(ExactRatio.of(25, 1), Classification.REGULAR);
        assertBand(plusMillionth(25), Classification.SUFICIENTE);
        assertBand(ExactRatio.of(50, 1), Classification.SUFICIENTE);
        assertBand(plusMillionth(50), Classification.BOM);
        assertBand(minusMillionth(75), Classification.BOM);
        assertBand(ExactRatio.of(75, 1), Classification.BOM);
        assertBand(plusMillionth(75), Classification.OTIMO);
        assertBand(ExactRatio.of(100, 1), Classification.OTIMO);
        assertBand(ExactRatio.zero(), Classification.REGULAR);
    }

    @Test
    void tC6_22_exactResultsAreBandedBeforeAnyRounding() {
        assertBand(ExactRatio.of(75, 1), Classification.BOM);
        assertBand(ExactRatio.of(750_001, 10_000), Classification.OTIMO);
        assertBand(ExactRatio.of(500_001, 10_000), Classification.BOM);
        assertBand(ExactRatio.of(25, 1), Classification.REGULAR);
    }

    // ---- release gate ------------------------------------------------------------------------------

    @Test
    void gate_evaluateBlocksTheValueButKeepsCountsComponentsAndTeams() {
        C6Scenario twoTeams =
                scenario().elder("a", INE_A).allPractices("a").elder("b", INE_B).practiceA("b");
        RuleOutcome computed = twoTeams.compute();
        RuleOutcome gated = twoTeams.evaluate();

        assertThat(computed.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertBlocked(gated.result(), computed.result());
        assertThat(gated.teams()).hasSize(2);
        for (TeamResult team : gated.teams()) {
            TeamResult ungated = C6Scenario.teamOf(computed, team.ine());
            assertThat(team.cnes()).isEqualTo(ungated.cnes());
            assertBlocked(team.result(), ungated.result());
        }
        assertThat(gated.evidence()).isEqualTo(computed.evidence());
    }

    @Test
    void gate_noDenominatorStaysNoDenominatorWithTheGateReasons() {
        IndicatorResult result = scenario().evaluate().result();

        assertThat(result.status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
        assertThat(result.valueText()).isNull();
        assertThat(result.limitations())
                .containsAll(GateFixtures.shipped(pack.descriptor()).incompleteReasons());
    }

    // ---- municipal isolation ---------------------------------------------------------------------

    @Test
    void municipality_aPersonOfAnotherMunicipalityIsRejected() {
        CanonicalPerson foreign = new CanonicalPerson(
                CanonicalFixtures.ref("tb_fat_cad_individual"),
                OTHER_IBGE,
                "estrangeira",
                C6Scenario.BORN_70.toString(),
                "FEMININO",
                null,
                null);
        C6Scenario data = scenario().elder("local").add(foreign);

        assertThatThrownBy(data::compute).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(data::evaluate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void municipality_aDoseOfAnotherMunicipalityIsRejected() {
        CanonicalImmunization foreign = new CanonicalImmunization(
                CanonicalFixtures.ref("tb_fat_vacinacao_vacina"),
                OTHER_IBGE,
                "local",
                "2025-05-10",
                C6Scenario.FLU_TRIVALENTE,
                "1",
                null,
                false,
                null,
                null,
                null);
        C6Scenario data = scenario().elder("local").add(foreign);

        assertThatThrownBy(data::compute).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- requirements and descriptor ---------------------------------------------------------------

    @Test
    void requirements_readSevenPartsWithThe12MonthWindowAndTheCodeLists() {
        DataRequirements requirements = pack.requirements(YearMonth.of(2026, 3));

        assertThat(requirements.canonicalSchemaVersion()).isEqualTo(DataRequirements.V2);
        assertThat(requirements.parts())
                .extracting(PartRequirement::capability)
                .containsExactlyInAnyOrder(
                        Capabilities.CITIZEN,
                        Capabilities.INDIVIDUAL_REGISTRATION,
                        Capabilities.CARE_ENCOUNTER,
                        Capabilities.PROCEDURE_PERFORMED,
                        Capabilities.HOME_VISIT,
                        Capabilities.IMMUNIZATION_HISTORY,
                        Capabilities.MEASUREMENT_RECORD);
        for (String practice : List.of(
                Capabilities.CARE_ENCOUNTER,
                Capabilities.PROCEDURE_PERFORMED,
                Capabilities.HOME_VISIT,
                Capabilities.IMMUNIZATION_HISTORY,
                Capabilities.MEASUREMENT_RECORD)) {
            PartRequirement part = part(requirements, practice);
            assertThat(part.periodStart()).as(practice).isEqualTo(PRACTICES_FROM);
            assertThat(part.periodEndExclusive()).as(practice).isEqualTo(PRACTICES_UNTIL);
        }
        assertThat(requirements.parts())
                .allSatisfy(p -> assertThat(p.dateParams().get(PartRequirement.BIRTH_DATE_TO))
                        .as(p.capability())
                        .isEqualTo(LocalDate.of(1966, 3, 31)));
        assertThat(part(requirements, Capabilities.PROCEDURE_PERFORMED)
                        .arrayParams()
                        .get(Capabilities.PROCEDURE_CODES))
                .containsExactlyInAnyOrder("0101040024", "0101040083", "0101040075");
        assertThat(part(requirements, Capabilities.IMMUNIZATION_HISTORY)
                        .arrayParams()
                        .get(Capabilities.IMMUNOBIOLOGICAL_CODES))
                .containsExactlyInAnyOrder("33", "77");
    }

    @Test
    void descriptor_declaresFourPracticesOf25PointsAndNoPendingRuleLimitation() {
        assertThat(pack.descriptor().components())
                .extracting(ComponentSpec::code)
                .containsExactly("A", "B", "C", "D");
        assertThat(pack.descriptor().components()).allMatch(c -> c.weight().equals(BigInteger.valueOf(25)));
        assertThat(pack.descriptor().valueKind()).isEqualTo(ValueKind.SCORE);
        assertThat(pack.descriptor().denominatorKind()).isEqualTo("PESSOAS_IDOSAS_VINCULADAS");
        assertThat(pack.descriptor().standingLimitations()).noneMatch(l -> l.contains("Regra em implementação"));
        assertThat(GateFixtures.shipped(pack.descriptor()).isComplete()).isFalse();
    }

    private void assertBand(ExactRatio value, Classification expected) {
        assertThat(pack.classify(value))
                .as("%s/%s", value.numerator(), value.denominator())
                .isEqualTo(Optional.of(expected));
    }

    private void assertBlocked(IndicatorResult gated, IndicatorResult computed) {
        assertThat(gated.status()).isEqualTo(IndicatorStatus.BLOCKED);
        assertThat(gated.valueText()).isNull();
        assertThat(gated.valueExact()).isNull();
        assertThat(gated.classification()).isNull();
        assertThat(gated.numerator()).isEqualTo(computed.numerator());
        assertThat(gated.denominator()).isEqualTo(computed.denominator());
        assertThat(gated.components()).isEqualTo(computed.components());
        assertThat(gated.limitations())
                .containsAll(GateFixtures.shipped(pack.descriptor()).incompleteReasons());
    }

    private static PartRequirement part(DataRequirements requirements, String capability) {
        return requirements.parts().stream()
                .filter(p -> p.capability().equals(capability))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no part " + capability));
    }

    private static ExactRatio plusMillionth(long whole) {
        return ExactRatio.of(whole * MILLION + 1, MILLION);
    }

    private static ExactRatio minusMillionth(long whole) {
        return ExactRatio.of(whole * MILLION - 1, MILLION);
    }
}
