package esusdata.indicator.pack.c2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ComponentKind;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.MonthlyEligibility;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.ValueKind;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Casos da ficha C2 (docs/metodologia/c2-desenvolvimento-infantil.md, "Casos de teste derivados") e
 * da Tech Spec (MET-*, ENG-*), escritos só a partir da transcrição e do contrato de comportamento do
 * pacote. Competência 2026-03, corte em 2026-03-31; salvo indicação a criança nasce em 2024-03-10
 * (2º aniversário em 2026-03-10, dentro da competência), vinculada à equipe {@value #INE}.
 */
class C2PackCasesTest {

    private static final String IBGE = CanonicalFixtures.IBGE;
    private static final String INE = "0000000001";
    private static final String CNES = "1234567";
    private static final YearMonth MARCO = YearMonth.of(2026, 3);
    private static final EvaluationContext CONTEXTO = EvaluationContext.endOfMonth(IBGE, MARCO);
    private static final LocalDate CORTE = LocalDate.of(2026, 3, 31);

    /** Nascimento padrão: 2º aniversário em 2026-03-10, 6 meses em 2024-09-10, 12 meses em 2025-03-10. */
    private static final LocalDate N = LocalDate.of(2024, 3, 10);

    private static final LocalDate SEGUNDO_ANIVERSARIO = LocalDate.of(2026, 3, 10);
    private static final String CRIANCA = "crianca-1";

    private static final String MEDICO = "225142";
    private static final String ENFERMEIRA = "223505";
    private static final String DENTISTA = "223208";
    private static final String TECNICO_ENFERMAGEM = "322205";
    private static final String ACS = "515105";

    private static final BigInteger VINTE = BigInteger.valueOf(20);
    private static final Set<EvidenceDecision> DECISOES_DE_PRATICA = Set.of(
            EvidenceDecision.PRACTICE_MET,
            EvidenceDecision.PRACTICE_NOT_MET,
            EvidenceDecision.PRACTICE_EXEMPT,
            EvidenceDecision.PRACTICE_AMBIGUOUS);

    private final List<Record> registros = new ArrayList<>();

    // ================================================================ MET-03, MET-04, BLOCKED

    @Test
    void met03_ct_c2_08_numeradorZeroEResultadoZeroRegularAntesDoPortao() {
        crianca("c1", N);
        crianca("c2", N);

        IndicatorResult result = calcular().result();

        assertThat(result.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(result.numerator()).isZero();
        assertThat(result.denominator()).isEqualTo(BigInteger.TWO);
        assertThat(result.valueText()).isEqualTo("0.0000");
        assertThat(result.valueExact()).isNotNull();
        assertThat(result.valueExact().isZero()).isTrue();
        assertThat(result.classification()).isEqualTo(Classification.REGULAR);
        assertThat(result.components()).allSatisfy(c -> {
            assertThat(c.numerator()).isZero();
            assertThat(c.denominator()).isEqualTo(BigInteger.TWO);
            assertThat(c.status()).isEqualTo(IndicatorStatus.COMPUTED);
        });
    }

    @Test
    void met04_ct_c2_09_semElegiveisDaNoDenominatorComValorNulo() {
        // Só uma criança fora da coorte (2º aniversário antes da competência).
        crianca("velha", LocalDate.of(2023, 6, 1));

        for (RuleOutcome outcome : List.of(calcular(), avaliar())) {
            IndicatorResult result = outcome.result();
            assertThat(result.status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
            assertThat(result.valueText()).isNull();
            assertThat(result.valueExact()).isNull();
            assertThat(result.classification()).isNull();
            assertThat(result.numerator()).isZero();
            assertThat(result.denominator()).isZero();
            assertThat(result.components()).hasSize(5).allSatisfy(c -> {
                assertThat(c.status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
                assertThat(c.value()).isNull();
            });
        }
    }

    @Test
    void met04_extratoVazioTambemDaNoDenominator() {
        assertThat(calcular().result().status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
        assertThat(calcular().result().valueText()).isNull();
    }

    @Test
    void evaluate_ficaBlockedPorPadraoMantendoContagensEComponentes() {
        crianca(CRIANCA, N);
        cumpreA(CRIANCA, N);

        IndicatorResult computado = calcular().result();
        assertThat(computado.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(computado.valueText()).isEqualTo("20.0000");
        assertThat(computado.classification()).isEqualTo(Classification.REGULAR);

        RuleOutcome avaliado = avaliar();
        IndicatorResult result = avaliado.result();
        assertThat(result.status()).isEqualTo(IndicatorStatus.BLOCKED);
        assertThat(result.valueText()).isNull();
        assertThat(result.valueExact()).isNull();
        assertThat(result.classification()).isNull();
        assertThat(result.numerator()).isEqualTo(VINTE);
        assertThat(result.denominator()).isEqualTo(BigInteger.ONE);
        assertThat(result.components()).isEqualTo(computado.components());
        assertThat(componente(result, "A").numerator()).isEqualTo(BigInteger.ONE);
        assertThat(result.limitations()).anyMatch(l -> l.contains("Portão"));
        assertThat(avaliado.teams()).singleElement().satisfies(t -> {
            assertThat(t.ine()).isEqualTo(INE);
            assertThat(t.result().status()).isEqualTo(IndicatorStatus.BLOCKED);
            assertThat(t.result().numerator()).isEqualTo(VINTE);
        });
    }

    @Test
    void municipio_registroDeOutroMunicipioERecusado() {
        crianca(CRIANCA, N);
        CanonicalCareEvent alheio = presencial(CRIANCA, N.plusDays(10));
        add(new CanonicalCareEvent(
                alheio.sourceRef(),
                "3550308",
                alheio.personKey(),
                alheio.careDate(),
                alheio.form(),
                alheio.cbo(),
                alheio.cnes(),
                alheio.ine(),
                null,
                null,
                false,
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
                null));

        assertThatThrownBy(this::avaliar).isInstanceOf(IllegalArgumentException.class);
    }

    // ================================================================ ENG-25 e médias (CT-03..07)

    @Test
    void eng25_faixasExatasEmVinteECincoCinquentaESetentaECinco() {
        C2Pack pack = new C2Pack();
        assertThat(pack.classify(ExactRatio.of(0, 1))).contains(Classification.REGULAR);
        assertThat(pack.classify(ExactRatio.of(24_999_999, 1_000_000))).contains(Classification.REGULAR);
        assertThat(pack.classify(ExactRatio.of(25, 1))).contains(Classification.REGULAR);
        assertThat(pack.classify(ExactRatio.of(25_000_001, 1_000_000))).contains(Classification.SUFICIENTE);
        assertThat(pack.classify(ExactRatio.of(49_999_999, 1_000_000))).contains(Classification.SUFICIENTE);
        assertThat(pack.classify(ExactRatio.of(50, 1))).contains(Classification.SUFICIENTE);
        assertThat(pack.classify(ExactRatio.of(50_000_001, 1_000_000))).contains(Classification.BOM);
        assertThat(pack.classify(ExactRatio.of(74_999_999, 1_000_000))).contains(Classification.BOM);
        assertThat(pack.classify(ExactRatio.of(75, 1))).contains(Classification.BOM);
        assertThat(pack.classify(ExactRatio.of(75_000_001, 1_000_000))).contains(Classification.OTIMO);
        assertThat(pack.classify(ExactRatio.of(100, 1))).contains(Classification.OTIMO);
    }

    @Test
    void ct_c2_01_criancaQueCumpreTudoTemCemPontos() {
        crianca(CRIANCA, N);
        pontuar(CRIANCA, "ABCDE");

        RuleOutcome outcome = calcular();

        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(outcome.result().numerator()).isEqualTo(BigInteger.valueOf(100));
        assertThat(outcome.result().valueText()).isEqualTo("100.0000");
        assertThat(outcome.result().classification()).isEqualTo(Classification.OTIMO);
        assertThat(outcome.result().components())
                .extracting(ResultComponent::code)
                .containsExactly("A", "B", "C", "D", "E");
        assertThat(outcome.result().components()).allSatisfy(c -> {
            assertThat(c.numerator()).isEqualTo(BigInteger.ONE);
            assertThat(c.denominator()).isEqualTo(BigInteger.ONE);
            assertThat(c.status()).isEqualTo(IndicatorStatus.COMPUTED);
        });
        for (String pratica : List.of("A", "B", "C", "D", "E")) {
            assertCumpre(outcome, CRIANCA, pratica);
        }
        assertThat(pessoa(outcome, CRIANCA).points()).isEqualTo(BigInteger.valueOf(100));
    }

    @Test
    void ct_c2_02_criancaQueSoCumpreATemVintePontos() {
        crianca(CRIANCA, N);
        cumpreA(CRIANCA, N);

        RuleOutcome outcome = calcular();

        assertCumpre(outcome, CRIANCA, "A");
        for (String pratica : List.of("B", "C", "D", "E")) {
            assertNaoCumpre(outcome, CRIANCA, pratica);
        }
        assertThat(pessoa(outcome, CRIANCA).points()).isEqualTo(VINTE);
        assertThat(outcome.result().valueText()).isEqualTo("20.0000");
    }

    @Test
    void ct_c2_03_mediaDeCemQuarentaEZeroESuficiente() {
        criancasComPontos("ABCDE", "AD", "");

        IndicatorResult result = calcular().result();

        assertThat(result.numerator()).isEqualTo(BigInteger.valueOf(140));
        assertThat(result.denominator()).isEqualTo(BigInteger.valueOf(3));
        assertThat(result.valueExact()).isEqualByComparingTo(ExactRatio.of(140, 3));
        assertThat(result.valueText()).isEqualTo("46.6667");
        assertThat(result.classification()).isEqualTo(Classification.SUFICIENTE);
    }

    @Test
    void ct_c2_04_setentaECincoExatoEBom() {
        criancasComPontos("ABCDE", "ABCDE", "ADE", "AD");

        IndicatorResult result = calcular().result();

        assertThat(result.valueExact()).isEqualByComparingTo(ExactRatio.of(75, 1));
        assertThat(result.valueText()).isEqualTo("75.0000");
        assertThat(result.classification()).isEqualTo(Classification.BOM);
    }

    @Test
    void ct_c2_05_setentaESeisEOtimo() {
        criancasComPontos("ABCDE", "ABCDE", "ABCDE", "AD", "AD");

        IndicatorResult result = calcular().result();

        assertThat(result.valueExact()).isEqualByComparingTo(ExactRatio.of(76, 1));
        assertThat(result.classification()).isEqualTo(Classification.OTIMO);
    }

    @Test
    void ct_c2_06_cinquentaExatoESuficiente() {
        criancasComPontos("ABCDE", "");

        IndicatorResult result = calcular().result();

        assertThat(result.valueExact()).isEqualByComparingTo(ExactRatio.of(50, 1));
        assertThat(result.classification()).isEqualTo(Classification.SUFICIENTE);
    }

    @Test
    void ct_c2_07_vinteECincoExatoERegular() {
        criancasComPontos("ABCDE", "", "", "");

        IndicatorResult result = calcular().result();

        assertThat(result.valueExact()).isEqualByComparingTo(ExactRatio.of(25, 1));
        assertThat(result.classification()).isEqualTo(Classification.REGULAR);
    }

    // ================================================================ Prática A (MET-19, CT-10..16)

    @Test
    void met19_primeiraConsultaNoDia29Cumpre_30Ambigua_31NaoCumpre_demaisPraticasIndependentes() {
        for (int dia : List.of(29, 30, 31)) {
            String chave = "dia-" + dia;
            crianca(chave, N);
            add(presencial(chave, N.plusDays(dia)));
            cumpreD(chave, N); // D não depende de A
        }

        RuleOutcome outcome = calcular();

        assertCumpre(outcome, "dia-29", "A");
        assertAmbigua(outcome, "dia-30", "A", "AMB-C2-01");
        assertNaoCumpre(outcome, "dia-31", "A");
        for (String chave : List.of("dia-29", "dia-30", "dia-31")) {
            assertCumpre(outcome, chave, "D");
            assertNaoCumpre(outcome, chave, "B");
        }
        assertThat(pessoa(outcome, "dia-29").points()).isEqualTo(BigInteger.valueOf(40));
        assertThat(pessoa(outcome, "dia-30").points()).isNull();
        assertThat(pessoa(outcome, "dia-31").points()).isEqualTo(VINTE);
        // limite inferior: só os pontos certos (40 + 20 + 20)
        assertThat(outcome.result().numerator()).isEqualTo(BigInteger.valueOf(80));
        assertThat(componente(outcome.result(), "A").numerator()).isEqualTo(BigInteger.ONE);
        assertThat(componente(outcome.result(), "D").status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct_c2_10_primeiraConsultaPresencialEmNMais29Cumpre() {
        crianca(CRIANCA, N);
        CanonicalCareEvent consulta = presencial(CRIANCA, N.plusDays(29));
        add(consulta);

        RuleOutcome outcome = calcular();

        assertCumpre(outcome, CRIANCA, "A");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(outcome.evidence()).anySatisfy(e -> {
            assertThat(e.decision()).isEqualTo(EvidenceDecision.SUPPORTING_EVENT);
            assertThat(e.component()).isEqualTo("A");
            assertThat(e.sourceRef()).isEqualTo(consulta.sourceRef());
            assertThat(e.eventDate()).isEqualTo(consulta.careDate());
        });
    }

    @Test
    void ct_c2_11_primeiraConsultaPresencialEmNMais30EAmbigua() {
        crianca(CRIANCA, N);
        add(presencial(CRIANCA, N.plusDays(30)));

        assertAmbiguaAntesEDepoisDoPortao(CRIANCA, "A", "AMB-C2-01");
        IndicatorResult result = calcular().result();
        assertThat(result.numerator()).isZero();
        assertThat(result.denominator()).isEqualTo(BigInteger.ONE);
        assertThat(calcular().teams())
                .singleElement()
                .satisfies(t -> assertThat(t.result().status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY));
    }

    @Test
    void ct_c2_12_primeiraConsultaPresencialEmNMais31NaoCumpre() {
        crianca(CRIANCA, N);
        add(presencial(CRIANCA, N.plusDays(31)));

        RuleOutcome outcome = calcular();

        assertNaoCumpre(outcome, CRIANCA, "A");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct_c2_13_consultaRemotaNaoCumpreAMasAsDuasContamParaB() {
        crianca(CRIANCA, N);
        add(remota(CRIANCA, N.plusDays(10)), presencial(CRIANCA, N.plusDays(35)));
        consultasPresenciais(CRIANCA, N, 7); // 7 + remota + presencial = 9

        RuleOutcome outcome = calcular();

        assertNaoCumpre(outcome, CRIANCA, "A");
        assertCumpre(outcome, CRIANCA, "B");
    }

    @Test
    void ct_c2_14_consultaDeCirurgiaoDentistaNaoCumpreA() {
        crianca(CRIANCA, N);
        add(atendimento(CRIANCA, N.plusDays(10), DENTISTA, false, "INDIVIDUAL", null));

        assertNaoCumpre(calcular(), CRIANCA, "A");
    }

    @Test
    void ct_c2_15_puericulturaNaoEFiltradaLacunaL7() {
        // A ficha exige "Puericultura" no problema/condição avaliado; o contrato não filtra (lacuna
        // L7, sem marcador no PEC) e declara a limitação permanente. A consulta conta.
        crianca(CRIANCA, N);
        add(presencial(CRIANCA, N.plusDays(10)));

        assertCumpre(calcular(), CRIANCA, "A");
        assertThat(new C2Pack().descriptor().standingLimitations()).anyMatch(menciona("L7", "puericultura"));
    }

    @Test
    void ct_c2_16_unicaConsultaDomiciliarEAmbiguaAmb04() {
        crianca(CRIANCA, N);
        add(domiciliar(CRIANCA, N.plusDays(10)));

        assertAmbiguaAntesEDepoisDoPortao(CRIANCA, "A", "AMB-C2-04");
    }

    @Test
    void a_modalidadeDesconhecidaFicaAmbiguaPelaLacunaL3() {
        crianca(CRIANCA, N);
        add(atendimento(CRIANCA, N.plusDays(10), MEDICO, null, "INDIVIDUAL", null));

        assertAmbiguaAntesEDepoisDoPortao(CRIANCA, "A", "LACUNA-L3");
    }

    @Test
    void a_procedimentoMip0301010277SemConsultaEAmbiguoAmb06() {
        crianca(CRIANCA, N);
        add(procedimento(CRIANCA, N.plusDays(10), "0301010277", ENFERMEIRA, "MIP"));

        assertAmbiguaAntesEDepoisDoPortao(CRIANCA, "A", "AMB-C2-06");
    }

    // CT-C2-17 (equipe não 70/76, AMB-C2-11 em A/B): o contrato não declara essa leitura; omitido.

    // ================================================================ Prática B (CT-18..24, MET-32)

    @Test
    void ct_c2_18_oitoConsultasNaoBastam() {
        crianca(CRIANCA, N);
        consultasPresenciais(CRIANCA, N, 8);

        RuleOutcome outcome = calcular();

        assertNaoCumpre(outcome, CRIANCA, "B");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct_c2_19_noveConsultasPresenciaisERemotasCumprem() {
        crianca(CRIANCA, N);
        consultasPresenciais(CRIANCA, N, 6);
        add(remota(CRIANCA, N.plusDays(400)), remota(CRIANCA, N.plusDays(440)), remota(CRIANCA, N.plusDays(480)));

        assertCumpre(calcular(), CRIANCA, "B");
    }

    @Test
    void ct_c2_20_oitoAntesEUmaDepoisDoSegundoAniversarioNaoCumpre() {
        crianca(CRIANCA, N);
        consultasPresenciais(CRIANCA, N, 8);
        add(presencial(CRIANCA, SEGUNDO_ANIVERSARIO.plusDays(5)));

        RuleOutcome outcome = calcular();

        assertNaoCumpre(outcome, CRIANCA, "B");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct_c2_21_nonaConsultaNoDiaDoSegundoAniversarioEAmbigua() {
        crianca(CRIANCA, N);
        consultasPresenciais(CRIANCA, N, 8);
        add(presencial(CRIANCA, SEGUNDO_ANIVERSARIO));

        assertAmbiguaAntesEDepoisDoPortao(CRIANCA, "B", "AMB-C2-02");
    }

    @Test
    void ct_c2_22_nonaSoNoMipComTeleconsultaEAmbigua() {
        crianca(CRIANCA, N);
        consultasPresenciais(CRIANCA, N, 8);
        add(procedimento(CRIANCA, N.plusDays(400), "0301010250", ENFERMEIRA, "MIP"));

        assertAmbiguaAntesEDepoisDoPortao(CRIANCA, "B", "AMB-C2-06");
    }

    @Test
    void ct_c2_23_doisAtendimentosNoMesmoDiaEmOitoDiasEAmbiguo() {
        crianca(CRIANCA, N);
        consultasPresenciais(CRIANCA, N, 8);
        add(atendimento(CRIANCA, N.plusDays(40), ENFERMEIRA, false, "INDIVIDUAL", null));

        assertAmbiguaAntesEDepoisDoPortao(CRIANCA, "B", "AMB-C2-15");
    }

    @Test
    void ct_c2_24_met32_consultaDuplicadaContaUmaVez() {
        crianca(CRIANCA, N);
        consultasPresenciais(CRIANCA, N, 8);
        add(presencial(CRIANCA, N.plusDays(40))); // mesma data, CBO, CNES e INE da 1ª

        RuleOutcome outcome = calcular();

        assertNaoCumpre(outcome, CRIANCA, "B");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    // ================================================================ Prática C (CT-25..32)

    @Test
    void ct_c2_25_noveDiasComPesoEAlturaNoMiaiCumpre() {
        crianca(CRIANCA, N);
        paresMiai(CRIANCA, N, 9);

        RuleOutcome outcome = calcular();

        assertCumpre(outcome, CRIANCA, "C");
        assertNaoCumpre(outcome, CRIANCA, "B"); // técnico de enfermagem não faz consulta de B
    }

    @Test
    void c_noveDiasComParesDeModelosDiferentesCumpre() {
        crianca(CRIANCA, N);
        paresMiai(CRIANCA, N, 3); // N+43, N+83, N+123
        add(
                medidaMiai(CRIANCA, N.plusDays(160), "7.9", null),
                visitaComMedidas(CRIANCA, N.plusDays(160), null, "68.0"),
                CanonicalFixtures.measurement(CRIANCA, N.plusDays(200), "8.1", "69.0", "MIP"),
                CanonicalFixtures.measurement(CRIANCA, N.plusDays(240), "8.3", "70.0", "MIAC"),
                procedimento(CRIANCA, N.plusDays(280), "0101040083", TECNICO_ENFERMAGEM, "MIP"),
                procedimento(CRIANCA, N.plusDays(280), "0101040075", TECNICO_ENFERMAGEM, "MIP"),
                visitaComMedidas(CRIANCA, N.plusDays(320), "8.8", "73.0"),
                procedimento(CRIANCA, N.plusDays(360), "0101040083", TECNICO_ENFERMAGEM, "MIAI"),
                procedimento(CRIANCA, N.plusDays(360), "0101040075", TECNICO_ENFERMAGEM, "MIAI"));

        assertCumpre(calcular(), CRIANCA, "C");
    }

    @Test
    void ct_c2_26_oitoParesEUmDiaSoComPesoNaoCumpre() {
        crianca(CRIANCA, N);
        paresMiai(CRIANCA, N, 8);
        add(medidaMiai(CRIANCA, N.plusDays(400), "9.5", null));

        assertNaoCumpre(calcular(), CRIANCA, "C");
    }

    @Test
    void ct_c2_27_oitoNoMiaiEUmParDeProcedimentosNoMipCumpre() {
        crianca(CRIANCA, N);
        paresMiai(CRIANCA, N, 8);
        add(
                procedimento(CRIANCA, N.plusDays(400), "0101040083", TECNICO_ENFERMAGEM, "MIP"),
                procedimento(CRIANCA, N.plusDays(400), "0101040075", TECNICO_ENFERMAGEM, "MIP"));

        assertCumpre(calcular(), CRIANCA, "C");
    }

    @Test
    void ct_c2_28_oitoNoMiaiEUmDiaSoComAvaliacaoAntropometricaEAmbiguo() {
        crianca(CRIANCA, N);
        paresMiai(CRIANCA, N, 8);
        add(procedimento(CRIANCA, N.plusDays(400), "0101040024", TECNICO_ENFERMAGEM, "MIP"));

        assertAmbiguaAntesEDepoisDoPortao(CRIANCA, "C", "AMB-C2-07");
    }

    @Test
    void ct_c2_29_pesoNoMiaiEAlturaNoMivdtNoMesmoDiaFormamPar() {
        crianca(CRIANCA, N);
        paresMiai(CRIANCA, N, 8);
        add(
                medidaMiai(CRIANCA, N.plusDays(400), "9.5", null),
                visitaComMedidas(CRIANCA, N.plusDays(400), null, "78.0"));

        assertCumpre(calcular(), CRIANCA, "C");
    }

    @Test
    void ct_c2_30_oitoDiasComUmDiaDeDoisParesEAmbiguo() {
        crianca(CRIANCA, N);
        paresMiai(CRIANCA, N, 8); // o 1º em N+43
        add(CanonicalFixtures.measurement(CRIANCA, N.plusDays(43), "6.0", "60.0", "MIAC"));

        assertAmbiguaAntesEDepoisDoPortao(CRIANCA, "C", "AMB-C2-07");
    }

    @Test
    void ct_c2_31_registroConsolidadoNaoConta() {
        crianca(CRIANCA, N);
        paresMiai(CRIANCA, N, 8);
        add(CanonicalFixtures.measurement(CRIANCA, N.plusDays(400), "9.5", "78.0", "CONSOLIDADO"));

        RuleOutcome outcome = calcular();

        assertNaoCumpre(outcome, CRIANCA, "C");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct_c2_32_nonoParDepoisDoSegundoAniversarioNaoConta() {
        crianca(CRIANCA, N);
        paresMiai(CRIANCA, N, 8);
        add(medidaMiai(CRIANCA, SEGUNDO_ANIVERSARIO.plusDays(10), "12.0", "86.0"));

        RuleOutcome outcome = calcular();

        assertNaoCumpre(outcome, CRIANCA, "C");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    // ================================================================ Prática D (CT-33..45)

    @Test
    void ct_c2_33_visitasEmNMais10ENMais100Cumprem() {
        crianca(CRIANCA, N);
        cumpreD(CRIANCA, N);

        assertCumpre(calcular(), CRIANCA, "D");
    }

    @Test
    void ct_c2_34_primeiraVisitaEm29Cumpre_30Ambigua_31NaoCumpre() {
        for (int dia : List.of(29, 30, 31)) {
            String chave = "visita-" + dia;
            crianca(chave, N);
            add(visita(chave, N.plusDays(dia)), visita(chave, N.plusDays(100)));
        }

        RuleOutcome outcome = calcular();

        assertCumpre(outcome, "visita-29", "D");
        assertAmbigua(outcome, "visita-30", "D", "AMB-C2-01");
        assertNaoCumpre(outcome, "visita-31", "D");
        assertThat(avaliar().result().status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
    }

    @Test
    void ct_c2_35_primeiraVisitaForaDosTrintaDiasNaoCumpre() {
        crianca(CRIANCA, N);
        add(visita(CRIANCA, N.plusDays(40)), visita(CRIANCA, N.plusDays(100)));

        assertNaoCumpre(calcular(), CRIANCA, "D");
    }

    @Test
    void ct_c2_36_duasVisitasDentroDosTrintaDiasEAmbiguo() {
        crianca(CRIANCA, N);
        add(visita(CRIANCA, N.plusDays(10)), visita(CRIANCA, N.plusDays(20)));

        assertAmbiguaAntesEDepoisDoPortao(CRIANCA, "D", "AMB-C2-08");
    }

    @Test
    void ct_c2_37_segundaVisitaNoDiaDosSeisMesesEAmbigua() {
        crianca(CRIANCA, N);
        add(visita(CRIANCA, N.plusDays(10)), visita(CRIANCA, N.plusMonths(6)));

        assertAmbiguaAntesEDepoisDoPortao(CRIANCA, "D", "AMB-C2-02");
    }

    @Test
    void ct_c2_38_segundaVisitaNoDiaSeguinteAosSeisMesesNaoCumpre() {
        crianca(CRIANCA, N);
        add(visita(CRIANCA, N.plusDays(10)), visita(CRIANCA, N.plusMonths(6).plusDays(1)));

        RuleOutcome outcome = calcular();

        assertNaoCumpre(outcome, CRIANCA, "D");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct_c2_39_doisRegistrosDeVisitaNaMesmaDataEAmbiguo() {
        crianca(CRIANCA, N);
        add(visita(CRIANCA, N.plusDays(10)), visita(CRIANCA, N.plusDays(10)));

        assertAmbiguaAntesEDepoisDoPortao(CRIANCA, "D", "AMB-C2-08");
    }

    @Test
    void ct_c2_40_visitasDeTecnicoDeEnfermagemNaoContam() {
        crianca(CRIANCA, N);
        add(
                visita(CRIANCA, N.plusDays(10), TECNICO_ENFERMAGEM, List.of("ACOMP_CRIANCA"), "1"),
                visita(CRIANCA, N.plusDays(100), TECNICO_ENFERMAGEM, List.of("ACOMP_CRIANCA"), "1"));

        assertNaoCumpre(calcular(), CRIANCA, "D");
    }

    @Test
    void ct_c2_41_visitasComOutroMotivoNaoContam() {
        crianca(CRIANCA, N);
        add(
                visita(CRIANCA, N.plusDays(10), ACS, List.of("ACOMP_GESTANTE"), "1"),
                visita(CRIANCA, N.plusDays(100), ACS, List.of("ACOMP_GESTANTE"), "1"));

        RuleOutcome outcome = calcular();

        assertNaoCumpre(outcome, CRIANCA, "D");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct_c2_42_visitaComDesfechoNaoRealizadoEAmbigua() {
        crianca(CRIANCA, N);
        add(
                visita(CRIANCA, N.plusDays(10), ACS, List.of("ACOMP_RECEM_NASCIDO"), "2"),
                visita(CRIANCA, N.plusDays(100)));

        assertAmbiguaAntesEDepoisDoPortao(CRIANCA, "D", "AMB-C2-08");
    }

    @Test
    void ct_c2_43_eap76SemVisitaTemDIsenta() {
        crianca(CRIANCA, N);
        add(equipe("76"));

        RuleOutcome outcome = calcular();

        EvidenceItem d = pratica(outcome, CRIANCA, "D");
        assertThat(d.decision()).isEqualTo(EvidenceDecision.PRACTICE_EXEMPT);
        assertThat(d.points()).isEqualTo(VINTE);
        assertThat(d.reasonCode()).isEqualTo("ISENTA_EAP_76");
        assertThat(componente(outcome.result(), "D").numerator()).isEqualTo(BigInteger.ONE);
        assertThat(pessoa(outcome, CRIANCA).points()).isEqualTo(VINTE);
    }

    @Test
    void ct_c2_44_esf70SemVisitaTemDZero() {
        crianca(CRIANCA, N);
        add(equipe("70"));

        RuleOutcome outcome = calcular();

        assertNaoCumpre(outcome, CRIANCA, "D");
        assertThat(outcome.evidence()).noneMatch(e -> e.decision() == EvidenceDecision.PRACTICE_EXEMPT);
    }

    @Test
    void ct_c2_45_eap76QueCumpreABCESemVisitasTemCemPontos() {
        crianca(CRIANCA, N);
        add(equipe("76"));
        pontuar(CRIANCA, "ABCE");

        IndicatorResult result = calcular().result();

        assertThat(result.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(result.numerator()).isEqualTo(BigInteger.valueOf(100));
        assertThat(result.valueText()).isEqualTo("100.0000");
    }

    @Test
    void d_semTipoDeEquipeEAvaliadaComLimitacaoL1() {
        crianca(CRIANCA, N);

        RuleOutcome outcome = calcular();

        assertNaoCumpre(outcome, CRIANCA, "D");
        assertThat(outcome.result().limitations()).anyMatch(menciona("L1", "tipo de equipe"));
    }

    // ================================================================ Prática E (CT-46..63)

    @Test
    void ct_c2_46_esquemaCompletoCumpre() {
        crianca(CRIANCA, N);
        esquemaCompleto(CRIANCA, N);

        RuleOutcome outcome = calcular();

        assertCumpre(outcome, CRIANCA, "E");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct_c2_47_esquemaAlternativoHexaScrvEVpc13Cumpre() {
        crianca(CRIANCA, N);
        doses(CRIANCA, "43", N.plusDays(60), N.plusDays(120), N.plusDays(180));
        doses(CRIANCA, "56", N.plusMonths(13), N.plusMonths(15));
        doses(CRIANCA, "59", N.plusDays(60), N.plusDays(120));

        assertCumpre(calcular(), CRIANCA, "E");
    }

    @Test
    void ct_c2_48_pentaSemPolioNaoCumpre() {
        crianca(CRIANCA, N);
        penta(CRIANCA, N);
        scr(CRIANCA, N);
        pneumo(CRIANCA, N);

        assertNaoCumpre(calcular(), CRIANCA, "E");
    }

    @Test
    void ct_c2_49_soUmaDoseDeScrNaoCumpre() {
        crianca(CRIANCA, N);
        penta(CRIANCA, N);
        vip(CRIANCA, N);
        doses(CRIANCA, "24", N.plusMonths(12));
        pneumo(CRIANCA, N);

        assertNaoCumpre(calcular(), CRIANCA, "E");
    }

    @Test
    void ct_c2_50_scrNaVesperaDosDozeMesesNaoConta() {
        crianca(CRIANCA, N);
        penta(CRIANCA, N);
        vip(CRIANCA, N);
        doses(CRIANCA, "24", N.plusMonths(12).minusDays(1), N.plusMonths(15));
        pneumo(CRIANCA, N);

        RuleOutcome outcome = calcular();

        assertNaoCumpre(outcome, CRIANCA, "E");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct_c2_51_scrNoDiaDosDozeMesesConta() {
        crianca(CRIANCA, N);
        penta(CRIANCA, N);
        vip(CRIANCA, N);
        doses(CRIANCA, "24", N.plusMonths(12), N.plusMonths(15));
        pneumo(CRIANCA, N);

        RuleOutcome outcome = calcular();

        assertCumpre(outcome, CRIANCA, "E");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct_c2_52_intervaloDe29DiasDeixaGrupo1ComDuasDoses() {
        crianca(CRIANCA, N);
        doses(CRIANCA, "42", N.plusDays(60), N.plusDays(89), N.plusDays(150));
        vip(CRIANCA, N);
        scr(CRIANCA, N);
        pneumo(CRIANCA, N);

        RuleOutcome outcome = calcular();

        assertNaoCumpre(outcome, CRIANCA, "E");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct_c2_53_intervaloDe30DiasEAceito() {
        crianca(CRIANCA, N);
        doses(CRIANCA, "42", N.plusDays(60), N.plusDays(90), N.plusDays(120));
        vip(CRIANCA, N);
        scr(CRIANCA, N);
        pneumo(CRIANCA, N);

        assertCumpre(calcular(), CRIANCA, "E");
    }

    @Test
    void ct_c2_54_doseComIntervaloCurtoDescartadaOuInvalidandoEAmbiguo() {
        crianca(CRIANCA, N);
        doses(CRIANCA, "42", N.plusDays(60), N.plusDays(89), N.plusDays(150), N.plusDays(200));
        vip(CRIANCA, N);
        scr(CRIANCA, N);
        pneumo(CRIANCA, N);

        assertAmbiguaAntesEDepoisDoPortao(CRIANCA, "E", "AMB-C2-09");
    }

    @Test
    void ct_c2_55_porComponenteCumprePorOcasiaoNaoEAmbiguo() {
        crianca(CRIANCA, N);
        doses(CRIANCA, "39", N.plusDays(60), N.plusDays(120), N.plusDays(180));
        doses(CRIANCA, "09", N.plusDays(70), N.plusDays(130), N.plusDays(190));
        vip(CRIANCA, N);
        scr(CRIANCA, N);
        pneumo(CRIANCA, N);

        assertAmbiguaAntesEDepoisDoPortao(CRIANCA, "E", "AMB-C2-09");
    }

    @Test
    void ct_c2_56_produtosSeparadosNaMesmaDataCumprem() {
        crianca(CRIANCA, N);
        for (String codigo : List.of("46", "09", "17")) {
            doses(CRIANCA, codigo, N.plusDays(60), N.plusDays(120), N.plusDays(180));
        }
        vip(CRIANCA, N);
        scr(CRIANCA, N);
        pneumo(CRIANCA, N);

        RuleOutcome outcome = calcular();

        assertCumpre(outcome, CRIANCA, "E");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct_c2_57_hepBAoNascerMaisDuasPentasNaoCumpre() {
        crianca(CRIANCA, N);
        doses(CRIANCA, "09", N);
        doses(CRIANCA, "42", N.plusDays(60), N.plusDays(120));
        vip(CRIANCA, N);
        scr(CRIANCA, N);
        pneumo(CRIANCA, N);

        RuleOutcome outcome = calcular();

        assertNaoCumpre(outcome, CRIANCA, "E");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct_c2_58_hepBAoNascerDecideOTerceiroComponenteEAmbiguo() {
        crianca(CRIANCA, N);
        doses(CRIANCA, "09", N);
        doses(CRIANCA, "42", N.plusDays(60), N.plusDays(120));
        doses(CRIANCA, "39", N.plusDays(180));
        vip(CRIANCA, N);
        scr(CRIANCA, N);
        pneumo(CRIANCA, N);

        assertAmbiguaAntesEDepoisDoPortao(CRIANCA, "E", "AMB-C2-09");
    }

    @Test
    void ct_c2_59_vpc10EVpc13ContamJuntasNoGrupo4() {
        crianca(CRIANCA, N);
        penta(CRIANCA, N);
        vip(CRIANCA, N);
        scr(CRIANCA, N);
        doses(CRIANCA, "26", N.plusDays(60));
        doses(CRIANCA, "59", N.plusDays(120));

        assertCumpre(calcular(), CRIANCA, "E");
    }

    @Test
    void ct_c2_60_met32_mesmaDoseNoMivENoRiaContaUma() {
        crianca(CRIANCA, N);
        esquemaCompleto(CRIANCA, N);
        add(new CanonicalImmunization(
                CanonicalFixtures.ref("ria"),
                IBGE,
                CRIANCA,
                N.plusDays(60).toString(),
                "42",
                null,
                null,
                false,
                null,
                null,
                null));

        RuleOutcome outcome = calcular();

        // contada duas vezes, a dose teria intervalo 0 e a leitura "invalida o esquema" divergiria
        assertCumpre(outcome, CRIANCA, "E");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct_c2_61_transcricaoRegistradaDepoisDosDoisAnosEAmbigua() {
        // CT-61: SCR aplicada aos 13 e aos 15 meses e transcrita depois do 2º aniversário (antes do
        // corte): conta pela data de aplicação, não pela de registro (AMB-C2-09 v, AMB-C2-10 i).
        crianca(CRIANCA, N);
        penta(CRIANCA, N);
        vip(CRIANCA, N);
        pneumo(CRIANCA, N);
        LocalDate registro = N.plusYears(2).plusDays(10);
        for (LocalDate data : List.of(N.plusMonths(13), N.plusMonths(15))) {
            add(CanonicalFixtures.transcribedDose(CRIANCA, data, registro, "24", null));
        }

        assertAmbigua(calcular(), CRIANCA, "E", "AMB-C2-10");
    }

    @Test
    void ct_c2_61_transcricaoDentroDosDoisAnosContaPelaAplicacao() {
        crianca(CRIANCA, N);
        penta(CRIANCA, N);
        vip(CRIANCA, N);
        pneumo(CRIANCA, N);
        LocalDate registro = N.plusMonths(16);
        for (LocalDate data : List.of(N.plusMonths(13), N.plusMonths(15))) {
            add(CanonicalFixtures.transcribedDose(CRIANCA, data, registro, "24", null));
        }

        assertCumpre(calcular(), CRIANCA, "E");
    }

    @Test
    void ct_c2_62_codigoForaDoQuadro05NaoConta() {
        crianca(CRIANCA, N);
        penta(CRIANCA, N);
        vip(CRIANCA, N);
        scr(CRIANCA, N);
        doses(CRIANCA, "99", N.plusDays(60), N.plusDays(120));

        RuleOutcome outcome = calcular();

        assertNaoCumpre(outcome, CRIANCA, "E");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void ct_c2_63_criancaDeTresMesesComUmaDoseDeCadaGrupoTemEZeroEEntraNoDenominador() {
        LocalDate nascimento = LocalDate.of(2025, 12, 15);
        crianca(CRIANCA, nascimento);
        for (String codigo : List.of("42", "22", "24", "26")) {
            doses(CRIANCA, codigo, nascimento.plusDays(60));
        }

        RuleOutcome outcome = calcular();

        assertNaoCumpre(outcome, CRIANCA, "E");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.ONE);
        assertThat(pessoa(outcome, CRIANCA).reasonCode()).isEqualTo("COORTE_ATE_2_ANOS");
    }

    @Test
    void e_doseDepoisDoSegundoAniversarioEAmbiguaAmb10() {
        crianca(CRIANCA, N);
        doses(CRIANCA, "42", N.plusDays(60), N.plusDays(120), SEGUNDO_ANIVERSARIO.plusDays(5));
        vip(CRIANCA, N);
        scr(CRIANCA, N);
        pneumo(CRIANCA, N);

        assertAmbiguaAntesEDepoisDoPortao(CRIANCA, "E", "AMB-C2-10");
    }

    @Test
    void e_doseComCboForaDaListaEAmbiguaAmb11() {
        crianca(CRIANCA, N);
        doses(CRIANCA, "42", N.plusDays(60), N.plusDays(120));
        add(new CanonicalImmunization(
                CanonicalFixtures.ref("tb_fat_vacinacao_vacina"),
                IBGE,
                CRIANCA,
                N.plusDays(180).toString(),
                "42",
                null,
                null,
                false,
                "252545",
                CNES,
                INE));
        vip(CRIANCA, N);
        scr(CRIANCA, N);
        pneumo(CRIANCA, N);

        assertAmbiguaAntesEDepoisDoPortao(CRIANCA, "E", "AMB-C2-11");
    }

    // ================================================================ Coorte, ENG-27, ENG-36

    @Test
    void ct_c2_64_segundoAniversarioAntesDaCompetenciaFicaForaDoDenominador() {
        crianca(CRIANCA, LocalDate.of(2024, 2, 28)); // 2º aniversário em 2026-02-28

        RuleOutcome outcome = calcular();

        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
        assertThat(pessoa(outcome, CRIANCA).decision()).isEqualTo(EvidenceDecision.EXCLUDED);
        assertThat(pessoa(outcome, CRIANCA).reasonCode()).isEqualTo("EXCLUIDO_IDADE_ACIMA_2_ANOS");
    }

    @Test
    void ct_c2_65_criancaQueCompletaDoisAnosNaCompetenciaEntra() {
        // A ficha propõe RULE_AMBIGUITY (AMB-C2-02/03) para a inclusão; o contrato declara que entra.
        crianca(CRIANCA, N);

        RuleOutcome outcome = calcular();

        assertThat(pessoa(outcome, CRIANCA).decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
        assertThat(pessoa(outcome, CRIANCA).reasonCode()).isEqualTo("COORTE_COMPLETA_2_ANOS_NA_COMPETENCIA");
        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.ONE);
        assertThat(new C2Pack().descriptor().standingLimitations()).anyMatch(menciona("AMB-C2-03"));
    }

    @Test
    void ct_c2_66_saidaPorMudancaDeTerritorioNaVersaoMaisRecenteInterrompe() {
        crianca(CRIANCA, N);
        add(versao(CRIANCA, LocalDate.of(2025, 5, 1), false, false, "136"));

        RuleOutcome outcome = calcular();

        assertThat(pessoa(outcome, CRIANCA).reasonCode()).isEqualTo("INTERROMPIDO_MUDANCA_TERRITORIO");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
    }

    @Test
    void vinculo_versaoSimplificadaOuInativaMaisRecenteEIgnorada() {
        crianca(CRIANCA, N);
        add(new CanonicalRegistration(
                CanonicalFixtures.ref("tb_fat_cad_individual"),
                IBGE,
                CRIANCA,
                "2025-05-01",
                CNES,
                INE,
                true,
                false,
                false,
                "136",
                null,
                null,
                null));
        add(new CanonicalRegistration(
                CanonicalFixtures.ref("tb_fat_cad_individual"),
                IBGE,
                CRIANCA,
                "2025-06-01",
                CNES,
                INE,
                false,
                true,
                false,
                "136",
                null,
                null,
                null));

        assertThat(pessoa(calcular(), CRIANCA).decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
    }

    @Test
    void eng27_nascidaEm29DeFevereiroEntraNaCompetenciaDeMarcoPelaRegraNextDay() {
        crianca(CRIANCA, LocalDate.of(2024, 2, 29)); // NEXT_DAY: 2º aniversário em 2026-03-01

        RuleOutcome outcome = calcular();

        assertThat(pessoa(outcome, CRIANCA).decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
        assertThat(pessoa(outcome, CRIANCA).reasonCode()).isEqualTo("COORTE_COMPLETA_2_ANOS_NA_COMPETENCIA");
        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.ONE);
        assertThat(outcome.result().consolidationEligible()).isTrue();
    }

    @Test
    void eng27_nascidaEm31DeAgostoTemAniversarioDeSeisMesesInexistente() {
        LocalDate nascimento = LocalDate.of(2025, 8, 31); // 6 meses: 28/02 (CLAMP) ou 01/03 (NEXT_DAY)
        crianca("divergente", nascimento);
        add(visita("divergente", nascimento.plusDays(10)), visita("divergente", LocalDate.of(2026, 2, 28)));
        crianca("antes", nascimento);
        add(visita("antes", nascimento.plusDays(10)), visita("antes", LocalDate.of(2026, 2, 27)));
        crianca("depois", nascimento);
        add(visita("depois", nascimento.plusDays(10)), visita("depois", LocalDate.of(2026, 3, 2)));

        RuleOutcome outcome = calcular();

        assertAmbigua(outcome, "divergente", "D", "AMB-C2-02");
        assertCumpre(outcome, "antes", "D");
        assertNaoCumpre(outcome, "depois", "D");
    }

    @Test
    void eng36_evidenciaPersonReconstroiAPopulacaoInclusiveExcluidos() {
        crianca("elegivel", N);
        crianca("nasceu-depois", LocalDate.of(2026, 4, 5));
        crianca("idade-acima", LocalDate.of(2023, 1, 10));
        add(CanonicalFixtures.person("sem-vinculo", N, "M"));
        add(CanonicalFixtures.person("ine-vazio", N, "M"), CanonicalFixtures.registration("ine-vazio", N, CNES, ""));
        add(CanonicalFixtures.person("recusa", N, "F"), versao("recusa", N, true, false, null));
        crianca("mudou", N);
        add(versao("mudou", N.plusMonths(6), false, false, "136"));
        add(
                new CanonicalPerson(
                        CanonicalFixtures.ref("tb_fat_cad_individual"),
                        IBGE,
                        "obito",
                        N.toString(),
                        "F",
                        null,
                        "2026-02-01"),
                CanonicalFixtures.registration("obito", N, CNES, INE));
        crianca("obito-cadastro", N);
        add(versao("obito-cadastro", N.plusMonths(3), false, false, "135"));

        RuleOutcome outcome = calcular();

        List<EvidenceItem> pessoas = outcome.evidence().stream()
                .filter(e -> e.decision() == EvidenceDecision.ELIGIBLE || e.decision() == EvidenceDecision.EXCLUDED)
                .toList();
        Map<String, String> motivos =
                pessoas.stream().collect(Collectors.toMap(EvidenceItem::subjectKey, EvidenceItem::reasonCode));
        assertThat(pessoas).hasSize(9);
        assertThat(motivos)
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "elegivel", "COORTE_COMPLETA_2_ANOS_NA_COMPETENCIA",
                        "nasceu-depois", "EXCLUIDO_NASCIDO_APOS_CORTE",
                        "idade-acima", "EXCLUIDO_IDADE_ACIMA_2_ANOS",
                        "sem-vinculo", "EXCLUIDO_SEM_VINCULO",
                        "ine-vazio", "EXCLUIDO_SEM_VINCULO",
                        "recusa", "EXCLUIDO_RECUSA_CADASTRO",
                        "mudou", "INTERROMPIDO_MUDANCA_TERRITORIO",
                        "obito", "INTERROMPIDO_OBITO",
                        "obito-cadastro", "INTERROMPIDO_OBITO"));
        assertThat(pessoas).allSatisfy(e -> {
            assertThat(e.subjectKind()).isEqualTo(EvidenceSubjectKind.PERSON);
            assertThat(e.sourceRef()).isNull(); // só a personKey identifica: sem nome, CPF ou CNS
            assertThat(e.eventDate()).isEqualTo(CORTE.toString());
        });
        assertThat(pessoas)
                .filteredOn(e -> e.decision() == EvidenceDecision.EXCLUDED)
                .allSatisfy(e -> assertThat(e.points()).isNull());
        assertThat(pessoa(outcome, "elegivel").decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
        assertThat(pessoa(outcome, "elegivel").ine()).isEqualTo(INE);
        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.ONE);
    }

    @Test
    void equipes_resultadoPorIneDoVinculoEmOrdemCrescente() {
        crianca("a1", N);
        cumpreA("a1", N);
        crianca("a2", N);
        add(CanonicalFixtures.person("b1", N, "M"), CanonicalFixtures.registration("b1", N, "7654321", "0000000002"));

        List<TeamResult> equipes = calcular().teams();

        assertThat(equipes).extracting(TeamResult::ine).containsExactly(INE, "0000000002");
        assertThat(equipes.get(0).cnes()).isEqualTo(CNES);
        assertThat(equipes.get(0).result().numerator()).isEqualTo(VINTE);
        assertThat(equipes.get(0).result().denominator()).isEqualTo(BigInteger.TWO);
        assertThat(equipes.get(1).cnes()).isEqualTo("7654321");
        assertThat(equipes.get(1).result().denominator()).isEqualTo(BigInteger.ONE);
    }

    // ================================================================ Consolidação (MET-34, CT-71)

    @Test
    void consolidacao_elegivelQuandoHaCriancaCompletandoDoisAnosNaCompetencia() {
        crianca(CRIANCA, N);
        crianca("bebe", LocalDate.of(2025, 1, 10));

        RuleOutcome outcome = avaliar();

        assertThat(outcome.result().consolidationEligible()).isTrue();
        assertThat(outcome.teams())
                .singleElement()
                .satisfies(t -> assertThat(t.result().consolidationEligible()).isTrue());
    }

    @Test
    void consolidacao_naoElegivelQuandoNinguemCompletaDoisAnosNaCompetencia() {
        crianca("bebe", LocalDate.of(2025, 1, 10));

        RuleOutcome outcome = avaliar();

        assertThat(outcome.result().consolidationEligible()).isFalse();
        assertThat(outcome.teams())
                .allSatisfy(t -> assertThat(t.result().consolidationEligible()).isFalse());
    }

    @Test
    void ct_c2_71_mediaQuadrimestralSoNosMesesComCoorteDeclaradaNoDescritor() {
        assertThat(new C2Pack().descriptor().monthlyEligibility())
                .isEqualTo(MonthlyEligibility.MONTHS_WITH_COHORT_EVENT);
    }

    // ================================================================ Descritor e requirements

    @Test
    void descritor_praticasDeVintePontosLimitacoesPermanentesEPortoesIncompletos() {
        PackDescriptor descritor = new C2Pack().descriptor();

        assertThat(descritor.id()).isEqualTo("c2-desenvolvimento-infantil");
        assertThat(descritor.code()).isEqualTo("C2");
        assertThat(descritor.valueKind()).isEqualTo(ValueKind.SCORE);
        assertThat(descritor.components()).extracting(ComponentSpec::code).containsExactly("A", "B", "C", "D", "E");
        assertThat(descritor.components()).allSatisfy(c -> {
            assertThat(c.kind()).isEqualTo(ComponentKind.PRACTICE);
            assertThat(c.weight()).isEqualTo(VINTE);
        });
        assertThat(descritor.gates().isComplete()).isFalse();
        assertThat(descritor.executionEnabled()).isFalse();
        assertThat(descritor.blockedGates()).isNotEmpty();
        List<String> limitacoes = descritor.standingLimitations();
        assertThat(limitacoes).noneMatch(l -> l.contains("Regra em implementação"));
        assertThat(limitacoes).anyMatch(menciona("L1", "tipo de equipe"));
        assertThat(limitacoes).anyMatch(menciona("L7", "puericultura"));
        assertThat(limitacoes).anyMatch(menciona("RNDS", "RIA"));
        assertThat(limitacoes).anyMatch(l -> l.toLowerCase(Locale.ROOT).contains("cadsus")); // CT-67
        assertThat(limitacoes).anyMatch(menciona("AMB-C2-03"));
    }

    @Test
    void ct_c2_68_registrosForaDoPecLocalSaoLimitacaoDeclarada() {
        // Consultas e doses feitas em outro município (CT-68) só chegam pela RNDS/RIA.
        crianca(CRIANCA, N);
        assertThat(avaliar().result().limitations()).anyMatch(menciona("RNDS", "RIA"));
    }

    // CT-C2-69 (CPF/CNS inválido) e CT-C2-70 (digitação retroativa) dependem do CadSUS e do
    // reprocessamento de janelas, fora do pacote; omitidos.

    @Test
    void requirements_capacidadesCodigosFaixaDeNascimentoEJanelaEmMesesCivis() {
        C2Pack pack = new C2Pack();
        DataRequirements requisitos = pack.requirements(MARCO);

        assertThat(requisitos.canonicalSchemaVersion()).isEqualTo(DataRequirements.V2);
        List<String> capacidades =
                requisitos.parts().stream().map(PartRequirement::capability).toList();
        assertThat(capacidades)
                .containsExactlyInAnyOrderElementsOf(pack.descriptor().requiredCapabilities());
        assertThat(capacidades)
                .contains(
                        Capabilities.CITIZEN,
                        Capabilities.INDIVIDUAL_REGISTRATION,
                        Capabilities.CARE_ENCOUNTER,
                        Capabilities.PROCEDURE_PERFORMED,
                        Capabilities.HOME_VISIT,
                        Capabilities.MEASUREMENT_RECORD,
                        Capabilities.IMMUNIZATION_HISTORY);

        PartRequirement cidadao = parte(requisitos, Capabilities.CITIZEN);
        LocalDate nascidosDesde = cidadao.dateParams().get(PartRequirement.BIRTH_DATE_FROM);
        assertThat(nascidosDesde).isBeforeOrEqualTo(LocalDate.of(2024, 2, 29)).isAfter(LocalDate.of(2023, 3, 31));
        assertThat(cidadao.dateParams().get(PartRequirement.BIRTH_DATE_TO)).isEqualTo(CORTE);

        for (PartRequirement parte : requisitos.parts()) {
            assertThat(parte.periodStart().getDayOfMonth()).isEqualTo(1);
            assertThat(parte.periodStart()).isBeforeOrEqualTo(nascidosDesde);
            assertThat(parte.periodEndExclusive()).isEqualTo(LocalDate.of(2026, 4, 1));
        }

        assertThat(parte(requisitos, Capabilities.PROCEDURE_PERFORMED).arrayParams())
                .containsEntry(Capabilities.PROCEDURE_CODES, C2Codes.PROCEDURE_CODES);
        assertThat(C2Codes.PROCEDURE_CODES)
                .containsExactlyInAnyOrder(
                        "0101040024", "0101040083", "0101040075", "0301010269", "0301010277", "0301010250");
        assertThat(parte(requisitos, Capabilities.IMMUNIZATION_HISTORY).arrayParams())
                .containsEntry(Capabilities.IMMUNOBIOLOGICAL_CODES, C2Codes.IMMUNOBIOLOGICAL_CODES);
        assertThat(C2Codes.IMMUNOBIOLOGICAL_CODES)
                .containsExactlyInAnyOrder(
                        "09", "17", "22", "24", "26", "29", "39", "42", "43", "46", "47", "56", "58", "59", "106",
                        "107");
    }

    // ================================================================ montagem dos cenários

    private void crianca(String chave, LocalDate nascimento) {
        add(
                CanonicalFixtures.person(chave, nascimento, "F"),
                CanonicalFixtures.registration(chave, nascimento, CNES, INE));
    }

    private void add(Record... novos) {
        registros.addAll(List.of(novos));
    }

    private CanonicalDataset dados() {
        CanonicalDataset.Builder builder = CanonicalDataset.builder();
        registros.forEach(builder::add);
        return builder.build();
    }

    private RuleOutcome calcular() {
        return C2Pack.compute(dados(), CONTEXTO);
    }

    private RuleOutcome avaliar() {
        return new C2Pack().evaluate(dados(), CONTEXTO);
    }

    /** Uma criança nascida em {@link #N} por item, cumprindo as práticas listadas (ex.: "AD"). */
    private void criancasComPontos(String... praticasPorCrianca) {
        for (int i = 0; i < praticasPorCrianca.length; i++) {
            String chave = "crianca-" + i;
            crianca(chave, N);
            pontuar(chave, praticasPorCrianca[i]);
        }
    }

    private void pontuar(String chave, String praticas) {
        if (praticas.contains("A")) {
            cumpreA(chave, N);
        }
        if (praticas.contains("B")) {
            consultasPresenciais(chave, N, 9);
        }
        if (praticas.contains("C")) {
            paresMiai(chave, N, 9);
        }
        if (praticas.contains("D")) {
            cumpreD(chave, N);
        }
        if (praticas.contains("E")) {
            esquemaCompleto(chave, N);
        }
    }

    private void cumpreA(String chave, LocalDate nascimento) {
        add(presencial(chave, nascimento.plusDays(10)));
    }

    /** {@code quantidade} consultas presenciais de médico em N+40, N+80, … */
    private void consultasPresenciais(String chave, LocalDate nascimento, int quantidade) {
        for (int i = 1; i <= quantidade; i++) {
            add(presencial(chave, nascimento.plusDays(40L * i)));
        }
    }

    /** {@code quantidade} dias com peso e altura no MIAI (técnico de enfermagem) em N+43, N+83, … */
    private void paresMiai(String chave, LocalDate nascimento, int quantidade) {
        for (int i = 1; i <= quantidade; i++) {
            add(medidaMiai(chave, nascimento.plusDays(40L * i + 3), "8.4", "70.0"));
        }
    }

    private void cumpreD(String chave, LocalDate nascimento) {
        add(visita(chave, nascimento.plusDays(10)), visita(chave, nascimento.plusDays(100)));
    }

    /** CT-C2-46: penta e VIP em N+60/120/180, SCR aos 12 e 15 meses, VPC10 em N+60/120. */
    private void esquemaCompleto(String chave, LocalDate nascimento) {
        penta(chave, nascimento);
        vip(chave, nascimento);
        scr(chave, nascimento);
        pneumo(chave, nascimento);
    }

    private void penta(String chave, LocalDate nascimento) {
        doses(chave, "42", nascimento.plusDays(60), nascimento.plusDays(120), nascimento.plusDays(180));
    }

    private void vip(String chave, LocalDate nascimento) {
        doses(chave, "22", nascimento.plusDays(60), nascimento.plusDays(120), nascimento.plusDays(180));
    }

    private void scr(String chave, LocalDate nascimento) {
        doses(chave, "24", nascimento.plusMonths(12), nascimento.plusMonths(15));
    }

    private void pneumo(String chave, LocalDate nascimento) {
        doses(chave, "26", nascimento.plusDays(60), nascimento.plusDays(120));
    }

    private void doses(String chave, String codigo, LocalDate... datas) {
        for (LocalDate data : datas) {
            add(CanonicalFixtures.dose(chave, data, codigo, null));
        }
    }

    private static CanonicalCareEvent presencial(String chave, LocalDate data) {
        return atendimento(chave, data, MEDICO, false, "INDIVIDUAL", null);
    }

    private static CanonicalCareEvent remota(String chave, LocalDate data) {
        return atendimento(chave, data, ENFERMEIRA, true, "INDIVIDUAL", null);
    }

    private static CanonicalCareEvent domiciliar(String chave, LocalDate data) {
        return atendimento(chave, data, ENFERMEIRA, false, "INDIVIDUAL", "4");
    }

    private static CanonicalCareEvent atendimento(
            String chave, LocalDate data, String cbo, Boolean remoto, String forma, String local) {
        return careEvent(chave, data, cbo, remoto, forma, local, null, null);
    }

    private static CanonicalCareEvent medidaMiai(String chave, LocalDate data, String peso, String altura) {
        return careEvent(chave, data, TECNICO_ENFERMAGEM, false, "INDIVIDUAL", null, peso, altura);
    }

    private static CanonicalCareEvent careEvent(
            String chave,
            LocalDate data,
            String cbo,
            Boolean remoto,
            String forma,
            String local,
            String peso,
            String altura) {
        return new CanonicalCareEvent(
                CanonicalFixtures.ref("tb_fat_atendimento_individual"),
                IBGE,
                chave,
                data.toString(),
                forma,
                cbo,
                CNES,
                INE,
                null,
                local,
                remoto,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                peso,
                altura,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    private static CanonicalProcedureEvent procedimento(
            String chave, LocalDate data, String sigtap, String cbo, String origem) {
        return new CanonicalProcedureEvent(
                CanonicalFixtures.ref("tb_fat_proced_atend_proced"),
                IBGE,
                chave,
                data.toString(),
                sigtap,
                "PERFORMED",
                cbo,
                CNES,
                INE,
                origem);
    }

    private static CanonicalHomeVisit visita(String chave, LocalDate data) {
        return visita(chave, data, ACS, List.of("ACOMP_CRIANCA"), "1");
    }

    private static CanonicalHomeVisit visita(
            String chave, LocalDate data, String cbo, List<String> motivos, String desfecho) {
        return new CanonicalHomeVisit(
                CanonicalFixtures.ref("tb_fat_visita_domiciliar"),
                IBGE,
                chave,
                data.toString(),
                cbo,
                CNES,
                INE,
                desfecho,
                motivos,
                null,
                null);
    }

    private static CanonicalHomeVisit visitaComMedidas(String chave, LocalDate data, String peso, String altura) {
        return new CanonicalHomeVisit(
                CanonicalFixtures.ref("tb_fat_visita_domiciliar"),
                IBGE,
                chave,
                data.toString(),
                ACS,
                CNES,
                INE,
                "1",
                List.of("ACOMP_CRIANCA"),
                peso,
                altura);
    }

    private static CanonicalRegistration versao(
            String chave, LocalDate data, boolean recusa, boolean inativa, String motivoSaida) {
        return new CanonicalRegistration(
                CanonicalFixtures.ref("tb_fat_cad_individual"),
                IBGE,
                chave,
                data.toString(),
                CNES,
                INE,
                false,
                inativa,
                recusa,
                motivoSaida,
                null,
                null,
                null);
    }

    private static CanonicalTeam equipe(String tipo) {
        return new CanonicalTeam(CanonicalFixtures.ref("tb_dim_equipe"), IBGE, INE, CNES, tipo, CORTE.toString());
    }

    // ================================================================ asserções

    private static EvidenceItem pratica(RuleOutcome outcome, String chave, String pratica) {
        List<EvidenceItem> linhas = outcome.evidence().stream()
                .filter(e -> chave.equals(e.subjectKey())
                        && pratica.equals(e.component())
                        && DECISOES_DE_PRATICA.contains(e.decision()))
                .toList();
        assertThat(linhas).as("prática %s da criança %s", pratica, chave).hasSize(1);
        return linhas.get(0);
    }

    private static EvidenceItem pessoa(RuleOutcome outcome, String chave) {
        List<EvidenceItem> linhas = outcome.evidence().stream()
                .filter(e -> e.subjectKind() == EvidenceSubjectKind.PERSON
                        && chave.equals(e.subjectKey())
                        && (e.decision() == EvidenceDecision.ELIGIBLE || e.decision() == EvidenceDecision.EXCLUDED))
                .toList();
        assertThat(linhas).as("linha PERSON de %s", chave).hasSize(1);
        return linhas.get(0);
    }

    private static ResultComponent componente(IndicatorResult result, String codigo) {
        return result.components().stream()
                .filter(c -> c.code().equals(codigo))
                .findFirst()
                .orElseThrow();
    }

    private static void assertCumpre(RuleOutcome outcome, String chave, String pratica) {
        EvidenceItem linha = pratica(outcome, chave, pratica);
        assertThat(linha.decision()).as("prática %s de %s", pratica, chave).isEqualTo(EvidenceDecision.PRACTICE_MET);
        assertThat(linha.points()).isEqualTo(VINTE);
    }

    private static void assertNaoCumpre(RuleOutcome outcome, String chave, String pratica) {
        EvidenceItem linha = pratica(outcome, chave, pratica);
        assertThat(linha.decision())
                .as("prática %s de %s", pratica, chave)
                .isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
        assertThat(linha.points())
                .as("pontos da prática %s de %s", pratica, chave)
                .isEqualTo(BigInteger.ZERO);
    }

    private static void assertAmbigua(RuleOutcome outcome, String chave, String pratica, String amb) {
        EvidenceItem linha = pratica(outcome, chave, pratica);
        assertThat(linha.decision()).isEqualTo(EvidenceDecision.PRACTICE_AMBIGUOUS);
        assertThat(linha.points()).isNull();
        assertThat(linha.reasonCode()).startsWith("AMBIGUIDADE:").contains(amb);
        ResultComponent componente = componente(outcome.result(), pratica);
        assertThat(componente.status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
        assertThat(componente.value()).isNull();
        IndicatorResult result = outcome.result();
        assertThat(result.status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
        assertThat(result.valueText()).isNull();
        assertThat(result.valueExact()).isNull();
        assertThat(result.classification()).isNull();
    }

    /** A ambiguidade aparece antes do portão ({@code compute}) e sobrevive a ele ({@code evaluate}). */
    private void assertAmbiguaAntesEDepoisDoPortao(String chave, String pratica, String amb) {
        assertAmbigua(calcular(), chave, pratica, amb);
        assertAmbigua(avaliar(), chave, pratica, amb);
    }

    private static PartRequirement parte(DataRequirements requisitos, String capacidade) {
        return requisitos.parts().stream()
                .filter(p -> p.capability().equals(capacidade))
                .findFirst()
                .orElseThrow();
    }

    /** Uma limitação que cite qualquer um dos termos (sem diferenciar maiúsculas). */
    private static Predicate<String> menciona(String... termos) {
        return l -> {
            String texto = l.toLowerCase(Locale.ROOT);
            for (String termo : termos) {
                if (texto.contains(termo.toLowerCase(Locale.ROOT))) {
                    return true;
                }
            }
            return false;
        };
    }
}
