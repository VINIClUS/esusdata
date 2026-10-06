package esusdata.indicator.pack.c6;

import static esusdata.indicator.pack.c6.C6Scenario.BORN_70;
import static esusdata.indicator.pack.c6.C6Scenario.CBO_ACS;
import static esusdata.indicator.pack.c6.C6Scenario.CBO_ENFERMEIRO;
import static esusdata.indicator.pack.c6.C6Scenario.CBO_MEDICO;
import static esusdata.indicator.pack.c6.C6Scenario.COMPETENCIA;
import static esusdata.indicator.pack.c6.C6Scenario.IBGE;
import static esusdata.indicator.pack.c6.C6Scenario.INE_A;
import static esusdata.indicator.pack.c6.C6Scenario.LINKED_ON;
import static esusdata.indicator.pack.c6.C6Scenario.VISIT_REASON;
import static esusdata.indicator.pack.c6.C6Scenario.context;
import static esusdata.indicator.pack.c6.C6Scenario.exclusionReason;
import static esusdata.indicator.pack.c6.C6Scenario.homeVisit;
import static esusdata.indicator.pack.c6.C6Scenario.met;
import static esusdata.indicator.pack.c6.C6Scenario.practiceRow;
import static esusdata.indicator.pack.c6.C6Scenario.registration;
import static esusdata.indicator.pack.c6.C6Scenario.scenario;
import static esusdata.indicator.pack.c6.C6Scenario.subjectRow;
import static esusdata.indicator.pack.c6.C6Scenario.team;
import static esusdata.indicator.pack.c6.C6Scenario.teamOf;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalMeasurement;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.RuleOutcome;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Cases raised by the integration review of C6: each one failed before its fix. */
class C6PackIntegrationReviewTest {

    private static final String P = "p";
    private static final LocalDate FEB_10 = LocalDate.of(2026, 2, 10);

    // ---- null ≠ zero: a capability not read is never a zero (BLOQUEANTE 1) --------------------------------

    @Test
    void capabilityNotReadIsUnsupportedSourceNeverZero() {
        RuleOutcome outcome =
                C6Pack.compute(withWindows(Capabilities.IMMUNIZATION_HISTORY, null), context(COMPETENCIA));

        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.UNSUPPORTED_SOURCE);
        assertThat(outcome.result().numerator()).isNull();
        assertThat(outcome.result().denominator()).isNull();
        assertThat(outcome.result().components()).isEmpty();
        assertThat(outcome.result().limitations()).anyMatch(l -> l.contains(Capabilities.IMMUNIZATION_HISTORY));
        assertThat(outcome.teams()).isEmpty();
        assertThat(outcome.evidence()).isEmpty();
    }

    @Test
    void registrationReadForAShorterWindowIsUnsupportedSource() {
        DateWindow twelve = DateWindow.lastCivilMonths(COMPETENCIA, 12);
        RuleOutcome outcome =
                C6Pack.compute(withWindows(Capabilities.INDIVIDUAL_REGISTRATION, twelve), context(COMPETENCIA));

        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.UNSUPPORTED_SOURCE);
        assertThat(outcome.result().limitations()).anyMatch(l -> l.contains(Capabilities.INDIVIDUAL_REGISTRATION));
    }

    @Test
    void everyPartReadWithItsWindowComputes() {
        RuleOutcome outcome = C6Pack.compute(withWindows(null, null), context(COMPETENCIA));

        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(outcome.result().valueExact()).isEqualByComparingTo(ExactRatio.of(25, 1));
    }

    // ---- link and team (IMPORTANTES 2, 3, 5, 6; MENOR 8) ----------------------------------------------------

    @Test
    void personOfATeamTypeOutsideSeventyAndSeventySixIsExcludedWithoutTeamResult() {
        RuleOutcome outcome = scenario().team(INE_A, "73").elder(P).practiceA(P).compute();

        assertThat(subjectRow(outcome, P).decision()).isEqualTo(EvidenceDecision.EXCLUDED);
        assertThat(exclusionReason(outcome, P)).isEqualTo("EXCLUIDO_EQUIPE_FORA_DO_ESCOPO");
        assertThat(outcome.teams()).isEmpty();
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
    }

    @Test
    void laterObservationWithoutTypeDoesNotEraseAKnownEap() {
        RuleOutcome outcome = scenario()
                .add(team(INE_A, "76", "2025-01-01"))
                .add(team(INE_A, null, "2026-01-01"))
                .elder(P)
                .compute();

        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
    }

    @Test
    void undatedTypeIsIgnoredAndReported() {
        RuleOutcome outcome =
                scenario().add(team(INE_A, "76", null)).elder(P).practiceA(P).compute();

        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(outcome.result().limitations()).anyMatch(l -> l.startsWith("1 observação(ões) de tipo de equipe"));
    }

    @Test
    void twoTypesAtTheSameLatestInstantAreAmbiguousNotChosen() {
        RuleOutcome outcome = scenario()
                .add(team(INE_A, "70", "2026-01-01T10:00:00Z"))
                .add(team(INE_A, "76", "2026-01-01T10:00:00Z"))
                .elder(P)
                .practiceA(P)
                .compute();

        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
        assertThat(practiceRow(outcome, P, "C").decision()).isEqualTo(EvidenceDecision.PRACTICE_AMBIGUOUS);
        assertThat(practiceRow(outcome, P, "C").reasonCode()).isEqualTo("C_AMBIGUA_TIPO_EQUIPE_CONFLITANTE");
        assertThat(outcome.result().limitations()).anyMatch(l -> l.startsWith("Tipo de equipe divergente"));
    }

    @Test
    void changeOfTerritoryOnAnInactiveVersionIsTheFichasInterruption() {
        RuleOutcome outcome = scenario()
                .elder(P)
                .add(registration(P, LocalDate.of(2026, 2, 1), INE_A, false, true, true, "136"))
                .compute();

        assertThat(exclusionReason(outcome, P)).isEqualTo("INTERROMPIDO_MUDANCA_TERRITORIO");
    }

    @Test
    void unknownExitCodeIsExcludedWithItsOwnReasonNeverIgnored() {
        RuleOutcome outcome = scenario()
                .elder(P)
                .add(registration(P, LocalDate.of(2026, 2, 1), INE_A, false, false, false, "MUDANCA_TERRITORIO"))
                .compute();

        assertThat(exclusionReason(outcome, P)).isEqualTo("EXCLUIDO_SAIDA_CADASTRO_NAO_MAPEADA");
    }

    @Test
    void teamCnesIsNullWhenItsMembersDisagree() {
        RuleOutcome outcome = scenario()
                .elder(P)
                .person("q", BORN_70)
                .add(new CanonicalRegistration(
                        CanonicalFixtures.ref("tb_fat_cad_individual"),
                        IBGE,
                        "q",
                        LINKED_ON.toString(),
                        "9999999",
                        INE_A,
                        false,
                        false,
                        false,
                        null,
                        null,
                        null,
                        null))
                .compute();

        assertThat(teamOf(outcome, INE_A).cnes()).isNull();
    }

    // ---- sources of B (IMPORTANTE 4; MENORES 11, 13) --------------------------------------------------------

    @Test
    void visitWithoutReasonOrByANonCommunityAgentDoesNotProveB() {
        RuleOutcome outcome = scenario()
                .elder("sem-motivo")
                .add(homeVisit("sem-motivo", FEB_10, CBO_ACS, List.of(), "70.5", "165.0"))
                .elder("enfermeira")
                .add(homeVisit("enfermeira", FEB_10, CBO_ENFERMEIRO, List.of(VISIT_REASON), "70.5", "165.0"))
                .elder("acs")
                .add(homeVisit("acs", FEB_10, CBO_ACS, List.of(VISIT_REASON), "70.5", "165.0"))
                .compute();

        assertThat(met(outcome, "sem-motivo", "B")).isFalse();
        assertThat(met(outcome, "enfermeira", "B")).isFalse();
        assertThat(met(outcome, "acs", "B")).isTrue();
    }

    @Test
    void procedureFromMiaiCountsAndAnEvaluatedOneDoesNot() {
        RuleOutcome outcome = scenario()
                .elder("miai")
                .add(new CanonicalProcedureEvent(
                        CanonicalFixtures.ref("tb_fat_atd_ind_procedimentos"),
                        IBGE,
                        "miai",
                        FEB_10.toString(),
                        "0101040024",
                        "PERFORMED",
                        CBO_ENFERMEIRO,
                        null,
                        null,
                        "MIAI"))
                .elder("avaliado")
                .add(CanonicalFixtures.procedure("avaliado", FEB_10, "0101040024", "EVALUATED", CBO_ENFERMEIRO))
                .compute();

        assertThat(met(outcome, "miai", "B")).isTrue();
        assertThat(met(outcome, "avaliado", "B")).isFalse();
    }

    @Test
    void measurementOnlyFromMipOrMiacWithACboOfQuadro03() {
        RuleOutcome outcome = scenario()
                .elder("miac")
                .add(CanonicalFixtures.collectiveActivity("miac", FEB_10, "70", "165", CBO_ENFERMEIRO, "05", List.of()))
                .elder("miac-sem-cbo")
                .add(CanonicalFixtures.collectiveActivity("miac-sem-cbo", FEB_10, "70", "165", null, "05", List.of()))
                .elder("outra-origem")
                .add(new CanonicalMeasurement(
                        CanonicalFixtures.ref("x"),
                        IBGE,
                        "outra-origem",
                        FEB_10.toString(),
                        "70",
                        "165",
                        null,
                        null,
                        CBO_ENFERMEIRO,
                        "MIAO"))
                .compute();

        assertThat(met(outcome, "miac", "B")).isTrue();
        assertThat(met(outcome, "miac-sem-cbo", "B")).isFalse();
        assertThat(met(outcome, "outra-origem", "B")).isFalse();
    }

    // ---- calendar and bands (MENORES 9, 10, 11) -------------------------------------------------------------

    @Test
    void cutoffBeforeTheEndOfTheMonthEndsThePracticeWindow() {
        CanonicalDataset data = scenario()
                .elder("antes")
                .consult("antes", LocalDate.of(2026, 3, 15))
                .elder("depois")
                .consult("depois", LocalDate.of(2026, 3, 16))
                .build();

        RuleOutcome outcome = C6Pack.compute(data, new EvaluationContext(IBGE, COMPETENCIA, LocalDate.of(2026, 3, 15)));

        assertThat(met(outcome, "antes", "A")).isTrue();
        assertThat(met(outcome, "depois", "A")).isFalse();
    }

    @Test
    void requirementsReadTheRegistrationFor24MonthsAndBirthsFromOneHundredThirtyYears() {
        List<PartRequirement> parts = new C6Pack().requirements(COMPETENCIA).parts();
        PartRequirement registrations = parts.stream()
                .filter(p -> Capabilities.INDIVIDUAL_REGISTRATION.equals(p.capability()))
                .findFirst()
                .orElseThrow();

        assertThat(registrations.periodStart()).isEqualTo(LocalDate.of(2024, 4, 1));
        assertThat(registrations.periodEndExclusive()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(parts)
                .allMatch(
                        p -> p.dateParams().get(PartRequirement.BIRTH_DATE_FROM).equals(LocalDate.of(1896, 3, 1)));
    }

    @Test
    void eng27_bornOn29FebruaryTurns60OnMarchFirstButIsStillReadInFebruary() {
        YearMonth feb2100 = YearMonth.of(2100, 2);
        LocalDate born = LocalDate.of(2040, 2, 29);
        PartRequirement citizen = new C6Pack().requirements(feb2100).parts().get(0);
        RuleOutcome outcome = scenario().person(P, born).linked(P, INE_A).compute(feb2100);

        assertThat(citizen.dateParams().get(PartRequirement.BIRTH_DATE_TO)).isEqualTo(born);
        // The bind is a superset (it clamps), so the person is read; C6-D3 (NEXT_DAY) keeps them out of 28/02.
        assertThat(exclusionReason(outcome, P)).isEqualTo("EXCLUIDO_IDADE_MENOR_60");
        YearMonth mar2100 = YearMonth.of(2100, 3);
        RuleOutcome march = scenario().person(P, born).linked(P, INE_A).compute(mar2100);
        assertThat(subjectRow(march, P).decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
    }

    @Test
    void eng25_justBelowTheEdgesAndAboveOneHundred() {
        C6Pack pack = new C6Pack();
        BigInteger million = BigInteger.valueOf(1_000_000);

        assertThat(pack.classify(below(25, million))).contains(Classification.REGULAR);
        assertThat(pack.classify(below(50, million))).contains(Classification.SUFICIENTE);
        assertThat(pack.classify(ExactRatio.of(100_000_001, 1_000_000))).isEmpty();
    }

    private static ExactRatio below(long edge, BigInteger million) {
        return ExactRatio.of(BigInteger.valueOf(edge).multiply(million).subtract(BigInteger.ONE), million);
    }

    /** A person with practice A, every part read for its window except {@code missing} or with {@code window}. */
    private static CanonicalDataset withWindows(String changed, DateWindow window) {
        CanonicalDataset.Builder data = CanonicalDataset.builder();
        for (PartRequirement part : new C6Pack().requirements(COMPETENCIA).parts()) {
            DateWindow read = new DateWindow(part.periodStart(), part.periodEndExclusive());
            if (part.capability().equals(changed)) {
                if (window != null) {
                    data.window(part.capability(), window);
                }
            } else {
                data.window(part.capability(), read);
            }
        }
        data.add(CanonicalFixtures.person(P, BORN_70, "FEMININO"));
        data.add(registration(P, LINKED_ON, INE_A));
        data.add(CanonicalFixtures.encounter(P, LocalDate.of(2026, 1, 15), CBO_MEDICO, false));
        return data.build();
    }
}
