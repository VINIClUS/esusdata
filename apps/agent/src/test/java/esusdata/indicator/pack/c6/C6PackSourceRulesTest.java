package esusdata.indicator.pack.c6;

import static esusdata.indicator.pack.c6.C6Scenario.CBO_ACS;
import static esusdata.indicator.pack.c6.C6Scenario.CNES;
import static esusdata.indicator.pack.c6.C6Scenario.IBGE;
import static esusdata.indicator.pack.c6.C6Scenario.INE_A;
import static esusdata.indicator.pack.c6.C6Scenario.INE_B;
import static esusdata.indicator.pack.c6.C6Scenario.LINKED_ON;
import static esusdata.indicator.pack.c6.C6Scenario.exclusionReason;
import static esusdata.indicator.pack.c6.C6Scenario.homeVisit;
import static esusdata.indicator.pack.c6.C6Scenario.met;
import static esusdata.indicator.pack.c6.C6Scenario.scenario;
import static esusdata.indicator.pack.c6.C6Scenario.subjectRow;
import static esusdata.indicator.pack.c6.C6Scenario.team;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.RuleOutcome;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Which source records C6 accepts as proof, and how it reads link and team-type versions. */
class C6PackSourceRulesTest {

    private static final LocalDate DAY = LocalDate.of(2025, 11, 20);

    @Test
    void ambC6_07_anthropometryProcedureOnlyCountsFromMipOrMiai() {
        RuleOutcome outcome = scenario()
                .elder("mip")
                .add(CanonicalFixtures.procedure("mip", DAY, "0101040024", "PERFORMED", "223505"))
                .elder("miao")
                .add(new CanonicalProcedureEvent(
                        CanonicalFixtures.ref("tb_fat_atendimento_odonto"),
                        IBGE,
                        "miao",
                        DAY.toString(),
                        "0101040024",
                        "PERFORMED",
                        "223208",
                        null,
                        null,
                        "MIAO"))
                .compute();

        assertThat(met(outcome, "mip", "B")).isTrue();
        assertThat(met(outcome, "miao", "B")).isFalse();
    }

    @Test
    void ambC6_06_onlyIndividualCareEventsAreConsultsOrMeasurements() {
        RuleOutcome outcome = scenario()
                .elder("dental")
                .add(careEvent("dental", "DENTAL", "225142"))
                .elder("individual")
                .add(careEvent("individual", "INDIVIDUAL", "225142"))
                .compute();

        assertThat(met(outcome, "dental", "A")).isFalse();
        assertThat(met(outcome, "dental", "B")).isFalse();
        assertThat(met(outcome, "individual", "A")).isTrue();
        assertThat(met(outcome, "individual", "B")).isTrue();
    }

    @Test
    void practiceC_blankVisitReasonIsNotAFilledReason() {
        RuleOutcome outcome = scenario()
                .elder("p")
                .add(homeVisit("p", LocalDate.of(2025, 10, 1), CBO_ACS, List.of(" "), null, null))
                .add(homeVisit("p", LocalDate.of(2026, 1, 15), CBO_ACS, List.of(""), null, null))
                .compute();

        assertThat(met(outcome, "p", "C")).isFalse();
    }

    @Test
    void link_sameDayVersionsDifferingOnlyInNullVersusFalseDoNotConflict() {
        RuleOutcome outcome = scenario()
                .person("p", C6Scenario.BORN_70)
                .add(version("p", INE_A, null, null))
                .add(version("p", INE_A, false, false))
                .compute();

        assertThat(subjectRow(outcome, "p").reasonCode()).isEqualTo("ELEGIVEL_60_ANOS_VINCULADO");
    }

    @Test
    void link_conflictingVersionsShowNoTeamInTheEvidence() {
        RuleOutcome outcome = scenario()
                .person("p", C6Scenario.BORN_70)
                .add(version("p", INE_A, false, false))
                .add(version("p", INE_B, false, false))
                .compute();

        assertThat(exclusionReason(outcome, "p")).isEqualTo("EXCLUIDO_VINCULO_CONFLITANTE");
        assertThat(subjectRow(outcome, "p").ine()).isNull();
        assertThat(subjectRow(outcome, "p").cnes()).isNull();
    }

    @Test
    void met23_sameDayTeamObservationsAreOrderedByInstantNotByExtractOrder() {
        RuleOutcome laterEap = scenario()
                .elder("p")
                .add(team(INE_A, "76", "2026-03-01T15:00:00Z"))
                .add(team(INE_A, "70", "2026-03-01T08:00:00Z"))
                .compute();
        RuleOutcome laterEsf = scenario()
                .elder("p")
                .add(team(INE_A, "70", "2026-03-01T15:00:00Z"))
                .add(team(INE_A, "76", "2026-03-01T08:00:00Z"))
                .compute();

        assertThat(laterEap.result().status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
        assertThat(laterEsf.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void met23_malformedTeamObservationIsRefusedNotGuessed() {
        C6Scenario short1 = scenario().elder("p").add(team(INE_A, "76", "2026-03"));
        C6Scenario text = scenario().elder("p").add(team(INE_A, "76", "ontem à tarde"));

        assertThatThrownBy(short1::compute).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(text::compute).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void measurements_onlyPositiveDecimalsCount() {
        assertThat(C6Practices.positive("70.5")).isTrue();
        assertThat(C6Practices.positive(" 1 ")).isTrue();
        assertThat(C6Practices.positive("0")).isFalse();
        assertThat(C6Practices.positive("-3")).isFalse();
        assertThat(C6Practices.positive(" ")).isFalse();
        assertThat(C6Practices.positive(null)).isFalse();
        assertThatThrownBy(() -> C6Practices.positive("70,5")).isInstanceOf(IllegalArgumentException.class);
    }

    private static CanonicalCareEvent careEvent(String key, String form, String cbo) {
        return new CanonicalCareEvent(
                CanonicalFixtures.ref("tb_fat_atendimento_individual"),
                IBGE,
                key,
                DAY.toString(),
                form,
                cbo,
                CNES,
                INE_A,
                null,
                null,
                false,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "70.5",
                "165",
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static CanonicalRegistration version(String key, String ine, Boolean inactive, Boolean refused) {
        return new CanonicalRegistration(
                CanonicalFixtures.ref("tb_fat_cad_individual"),
                IBGE,
                key,
                LINKED_ON.toString(),
                CNES,
                ine,
                false,
                inactive,
                refused,
                " ",
                null,
                null,
                null);
    }
}
