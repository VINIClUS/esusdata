package esusdata.indicator.pack.c2;

import static esusdata.indicator.model.CanonicalFixtures.IBGE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalCondition;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.RuleOutcome;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regressions for the adversarial review of the C2 rule (fidelity to the ficha and refusals). */
class C2PackReviewTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);
    private static final EvaluationContext CONTEXT = EvaluationContext.endOfMonth(IBGE, MARCH);
    private static final LocalDate BIRTH = LocalDate.of(2024, 3, 10);
    private static final String KEY = "k1";
    private static final String INE = "0000000001";
    private static final String NURSE = "223505";

    private static CanonicalDataset.Builder child() {
        return CanonicalDataset.builder()
                .add(CanonicalFixtures.person(KEY, BIRTH, "FEMININO"))
                .add(CanonicalFixtures.registration(KEY, BIRTH.plusDays(5), "1234567", INE));
    }

    private static EvidenceItem practice(RuleOutcome outcome, String component) {
        return outcome.evidence().stream()
                .filter(e -> component.equals(e.component()) && e.decision() != EvidenceDecision.SUPPORTING_EVENT)
                .findFirst()
                .orElseThrow();
    }

    private static CanonicalCareEvent encounter(
            LocalDate date, String cbo, Boolean remote, String form, String location) {
        return new CanonicalCareEvent(
                CanonicalFixtures.ref("tb_fat_atendimento_individual"),
                IBGE,
                KEY,
                date.toString(),
                form,
                cbo,
                "1234567",
                INE,
                null,
                location,
                remote,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static CanonicalProcedureEvent mip(LocalDate date, String code) {
        return new CanonicalProcedureEvent(
                CanonicalFixtures.ref("tb_fat_proced_atend_proced"),
                IBGE,
                KEY,
                date.toString(),
                code,
                "PERFORMED",
                NURSE,
                null,
                null,
                "MIP");
    }

    @Test
    void a_mipNoMesmoDiaDeConsultaRemotaContinuaAmbiguo() {
        LocalDate day = BIRTH.plusDays(10);
        RuleOutcome outcome = C2Pack.compute(
                child().add(encounter(day, NURSE, true, "INDIVIDUAL", null))
                        .add(mip(day, C2Codes.CHILD_DEVELOPMENT_SIGTAP))
                        .build(),
                CONTEXT);
        assertThat(practice(outcome, "A").reasonCode()).isEqualTo("AMBIGUIDADE:AMB-C2-06");
    }

    @Test
    void a_registroDeOutroModeloDomiciliarNaoContaEMiaiNoDomicilioEAmbiguo() {
        LocalDate day = BIRTH.plusDays(10);
        RuleOutcome otherModel = C2Pack.compute(
                child().add(encounter(day, NURSE, false, "HOME", null)).build(), CONTEXT);
        assertThat(practice(otherModel, "A").decision()).isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
        assertThat(practice(otherModel, "A").reasonCode()).isEqualTo(PracticeOutcome.NOT_MET);

        RuleOutcome miaiAtHome = C2Pack.compute(
                child().add(encounter(day, NURSE, false, "INDIVIDUAL", "4")).build(), CONTEXT);
        assertThat(practice(miaiAtHome, "A").reasonCode()).isEqualTo("AMBIGUIDADE:AMB-C2-04");
    }

    @Test
    void met32_cboComESemHifenEAMesmaConsulta() {
        CanonicalDataset.Builder data = child();
        for (int i = 0; i < 8; i++) {
            data.add(encounter(BIRTH.plusMonths(i + 1L), NURSE, false, "INDIVIDUAL", null));
        }
        LocalDate ninth = BIRTH.plusMonths(10);
        data.add(encounter(ninth, "225142", false, "INDIVIDUAL", null))
                .add(encounter(ninth, "2251-42", false, "INDIVIDUAL", null));
        RuleOutcome outcome = C2Pack.compute(data.build(), CONTEXT);
        assertThat(practice(outcome, "B").decision()).isEqualTo(EvidenceDecision.PRACTICE_MET);
    }

    @Test
    void met32_mesmaMedidaEmDoisModelosEUmParSo() {
        CanonicalDataset.Builder data = child();
        for (int i = 0; i < 5; i++) {
            LocalDate day = BIRTH.plusMonths(i + 1L);
            data.add(CanonicalFixtures.encounterWithMeasures(KEY, day, NURSE, "7.20", "65", null, null))
                    .add(CanonicalFixtures.measurement(KEY, day, "7.2", "65.0", "MIP"));
        }
        RuleOutcome outcome = C2Pack.compute(data.build(), CONTEXT);
        assertThat(practice(outcome, "C").reasonCode()).isEqualTo(PracticeOutcome.NOT_MET);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void municipio_registroDeTipoNaoLidoDeOutroMunicipioERecusado() {
        CanonicalCondition foreign = new CanonicalCondition(
                CanonicalFixtures.ref("tb_fat_atd_ind_problemas"),
                "3550308",
                KEY,
                "CIAP2",
                "A98",
                BIRTH.plusDays(3).toString(),
                "ATIVO",
                null,
                "PROFESSIONAL");
        CanonicalDataset data = child().add(foreign).build();
        assertThatThrownBy(() -> C2Pack.compute(data, CONTEXT)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void extratoSemCapacidadeObrigatoriaNuncaViraZero() {
        CanonicalDataset.Builder data = child();
        DateWindow window = DateWindow.lastCivilMonths(MARCH, 26);
        for (String capability : Capabilities.ALL) {
            if (!Capabilities.IMMUNIZATION_HISTORY.equals(capability)) {
                data.window(capability, window);
            }
        }
        RuleOutcome outcome = new C2Pack().evaluate(data.build(), CONTEXT);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.UNSUPPORTED_SOURCE);
        assertThat(outcome.result().numerator()).isNull();
        assertThat(outcome.result().limitations()).anyMatch(l -> l.contains(Capabilities.IMMUNIZATION_HISTORY));

        for (String capability : Capabilities.ALL) {
            data.window(capability, window);
        }
        assertThat(new C2Pack().evaluate(data.build(), CONTEXT).result().status())
                .isEqualTo(IndicatorStatus.BLOCKED);
    }

    @Test
    void item24b_equipeDeTipoConhecidoForaDe70e76SaiDaCoorte() {
        CanonicalTeam other =
                new CanonicalTeam(CanonicalFixtures.ref("cnes"), IBGE, INE, "1234567", "71", "2026-01-01");
        RuleOutcome outcome = C2Pack.compute(child().add(other).build(), CONTEXT);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
        assertThat(outcome.evidence())
                .singleElement()
                .satisfies(e -> assertThat(e.reasonCode()).isEqualTo(C2Cohort.TEAM_TYPE_NOT_CONSIDERED));
    }

    @Test
    void item24b_tipoObservadoDepoisDoCorteNaoIsentaD() {
        CanonicalTeam later =
                new CanonicalTeam(CanonicalFixtures.ref("cnes"), IBGE, INE, "1234567", "76", "2026-04-02");
        RuleOutcome outcome = C2Pack.compute(child().add(later).build(), CONTEXT);
        assertThat(practice(outcome, "D").decision()).isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
    }

    @Test
    void item15_pessoaRepetidaMantemOObito() {
        CanonicalPerson dead = new CanonicalPerson(
                CanonicalFixtures.ref("tb_fat_cad_individual"),
                IBGE,
                KEY,
                BIRTH.toString(),
                "FEMININO",
                null,
                "2026-01-10");
        RuleOutcome outcome = C2Pack.compute(child().add(dead).build(), CONTEXT);
        assertThat(outcome.evidence())
                .singleElement()
                .satisfies(e -> assertThat(e.reasonCode()).isEqualTo(C2Cohort.DEATH));
    }

    @Test
    void e_codigoSemZeroAEsquerdaEAHepatiteB() {
        CanonicalDataset.Builder data = child();
        for (int i = 0; i < 3; i++) {
            LocalDate day = BIRTH.plusDays(60L * (i + 1));
            data.add(dose(day, "9")).add(dose(day, "46")).add(dose(day, "17")).add(dose(day, "22"));
        }
        data.add(dose(BIRTH.plusMonths(12), "24"))
                .add(dose(BIRTH.plusMonths(15), "24"))
                .add(dose(BIRTH.plusDays(60), "26"))
                .add(dose(BIRTH.plusDays(120), "26"));
        RuleOutcome outcome = C2Pack.compute(data.build(), CONTEXT);
        assertThat(practice(outcome, "E").decision()).isEqualTo(EvidenceDecision.PRACTICE_MET);
    }

    private static CanonicalImmunization dose(LocalDate day, String code) {
        return CanonicalFixtures.dose(KEY, day, code, null);
    }
}
