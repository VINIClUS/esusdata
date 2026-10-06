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
import static esusdata.indicator.pack.c6.C6Scenario.practiceRow;
import static esusdata.indicator.pack.c6.C6Scenario.scenario;
import static esusdata.indicator.pack.c6.C6Scenario.subjectRow;
import static esusdata.indicator.pack.c6.C6Scenario.teamState;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CanonicalRegistration;
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
    void met23_theTypeIsTheOneValidOnTheLastDayOfTheCompetenciaWhateverTheExtractOrder() {
        RuleOutcome laterEap = scenario()
                .elder("p")
                .add(teamState(INE_A, "76", "2026-03-01", null))
                .add(teamState(INE_A, "70", "2024-01-01", "2026-03-01"))
                .compute();
        RuleOutcome laterEsf = scenario()
                .elder("p")
                .add(teamState(INE_A, "70", "2026-03-01", null))
                .add(teamState(INE_A, "76", "2024-01-01", "2026-03-01"))
                .compute();

        assertThat(practiceRow(laterEap, "p", "C").reasonCode()).isEqualTo("PRATICA_CREDITADA_EAP76");
        assertThat(practiceRow(laterEsf, "p", "C").reasonCode()).isEqualTo("C_SEM_DUAS_VISITAS_30_DIAS");
    }

    @Test
    void c6d2_validToIsExclusiveAndAStateBeginningAfterTheLastDayIsNotYetTheTeamsType() {
        // the competência ends on 2026-03-31: a 76 state ending that day does not cover it...
        RuleOutcome endsOnTheLastDay = scenario()
                .elder("p")
                .add(teamState(INE_A, "76", "2024-01-01", "2026-03-31"))
                .add(teamState(INE_A, "70", "2026-03-31", null))
                .compute();
        // ...and one that begins the day after is not in force, so the earlier one stands
        RuleOutcome beginsAfter = scenario()
                .elder("p")
                .add(teamState(INE_A, "70", "2024-01-01", "2026-04-01"))
                .add(teamState(INE_A, "76", "2026-04-01", null))
                .compute();

        assertThat(practiceRow(endsOnTheLastDay, "p", "C").reasonCode()).isEqualTo("C_SEM_DUAS_VISITAS_30_DIAS");
        assertThat(practiceRow(beginsAfter, "p", "C").reasonCode()).isEqualTo("C_SEM_DUAS_VISITAS_30_DIAS");
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
