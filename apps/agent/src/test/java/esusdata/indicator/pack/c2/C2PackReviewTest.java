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
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.SourceRef;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Regressions for the adversarial review of the C2 rule (fidelity to the ficha and refusals). */
class C2PackReviewTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);
    private static final EvaluationContext CONTEXT = EvaluationContext.endOfMonth(IBGE, MARCH);
    private static final LocalDate BIRTH = LocalDate.of(2024, 4, 10);
    private static final String KEY = "k1";
    private static final String INE = "0000000001";
    private static final String NURSE = "223505";

    /** A builder with every capability window the pack asks for, as a v2 extract carries them. */
    static CanonicalDataset.Builder extractWindows(YearMonth competencia) {
        CanonicalDataset.Builder builder = CanonicalDataset.builder();
        for (PartRequirement part : new C2Pack().requirements(competencia).parts()) {
            builder.window(part.capability(), new DateWindow(part.periodStart(), part.periodEndExclusive()));
        }
        return builder;
    }

    private static CanonicalDataset.Builder child() {
        return extractWindows(MARCH)
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
            LocalDate day = BIRTH.plusMonths(i + 2L);
            data.add(CanonicalFixtures.encounterWithMeasures(KEY, day, NURSE, "7.20", "65", null, null))
                    .add(CanonicalFixtures.measurement(KEY, day, "7.2", "65.0", "MIP"));
        }
        RuleOutcome outcome = C2Pack.compute(data.build(), CONTEXT);
        assertThat(practice(outcome, "C").reasonCode()).isEqualTo(PracticeOutcome.NOT_MET_WINDOW_OPEN);
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
    void extratoSemCapacidadeOuComJanelaCurtaNuncaViraZero() {
        CanonicalDataset.Builder data = CanonicalDataset.builder()
                .add(CanonicalFixtures.person(KEY, BIRTH, "FEMININO"))
                .add(CanonicalFixtures.registration(KEY, BIRTH.plusDays(5), "1234567", INE));
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

        data.window(Capabilities.IMMUNIZATION_HISTORY, DateWindow.lastCivilMonths(MARCH, 12));
        assertThat(new C2Pack().evaluate(data.build(), CONTEXT).result().status())
                .isEqualTo(IndicatorStatus.UNSUPPORTED_SOURCE);

        data.window(Capabilities.IMMUNIZATION_HISTORY, window);
        assertThat(new C2Pack().evaluate(data.build(), CONTEXT).result().status())
                .isNotEqualTo(IndicatorStatus.UNSUPPORTED_SOURCE);
    }

    @Test
    void extratoSemCapacidadeAindaRecusaOutroMunicipio() {
        CanonicalDataset foreign = CanonicalDataset.builder()
                .add(new CanonicalPerson(
                        CanonicalFixtures.ref("tb_fat_cad_individual"),
                        "3550308",
                        KEY,
                        BIRTH.toString(),
                        null,
                        null,
                        null))
                .build();
        assertThatThrownBy(() -> C2Pack.compute(foreign, CONTEXT)).isInstanceOf(IllegalArgumentException.class);
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

    // ---- Revisão da integração (sondas P1–P9 e itens 2, 9, 12, 15, 17, 20) ----

    private static CanonicalRegistration version(String id, LocalDate day, String exit, boolean inactive) {
        return new CanonicalRegistration(
                new SourceRef("pec", "tb_fat_cad_individual", id),
                IBGE,
                KEY,
                day.toString(),
                "1234567",
                INE,
                false,
                inactive,
                false,
                exit,
                null,
                null,
                null);
    }

    private static CanonicalProcedureEvent procedure(LocalDate date, String code, String cbo, String stage) {
        return new CanonicalProcedureEvent(
                CanonicalFixtures.ref("tb_fat_proced_atend_proced"),
                IBGE,
                KEY,
                date.toString(),
                code,
                stage,
                cbo,
                null,
                null,
                "MIP");
    }

    private static CanonicalCareEvent measured(LocalDate date, String cbo, String weight, String height) {
        return CanonicalFixtures.encounterWithMeasures(KEY, date, cbo, weight, height, null, null);
    }

    private static EvidenceItem person(RuleOutcome outcome) {
        return outcome.evidence().stream()
                .filter(e -> e.component() == null)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void p1_desempateDeVersoesNoMesmoDiaComparaOIdComoNumero() {
        LocalDate day = BIRTH.plusMonths(6);
        RuleOutcome outcome = C2Pack.compute(
                child().add(version("9", day, null, false))
                        .add(version("10", day, "136", false))
                        .build(),
                CONTEXT);
        assertThat(person(outcome).reasonCode()).isEqualTo(C2Cohort.TERRITORY_CHANGE);
    }

    @Test
    void item2_saidaNaVersaoInativaMaisRecenteInterrompe() {
        RuleOutcome outcome = C2Pack.compute(
                child().add(version("20", BIRTH.plusMonths(8), "136", true)).build(), CONTEXT);
        assertThat(person(outcome).reasonCode()).isEqualTo(C2Cohort.TERRITORY_CHANGE);
    }

    @Test
    void p2_p8_medidaZeroOuNaoNumericaNaoComprovaPesoNemAltura() {
        CanonicalDataset.Builder zeros = child();
        CanonicalDataset.Builder text = child();
        for (int i = 1; i <= 9; i++) {
            zeros.add(measured(BIRTH.plusDays(40L * i), "322205", "0", "0"));
            text.add(measured(BIRTH.plusDays(40L * i), "322205", "n/a", "?"));
        }
        assertThat(practice(C2Pack.compute(zeros.build(), CONTEXT), "C").decision())
                .isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
        assertThat(practice(C2Pack.compute(text.build(), CONTEXT), "C").decision())
                .isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
    }

    @Test
    void p3_p4_procedimentoDeConsultaPorCboForaDoQuadro02NaoCumpre() {
        RuleOutcome first = C2Pack.compute(
                child().add(procedure(BIRTH.plusDays(10), C2Codes.CHILD_DEVELOPMENT_SIGTAP, "515105", "PERFORMED"))
                        .build(),
                CONTEXT);
        assertThat(practice(first, "A").decision()).isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);

        CanonicalDataset.Builder nine = child();
        for (int i = 1; i <= 8; i++) {
            nine.add(encounter(BIRTH.plusDays(40L * i), "225142", false, "INDIVIDUAL", null));
        }
        nine.add(procedure(BIRTH.plusDays(400), C2Codes.TELECONSULT_SIGTAP, "515105", "PERFORMED"));
        assertThat(practice(C2Pack.compute(nine.build(), CONTEXT), "B").decision())
                .isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
    }

    @Test
    void p5_avaliacaoDoCrescimentoPorCboForaDoQuadro03NaoCumpre() {
        CanonicalDataset.Builder data = child();
        for (int i = 1; i <= 8; i++) {
            data.add(measured(BIRTH.plusDays(40L * i), NURSE, "7.0", "65.0"));
        }
        data.add(procedure(BIRTH.plusDays(400), C2Codes.GROWTH_EVALUATION, "251510", "PERFORMED"));
        assertThat(practice(C2Pack.compute(data.build(), CONTEXT), "C").decision())
                .isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
    }

    @Test
    void item15_procedimentoSoSolicitadoNaoComprova() {
        RuleOutcome outcome = C2Pack.compute(
                child().add(procedure(BIRTH.plusDays(10), C2Codes.CHILD_DEVELOPMENT_SIGTAP, NURSE, "REQUESTED"))
                        .build(),
                CONTEXT);
        assertThat(practice(outcome, "A").decision()).isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
    }

    @Test
    void p7_medidaDoMipSemCboNaoComprova() {
        CanonicalDataset.Builder data = child();
        for (int i = 1; i <= 9; i++) {
            data.add(CanonicalFixtures.measurement(KEY, BIRTH.plusDays(40L * i), "7.0", "65.0", "MIP"));
        }
        assertThat(practice(C2Pack.compute(data.build(), CONTEXT), "C").decision())
                .isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
    }

    @Test
    void p9_doseDeRotinaRegistradaDepoisDoCorteContaPelaAplicacao() {
        LocalDate birth = LocalDate.of(2024, 9, 15);
        CanonicalDataset.Builder data = extractWindows(MARCH)
                .add(CanonicalFixtures.person(KEY, birth, "FEMININO"))
                .add(CanonicalFixtures.registration(KEY, birth.plusDays(5), "1234567", INE));
        for (int days : new int[] {60, 120, 180}) {
            data.add(dose(birth.plusDays(days), "42")).add(dose(birth.plusDays(days), "22"));
        }
        data.add(dose(birth.plusDays(60), "26"))
                .add(dose(birth.plusDays(120), "26"))
                .add(dose(birth.plusMonths(12), "24"))
                .add(new CanonicalImmunization(
                        CanonicalFixtures.ref("tb_fat_vacinacao_vacina"),
                        IBGE,
                        KEY,
                        "2026-03-20",
                        "24",
                        null,
                        null,
                        false,
                        null,
                        null,
                        null,
                        "2026-04-02"));
        assertThat(practice(C2Pack.compute(data.build(), CONTEXT), "E").decision())
                .isEqualTo(EvidenceDecision.PRACTICE_MET);
    }

    @Test
    void item9_praticaNaoCumpridaComJanelaAbertaDizPrazoAberto() {
        RuleOutcome outcome = C2Pack.compute(child().build(), CONTEXT);
        assertThat(practice(outcome, "A").reasonCode()).isEqualTo(PracticeOutcome.NOT_MET);
        assertThat(practice(outcome, "D").reasonCode()).isEqualTo(PracticeOutcome.NOT_MET);
        for (String open : List.of("B", "C", "E")) {
            EvidenceItem row = practice(outcome, open);
            assertThat(row.reasonCode()).isEqualTo(PracticeOutcome.NOT_MET_WINDOW_OPEN);
            assertThat(row.points()).isZero();
        }
    }

    @Test
    void item17_tipoDeEquipeSemDataNaoIsentaD() {
        CanonicalTeam undated = new CanonicalTeam(CanonicalFixtures.ref("cnes"), IBGE, INE, "1234567", "76", null);
        RuleOutcome outcome = C2Pack.compute(child().add(undated).build(), CONTEXT);
        assertThat(practice(outcome, "D").decision()).isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
    }

    @Test
    void item20_janelasDosRequisitosSaoExatas() {
        for (PartRequirement part : new C2Pack().requirements(MARCH).parts()) {
            assertThat(part.periodStart()).isEqualTo(LocalDate.of(2024, 2, 1));
            assertThat(part.periodEndExclusive()).isEqualTo(LocalDate.of(2026, 4, 1));
            assertThat(part.dateParams())
                    .containsEntry(PartRequirement.BIRTH_DATE_FROM, LocalDate.of(2024, 2, 29))
                    .containsEntry(PartRequirement.BIRTH_DATE_TO, LocalDate.of(2026, 3, 31));
        }
    }

    private static CanonicalImmunization dose(LocalDate day, String code) {
        return CanonicalFixtures.dose(KEY, day, code, null);
    }
}
