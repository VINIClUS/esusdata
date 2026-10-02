package esusdata.indicator.pack.c7;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalImmunization;
import esusdata.indicator.model.CanonicalPerson;
import esusdata.indicator.model.CanonicalProcedureEvent;
import esusdata.indicator.model.CanonicalRegistration;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ComponentKind;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.SourceRef;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.ValueKind;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * C7 written from the ficha transcription ({@code docs/metodologia/c7-prevencao-cancer.md}, CT01–CT07
 * and AMB-C7-*) and the agreed contract — Tech Spec MET-03/04/24/25/32 and ENG-25/27/36. Ages are
 * completed years on the last day of the competência (NEXT_DAY for 29/02), windows are civil months.
 */
class C7RuleTest {

    private static final String FEMININO = "FEMININO";
    private static final String MASCULINO = "MASCULINO";
    private static final String HOMEM_TRANS = "149";
    private static final String MULHER_TRANS = "150";
    private static final String MEDICO = "225125";
    private static final String ENFERMEIRO = "223505";
    private static final String TECNICO_ENFERMAGEM = "322205";
    private static final String INE_1 = "0000000001";
    private static final String INE_2 = "0000000002";
    private static final LocalDate LINK_DATE = LocalDate.of(2020, 1, 1);
    private static final YearMonth JUN_2026 = YearMonth.of(2026, 6);

    private static final String CITO_RASTREAMENTO = "0203010086";
    private static final String COLETA_CITO = "0201020033";
    private static final String COLETA_MOLECULAR = "0201020076";
    private static final String HPV_MOLECULAR = "0202100251";
    private static final String MAMOGRAFIA = "0204030030";
    private static final String MAMOGRAFIA_RASTREAMENTO = "0204030188";

    private static final String PRATICA_CUMPRIDA = "PRATICA_CUMPRIDA";
    private static final String PRATICA_NAO_CUMPRIDA = "PRATICA_NAO_CUMPRIDA";
    private static final String AMB_08 = "AMB_C7_08_HPV_MOLECULAR_ANTES_2026";
    private static final String AMB_06 = "AMB_C7_06_DOSE_HPV_ALEM_60_MESES";
    private static final String AMB_05 = "AMB_C7_05_HOMEM_TRANSGENERO_9_14";

    // ---- MET-24 / CT01: one denominator per subpopulation -> exactly 40 (Suficiente) ----
    @Test
    void met24_ct01_scoreBySubpopulationIsExactly40NotTheMeanOfPointsPerPerson() {
        IndicatorResult result = met24().compute(JUN_2026).result();

        assertThat(result.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(result.valueKind()).isEqualTo(ValueKind.COMPOSITE_SCORE);
        assertThat(result.numerator()).isNull();
        assertThat(result.denominator()).isNull();
        assertThat(result.components()).extracting(ResultComponent::code).containsExactly("A", "B", "C", "D");
        assertThat(result.components()).extracting(ResultComponent::kind).containsOnly(ComponentKind.SUBGROUP);
        assertThat(result.components())
                .extracting(ResultComponent::weight)
                .containsExactly(
                        BigInteger.valueOf(20), BigInteger.valueOf(30), BigInteger.valueOf(30), BigInteger.valueOf(20));
        assertComponent(result, "A", 1, 2, IndicatorStatus.COMPUTED);
        assertComponent(result, "B", 1, 4, IndicatorStatus.COMPUTED);
        assertComponent(result, "C", 3, 4, IndicatorStatus.COMPUTED);
        assertComponent(result, "D", 0, 2, IndicatorStatus.COMPUTED);
        assertThat(result.valueExact()).isEqualByComparingTo(ExactRatio.of(40, 1));
        assertThat(result.valueText()).isEqualTo("40.0000");
        assertThat(result.classification()).isEqualTo(Classification.SUFICIENTE);
        // negative control: a common denominator of 7 people averaging points would give 140/7 = 20
        assertThat(result.valueExact()).isNotEqualByComparingTo(ExactRatio.of(20, 1));
    }

    // ---- MET-25 / CT02: per-procedure windows in A ----
    @Test
    void met25_ct02a_cytologyOutside36MonthsDoesNotCount() {
        assertAOnly(JUN_2026, CITO_RASTREAMENTO, d("2023-01-15"), 0, IndicatorStatus.COMPUTED, PRATICA_NAO_CUMPRIDA);
    }

    @Test
    void met25_ct02b_hpvMolecularBefore2026AsOnlyEvidenceIsRuleAmbiguity() {
        RuleOutcome outcome =
                assertAOnly(JUN_2026, HPV_MOLECULAR, d("2023-01-15"), 0, IndicatorStatus.RULE_AMBIGUITY, AMB_08);

        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
        assertThat(outcome.result().valueExact()).isNull();
        assertThat(outcome.result().limitations()).anyMatch(l -> l.contains("AMB-C7-08"));
    }

    @Test
    void met25_ct02b_hpvMolecularBefore2026IsNotAmbiguousWhenAnotherExamAlreadyMeetsA() {
        IndicatorResult result = new Scenario()
                .woman("p", d("1986-01-10"))
                .add(exam("p", d("2023-01-15"), HPV_MOLECULAR), exam("p", d("2025-01-15"), CITO_RASTREAMENTO))
                .compute(JUN_2026)
                .result();

        assertComponent(result, "A", 1, 1, IndicatorStatus.COMPUTED);
    }

    @Test
    void met25_ct02c_molecularCollectionKeeps36MonthsNot60() {
        assertAOnly(JUN_2026, COLETA_MOLECULAR, d("2023-01-15"), 0, IndicatorStatus.COMPUTED, PRATICA_NAO_CUMPRIDA);
    }

    @Test
    void met25_ct02d_hpvMolecularOutside60MonthsDoesNotCount() {
        assertAOnly(JUN_2026, HPV_MOLECULAR, d("2021-01-15"), 0, IndicatorStatus.COMPUTED, PRATICA_NAO_CUMPRIDA);
    }

    @Test
    void met25_ct02e_hpvMolecularDoesNotCountBeforeCompetenciaJanuary2026() {
        assertAOnly(
                YearMonth.of(2025, 12),
                HPV_MOLECULAR,
                d("2024-01-15"),
                0,
                IndicatorStatus.COMPUTED,
                PRATICA_NAO_CUMPRIDA);
    }

    @Test
    void met25_hpvMolecularDatedFebruary2026CountsForCompetenciaJune2026() {
        assertAOnly(JUN_2026, HPV_MOLECULAR, d("2026-02-10"), 1, IndicatorStatus.COMPUTED, PRATICA_CUMPRIDA);
    }

    @Test
    void met25_hpvMolecularInside60ButOutside36MonthsCountsWhenDatedFrom2026() {
        // competência 2029-06: 36 months start 2026-07-01, 60 months start 2024-07-01
        assertAOnly(
                YearMonth.of(2029, 6), HPV_MOLECULAR, d("2026-02-10"), 1, IndicatorStatus.COMPUTED, PRATICA_CUMPRIDA);
    }

    // ---- CT06 / AMB-C7-01: empty subpopulation -> unavailable, never 70 nor renormalized ----
    @Test
    void ct06_emptySubgroupBMakesTheScoreUnavailableNever70() {
        IndicatorResult result = new Scenario()
                .woman("p1", d("1971-01-15"))
                .woman("p2", d("1966-01-15"))
                .woman("p4", d("2006-01-15"))
                .add(exam("p1", d("2024-10-15"), CITO_RASTREAMENTO), ssr("p1", d("2026-03-15"), "W11"))
                .add(ssr("p4", d("2026-01-15"), "X01"))
                .compute(JUN_2026)
                .result();

        assertComponent(result, "B", 0, 0, IndicatorStatus.NO_DENOMINATOR);
        assertComponent(result, "A", 1, 2, IndicatorStatus.COMPUTED);
        assertThat(result.status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
        assertThat(result.valueExact()).isNull();
        assertThat(result.valueText()).isNull();
        assertThat(result.classification()).isNull();
        assertThat(result.limitations()).anyMatch(l -> l.contains("AMB-C7-01"));
    }

    // ---- CT05: registered sex x gender identity (items 4.1/4.2) ----
    @Test
    void ct05_sexAndGenderIdentityCombinationsAsTheFichaEnumerates() {
        LocalDate thirty = d("1996-01-10");
        RuleOutcome outcome = new Scenario()
                .person("fem", thirty, FEMININO, null)
                .person("femTrans", thirty, FEMININO, MULHER_TRANS)
                .person("homemTrans", thirty, MASCULINO, HOMEM_TRANS)
                .person("masc", thirty, MASCULINO, null)
                .person("semSexo", thirty, null, null)
                .compute(JUN_2026);

        assertThat(personReasons(outcome))
                .containsEntry("fem", "ELEGIVEL_SEXO_FEMININO")
                .containsEntry("femTrans", "EXCLUIDO_MULHER_TRANSGENERO")
                .containsEntry("homemTrans", "ELEGIVEL_HOMEM_TRANSGENERO")
                .containsEntry("masc", "EXCLUIDO_SEXO_NAO_ELEGIVEL")
                .containsEntry("semSexo", "EXCLUIDO_SEXO_NAO_ELEGIVEL");
        assertThat(subgroups(outcome, "fem")).containsExactly("A", "C");
        assertThat(subgroups(outcome, "homemTrans")).containsExactly("A", "C");
        assertThat(subgroups(outcome, "femTrans")).isEmpty();
        assertComponent(outcome.result(), "A", 0, 2, IndicatorStatus.COMPUTED);
        assertComponent(outcome.result(), "C", 0, 2, IndicatorStatus.COMPUTED);
    }

    @Test
    void ct05_amb05_twelveYearOldTransManLeavesSubgroupBUndefined() {
        RuleOutcome outcome = new Scenario()
                .person("homemTrans", d("2014-01-10"), MASCULINO, HOMEM_TRANS)
                .woman("menina", d("2014-01-10"))
                .compute(JUN_2026);

        assertComponent(outcome.result(), "B", 0, 1, IndicatorStatus.RULE_AMBIGUITY);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
        assertThat(practiceRow(outcome, "homemTrans", "B"))
                .extracting(EvidenceItem::decision, EvidenceItem::reasonCode)
                .containsExactly(EvidenceDecision.EXCLUDED, AMB_05);
    }

    // ---- CT03: age boundaries, completed years on 2026-06-30, inclusive limits ----
    @Test
    void ct03_ageBoundariesDecideEachSubpopulation() {
        Map<String, LocalDate> births = new TreeMap<>();
        Map<String, Set<String>> expected = new TreeMap<>();
        boundary(births, expected, "age08", "2017-07-01");
        boundary(births, expected, "age09", "2017-06-30", "B");
        boundary(births, expected, "age14", "2011-07-01", "B", "C");
        boundary(births, expected, "age15", "2011-06-30", "C");
        boundary(births, expected, "age13", "2012-07-01", "B");
        boundary(births, expected, "age24", "2001-07-01", "C");
        boundary(births, expected, "age25", "2001-06-30", "A", "C");
        boundary(births, expected, "age64", "1961-07-01", "A", "C", "D");
        boundary(births, expected, "age65", "1961-06-30", "C", "D");
        boundary(births, expected, "age49", "1976-07-01", "A", "C");
        boundary(births, expected, "age50", "1976-06-30", "A", "C", "D");
        boundary(births, expected, "age69", "1956-07-01", "C", "D");
        boundary(births, expected, "age70", "1956-06-30");
        Scenario scenario = new Scenario();
        births.forEach(scenario::woman);

        RuleOutcome outcome = scenario.compute(JUN_2026);

        expected.forEach(
                (key, groups) -> assertThat(subgroups(outcome, key)).as(key).isEqualTo(groups));
        assertThat(personReasons(outcome))
                .containsEntry("age08", "EXCLUIDO_FORA_FAIXA_ETARIA")
                .containsEntry("age70", "EXCLUIDO_FORA_FAIXA_ETARIA");
        assertComponent(outcome.result(), "A", 0, 4, IndicatorStatus.COMPUTED);
        assertComponent(outcome.result(), "B", 0, 3, IndicatorStatus.COMPUTED);
        assertComponent(outcome.result(), "C", 0, 9, IndicatorStatus.COMPUTED);
        assertComponent(outcome.result(), "D", 0, 4, IndicatorStatus.COMPUTED);
    }

    @Test
    void ct03_hpvDoseCountsFromTheNinthBirthdayNotTheDayBefore() {
        // born 2013-03-10: 9 years on 2022-03-10, inside the 60 months of 2026-06 (from 2021-07-01)
        RuleOutcome outcome = new Scenario()
                .woman("dose8", d("2013-03-10"))
                .woman("dose9", d("2013-03-10"))
                .add(hpv("dose8", d("2022-03-09")), hpv("dose9", d("2022-03-10")))
                .compute(JUN_2026);

        assertComponent(outcome.result(), "B", 1, 2, IndicatorStatus.COMPUTED);
        assertThat(practiceRow(outcome, "dose8", "B").decision()).isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
        assertThat(practiceRow(outcome, "dose9", "B").decision()).isEqualTo(EvidenceDecision.PRACTICE_MET);
    }

    @Test
    void ct03_hpvDoseAtFifteenDoesNotCountWhileAtFourteenItDoes() {
        RuleOutcome outcome = new Scenario()
                .woman("age14", d("2011-07-01"))
                .woman("age15", d("2011-06-30"))
                .add(hpv("age14", d("2026-06-30")), hpv("age15", d("2026-06-30")))
                .compute(JUN_2026);

        assertComponent(outcome.result(), "B", 1, 1, IndicatorStatus.COMPUTED);
        assertThat(subgroups(outcome, "age15")).containsExactly("C");
        assertThat(supportingRefs(outcome, "age15", "B")).isEmpty();
    }

    @Test
    void ct03_amb06_hpvDoseOlderThan60MonthsIsRuleAmbiguity() {
        // born 2012-03-10, dose at 9 on 2021-03-10; the 60 months of 2026-06 start on 2021-07-01
        RuleOutcome outcome = new Scenario()
                .woman("p", d("2012-03-10"))
                .add(hpv("p", d("2021-03-10")))
                .compute(JUN_2026);

        assertComponent(outcome.result(), "B", 0, 1, IndicatorStatus.RULE_AMBIGUITY);
        assertThat(practiceRow(outcome, "p", "B").reasonCode()).isEqualTo(AMB_06);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
    }

    // ---- CT04: civil-month windows, last day out / first day in, across 29/02 ----
    @Test
    void ct04_window12MonthsForC() {
        assertWindowBoundary(
                YearMonth.of(2028, 2),
                d("1997-06-15"),
                "C",
                (key, date) -> ssr(key, date, "W11"),
                d("2027-02-28"),
                d("2027-03-01"));
    }

    @Test
    void ct04_window24MonthsForD() {
        assertWindowBoundary(
                YearMonth.of(2025, 2),
                d("1970-01-10"),
                "D",
                (key, date) -> exam(key, date, MAMOGRAFIA_RASTREAMENTO),
                d("2023-02-28"),
                d("2023-03-01"));
    }

    @Test
    void ct04_window36MonthsForA() {
        assertWindowBoundary(
                JUN_2026,
                d("1986-01-10"),
                "A",
                (key, date) -> exam(key, date, CITO_RASTREAMENTO),
                d("2023-06-30"),
                d("2023-07-01"));
    }

    @Test
    void ct04_window60MonthsForHpvMolecularAcross2028Leap() {
        // competência 2031-02: window [2026-03-01, 2031-02-28], both dates after 2026-01-01
        assertWindowBoundary(
                YearMonth.of(2031, 2),
                d("1991-01-10"),
                "A",
                (key, date) -> exam(key, date, HPV_MOLECULAR),
                d("2026-02-28"),
                d("2026-03-01"));
    }

    @Test
    void ct04_window60MonthsAtJanuary2026FirstDayInIsAmb08() {
        RuleOutcome outcome = new Scenario()
                .woman("out", d("1986-01-10"))
                .woman("in", d("1986-01-10"))
                .add(exam("out", d("2021-01-31"), HPV_MOLECULAR), exam("in", d("2021-02-01"), HPV_MOLECULAR))
                .compute(YearMonth.of(2026, 1));

        assertThat(practiceRow(outcome, "out", "A").reasonCode()).isEqualTo(PRATICA_NAO_CUMPRIDA);
        assertThat(practiceRow(outcome, "in", "A").reasonCode()).isEqualTo(AMB_08);
        assertComponent(outcome.result(), "A", 0, 2, IndicatorStatus.RULE_AMBIGUITY);
    }

    // ---- ENG-25 / CT07: exact band edges, no rounding before classifying ----
    @Test
    void eng25_bandEdgesAreExact() {
        C7Pack pack = new C7Pack();
        long million = 1_000_000;
        assertThat(pack.classify(ExactRatio.of(25, 1))).contains(Classification.REGULAR);
        assertThat(pack.classify(ExactRatio.of(25 * million - 1, million))).contains(Classification.REGULAR);
        assertThat(pack.classify(ExactRatio.of(25 * million + 1, million))).contains(Classification.SUFICIENTE);
        assertThat(pack.classify(ExactRatio.of(50, 1))).contains(Classification.SUFICIENTE);
        assertThat(pack.classify(ExactRatio.of(50 * million - 1, million))).contains(Classification.SUFICIENTE);
        assertThat(pack.classify(ExactRatio.of(50 * million + 1, million))).contains(Classification.BOM);
        assertThat(pack.classify(ExactRatio.of(75, 1))).contains(Classification.BOM);
        assertThat(pack.classify(ExactRatio.of(75 * million - 1, million))).contains(Classification.BOM);
        assertThat(pack.classify(ExactRatio.of(75 * million + 1, million))).contains(Classification.OTIMO);
        assertThat(pack.classify(ExactRatio.of(40, 1))).contains(Classification.SUFICIENTE);
        assertThat(pack.classify(ExactRatio.of(100, 1))).contains(Classification.OTIMO);
        assertThat(pack.classify(ExactRatio.of(100 * million + 1, million))).isEmpty();
    }

    // ---- ENG-27: born 29/02 completes years on 01/03 in common years (NEXT_DAY) ----
    @Test
    void eng27_bornOnLeapDayIsEightOnFebruary28AndNineInMarch() {
        Scenario scenario = new Scenario().woman("leap", d("2012-02-29")).add(hpv("leap", d("2021-02-28")));

        RuleOutcome february = scenario.compute(YearMonth.of(2021, 2));
        assertThat(personReasons(february)).containsEntry("leap", "EXCLUIDO_FORA_FAIXA_ETARIA");
        assertThat(february.result().status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);

        RuleOutcome march = scenario.compute(YearMonth.of(2021, 3));
        assertThat(personReasons(march)).containsEntry("leap", "ELEGIVEL_SEXO_FEMININO");
        // the dose of 2021-02-28 was given at 8 years: B is not met
        assertComponent(march.result(), "B", 0, 1, IndicatorStatus.COMPUTED);
    }

    // ---- MET-03: eligible people, no practice -> a real 0 (Regular) ----
    @Test
    void met03_eligiblePeopleWithoutPracticeAreARealZero() {
        IndicatorResult result = new Scenario()
                .woman("girl", d("2014-01-10"))
                .woman("adult", d("1996-01-10"))
                .woman("older", d("1971-01-10"))
                .compute(JUN_2026)
                .result();

        assertThat(result.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(result.valueExact()).isEqualByComparingTo(ExactRatio.zero());
        assertThat(result.valueText()).isEqualTo("0.0000");
        assertThat(result.classification()).isEqualTo(Classification.REGULAR);
        assertComponent(result, "A", 0, 2, IndicatorStatus.COMPUTED);
        assertComponent(result, "B", 0, 1, IndicatorStatus.COMPUTED);
        assertComponent(result, "C", 0, 2, IndicatorStatus.COMPUTED);
        assertComponent(result, "D", 0, 1, IndicatorStatus.COMPUTED);
    }

    // ---- MET-04: nobody eligible -> NO_DENOMINATOR, never 0 ----
    @Test
    void met04_nobodyEligibleIsNoDenominatorWithNullValue() {
        IndicatorResult result = new Scenario()
                .person("man", d("1996-01-10"), MASCULINO, null)
                .woman("old", d("1950-01-10"))
                .woman("child", d("2020-01-10"))
                .compute(JUN_2026)
                .result();

        assertThat(result.status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
        assertThat(result.valueExact()).isNull();
        assertThat(result.valueText()).isNull();
        assertThat(result.classification()).isNull();
        for (String code : List.of("A", "B", "C", "D")) {
            assertComponent(result, code, 0, 0, IndicatorStatus.NO_DENOMINATOR);
        }
    }

    // ---- MET-32: duplicated records and repeated practices count the person once ----
    @Test
    void met32_duplicatesCountThePersonOnceAndSupportingEventsAreDistinct() {
        CanonicalProcedureEvent cytology = exam("adult", d("2025-01-15"), CITO_RASTREAMENTO);
        RuleOutcome outcome = new Scenario()
                .woman("adult", d("1971-01-10"))
                .woman("girl", d("2014-01-10"))
                .add(cytology, cytology)
                .add(ssr("adult", d("2026-02-10"), "W11"), ssr("adult", d("2026-05-10"), "X01"))
                .add(exam("adult", d("2025-10-01"), MAMOGRAFIA))
                .add(hpv("girl", d("2024-02-01")), CanonicalFixtures.dose("girl", d("2025-02-01"), "93", "1"))
                .compute(JUN_2026);

        IndicatorResult result = outcome.result();
        assertComponent(result, "A", 1, 1, IndicatorStatus.COMPUTED);
        assertComponent(result, "B", 1, 1, IndicatorStatus.COMPUTED);
        assertComponent(result, "C", 1, 1, IndicatorStatus.COMPUTED);
        assertComponent(result, "D", 1, 1, IndicatorStatus.COMPUTED);
        assertThat(result.valueExact()).isEqualByComparingTo(ExactRatio.of(100, 1));
        assertThat(result.classification()).isEqualTo(Classification.OTIMO);
        assertThat(supportingRefs(outcome, "adult", "A")).containsExactly(cytology.sourceRef());
        assertThat(supportingRefs(outcome, "adult", "C")).hasSize(2);
        assertThat(supportingRefs(outcome, "girl", "B")).hasSize(2);
        List<EvidenceItem> supporting = rows(outcome, e -> e.decision() == EvidenceDecision.SUPPORTING_EVENT);
        assertThat(supporting)
                .extracting(e -> e.subjectKey() + "|" + e.component() + "|" + e.sourceRef())
                .doesNotHaveDuplicates();
    }

    // ---- ENG-36: evidence rebuilds the population, with reason codes and no identifiers ----
    @Test
    void eng36_evidenceRebuildsThePopulationAndOnlyCarriesOpaqueKeys() {
        LocalDate thirty = d("1996-01-10");
        Scenario scenario = new Scenario()
                .woman("adult", thirty)
                .woman("girl", d("2014-01-10"))
                .woman("older", d("1971-01-10"))
                .add(exam("older", d("2025-10-01"), MAMOGRAFIA))
                .person("man", thirty, MASCULINO, null)
                .person("transWoman", thirty, FEMININO, MULHER_TRANS)
                .person("aged", d("1950-01-10"), FEMININO, null)
                .woman("moved", thirty)
                .add(registrationVersion("moved", d("2025-01-01"), INE_1, "136", false))
                .woman("inactive", thirty)
                .add(registrationVersion("inactive", d("2025-01-01"), INE_1, null, true))
                .woman("conflict", thirty)
                .add(registrationVersion("conflict", d("2025-01-01"), INE_1, null, false))
                .add(registrationVersion("conflict", d("2025-01-01"), INE_2, null, false))
                .add(new CanonicalPerson(
                        CanonicalFixtures.ref("tb_fat_cad_individual"),
                        CanonicalFixtures.IBGE,
                        "dead",
                        thirty.toString(),
                        FEMININO,
                        null,
                        "2026-05-01"))
                .add(CanonicalFixtures.registration("dead", LINK_DATE, cnes(INE_1), INE_1))
                .add(CanonicalFixtures.person("unlinked", thirty, FEMININO))
                .add(CanonicalFixtures.person("linkedLater", thirty, FEMININO))
                .add(CanonicalFixtures.registration("linkedLater", d("2026-07-01"), cnes(INE_1), INE_1));

        RuleOutcome outcome = scenario.compute(JUN_2026);

        Map<String, String> reasons = personReasons(outcome);
        assertThat(reasons)
                .containsEntry("adult", "ELEGIVEL_SEXO_FEMININO")
                .containsEntry("girl", "ELEGIVEL_SEXO_FEMININO")
                .containsEntry("older", "ELEGIVEL_SEXO_FEMININO")
                .containsEntry("man", "EXCLUIDO_SEXO_NAO_ELEGIVEL")
                .containsEntry("transWoman", "EXCLUIDO_MULHER_TRANSGENERO")
                .containsEntry("aged", "EXCLUIDO_FORA_FAIXA_ETARIA")
                .containsEntry("moved", "EXCLUIDO_SAIDA_TERRITORIO")
                .containsEntry("inactive", "EXCLUIDO_CADASTRO_INATIVO")
                .containsEntry("conflict", "EXCLUIDO_VINCULO_CONFLITANTE")
                .containsEntry("dead", "EXCLUIDO_OBITO")
                .containsEntry("unlinked", "EXCLUIDO_SEM_VINCULO")
                .containsEntry("linkedLater", "EXCLUIDO_SEM_VINCULO")
                .hasSize(12);
        assertThat(rows(outcome, e -> e.component() == null && e.decision() == EvidenceDecision.ELIGIBLE))
                .hasSize(3);
        for (ResultComponent component : outcome.result().components()) {
            assertThat(BigInteger.valueOf(
                            practiceSubjects(outcome, component.code()).size()))
                    .as("people decided in " + component.code())
                    .isEqualTo(component.denominator());
        }
        assertThat(outcome.evidence()).allSatisfy(e -> {
            assertThat(e.subjectKind()).isEqualTo(EvidenceSubjectKind.PERSON);
            assertThat(reasons).containsKey(e.subjectKey());
            assertThat(e.points()).isNull();
            assertThat(e.reasonCode()).isNotBlank();
            if (e.decision() != EvidenceDecision.SUPPORTING_EVENT) {
                assertThat(e.sourceRef()).isNull();
            }
        });
    }

    // ---- Release gates: computed counts survive, the value does not ----
    @Test
    void gate_packEvaluateBlocksTheValueButKeepsComponentsAndTeams() {
        CanonicalDataset data = met24().build();
        EvaluationContext context = context(JUN_2026);
        RuleOutcome ungated = C7Rule.compute(data, context);

        RuleOutcome gated = new C7Pack().evaluate(data, context);

        IndicatorResult result = gated.result();
        assertThat(result.status()).isEqualTo(IndicatorStatus.BLOCKED);
        assertThat(result.valueText()).isNull();
        assertThat(result.valueExact()).isNull();
        assertThat(result.classification()).isNull();
        assertThat(result.components()).isEqualTo(ungated.result().components());
        assertComponent(result, "C", 3, 4, IndicatorStatus.COMPUTED);
        assertThat(result.limitations()).contains("Portão A (fonte e vigência) incompleto");
        assertThat(gated.teams()).extracting(TeamResult::ine).containsExactly(INE_1);
        assertThat(gated.teams()).allSatisfy(t -> {
            assertThat(t.result().status()).isEqualTo(IndicatorStatus.BLOCKED);
            assertThat(t.result().valueExact()).isNull();
        });
    }

    // ---- Teams: each INE has its own subpopulations ----
    @Test
    void teams_eachIneIsComputedSeparately() {
        Scenario scenario = met24().linked(CanonicalFixtures.person("t2girl", d("2014-01-10"), FEMININO), INE_2)
                .linked(CanonicalFixtures.person("t2adult", d("1996-01-10"), FEMININO), INE_2)
                .linked(CanonicalFixtures.person("t2older", d("1971-01-10"), FEMININO), INE_2)
                .add(hpv("t2girl", d("2024-01-15")), ssr("t2adult", d("2026-05-10"), "W11"))
                .add(exam("t2older", d("2025-12-01"), MAMOGRAFIA));

        RuleOutcome outcome = scenario.compute(JUN_2026);

        assertThat(outcome.teams()).extracting(TeamResult::ine).containsExactly(INE_1, INE_2);
        IndicatorResult first = outcome.teams().get(0).result();
        IndicatorResult second = outcome.teams().get(1).result();
        assertThat(first.valueExact()).isEqualByComparingTo(ExactRatio.of(40, 1));
        assertComponent(second, "A", 0, 2, IndicatorStatus.COMPUTED);
        assertComponent(second, "B", 1, 1, IndicatorStatus.COMPUTED);
        assertComponent(second, "C", 1, 2, IndicatorStatus.COMPUTED);
        assertComponent(second, "D", 1, 1, IndicatorStatus.COMPUTED);
        assertThat(second.valueExact()).isEqualByComparingTo(ExactRatio.of(65, 1));
        assertThat(second.classification()).isEqualTo(Classification.BOM);
        // municipality: A 1/4, B 2/5, C 4/6, D 1/3 -> 5 + 12 + 20 + 20/3 = 131/3
        assertThat(outcome.result().valueExact()).isEqualByComparingTo(ExactRatio.of(131, 3));
    }

    // ---- Scope: a record of another municipality is rejected before counting ----
    @Test
    void recordFromAnotherMunicipalityIsRejected() {
        CanonicalDataset data = new Scenario()
                .woman("p", d("1996-01-10"))
                .add(new CanonicalPerson(
                        CanonicalFixtures.ref("tb_fat_cad_individual"),
                        "3550308",
                        "other",
                        "1996-01-10",
                        FEMININO,
                        null,
                        null))
                .build();

        assertThatThrownBy(() -> C7Rule.compute(data, context(JUN_2026))).isInstanceOf(IllegalArgumentException.class);
    }

    // ---- CBO and code lists (Quadros 02/04, item 24 g, AMB-C7-11) ----
    @Test
    void cbo_onlyDoctorsAndNursesSatisfyAAndC() {
        LocalDate thirty = d("1996-01-10");
        RuleOutcome outcome = new Scenario()
                .woman("tecnico", thirty)
                .woman("medico", thirty)
                .woman("tecnicoC", thirty)
                .add(exam("tecnico", d("2025-01-15"), COLETA_CITO, TECNICO_ENFERMAGEM))
                .add(exam("medico", d("2025-01-15"), COLETA_CITO, MEDICO))
                .add(CanonicalFixtures.encounterWithProblems(
                        "tecnicoC", d("2026-05-10"), TECNICO_ENFERMAGEM, List.of("W11"), List.of()))
                .compute(JUN_2026);

        assertThat(practiceRow(outcome, "tecnico", "A").decision()).isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
        assertThat(practiceRow(outcome, "medico", "A").decision()).isEqualTo(EvidenceDecision.PRACTICE_MET);
        assertThat(practiceRow(outcome, "tecnicoC", "C").decision()).isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
        assertComponent(outcome.result(), "A", 1, 3, IndicatorStatus.COMPUTED);
    }

    @Test
    void codes_cExactListMatchingIgnoresDotsAndRejectsUnlistedCodes() {
        LocalDate thirty = d("1996-01-10");
        LocalDate may = d("2026-05-10");
        RuleOutcome outcome = new Scenario()
                .woman("a01", thirty)
                .woman("n800", thirty)
                .woman("z309", thirty)
                .woman("z39", thirty)
                .add(ssr("a01", may, "A01"), ssrCid("n800", may, "N80.0"))
                .add(ssrCid("z309", may, "Z309"), ssrCid("z39", may, "Z39"))
                .compute(JUN_2026);

        assertThat(practiceRow(outcome, "a01", "C").decision()).isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
        assertThat(practiceRow(outcome, "n800", "C").decision()).isEqualTo(EvidenceDecision.PRACTICE_MET);
        assertThat(practiceRow(outcome, "z309", "C").decision()).isEqualTo(EvidenceDecision.PRACTICE_MET);
        assertThat(practiceRow(outcome, "z39", "C").decision()).isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
        assertComponent(outcome.result(), "C", 2, 4, IndicatorStatus.COMPUTED);
    }

    // ---- scenarios ----

    /** CT01: P1–P7, all women of team 1, competência 2026-06. */
    private static Scenario met24() {
        return new Scenario()
                .woman("p1", d("1971-01-15"))
                .woman("p2", d("1966-01-15"))
                .woman("p3", d("2012-01-15"))
                .woman("p4", d("2006-01-15"))
                .woman("p5", d("2017-01-15"))
                .woman("p6", d("2015-01-15"))
                .woman("p7", d("2013-01-15"))
                .add(exam("p1", d("2024-10-15"), CITO_RASTREAMENTO), ssr("p1", d("2026-03-15"), "W11"))
                .add(ssrCid("p3", d("2026-04-15"), "Z300"), ssr("p4", d("2026-01-15"), "X01"))
                .add(hpv("p5", d("2026-02-01")));
    }

    /** One 40-year-old woman whose only evidence for A is {@code code} on {@code date}. */
    private static RuleOutcome assertAOnly(
            YearMonth competencia, String code, LocalDate date, long met, IndicatorStatus status, String reason) {
        LocalDate birth = competencia.atEndOfMonth().minusYears(40).withDayOfYear(10);
        RuleOutcome outcome =
                new Scenario().woman("p", birth).add(exam("p", date, code)).compute(competencia);
        assertComponent(outcome.result(), "A", met, 1, status);
        assertThat(practiceRow(outcome, "p", "A").reasonCode()).isEqualTo(reason);
        return outcome;
    }

    private static void assertWindowBoundary(
            YearMonth competencia,
            LocalDate birth,
            String component,
            BiFunction<String, LocalDate, Record> event,
            LocalDate lastDayOut,
            LocalDate firstDayIn) {
        RuleOutcome outcome = new Scenario()
                .woman("out", birth)
                .woman("in", birth)
                .add(event.apply("out", lastDayOut), event.apply("in", firstDayIn))
                .compute(competencia);

        assertThat(practiceRow(outcome, "out", component).decision()).isEqualTo(EvidenceDecision.PRACTICE_NOT_MET);
        assertThat(practiceRow(outcome, "in", component).decision()).isEqualTo(EvidenceDecision.PRACTICE_MET);
        assertComponent(outcome.result(), component, 1, 2, IndicatorStatus.COMPUTED);
    }

    private static void boundary(
            Map<String, LocalDate> births,
            Map<String, Set<String>> expected,
            String key,
            String birth,
            String... groups) {
        births.put(key, d(birth));
        expected.put(key, new TreeSet<>(List.of(groups)));
    }

    // ---- records ----

    private static LocalDate d(String iso) {
        return LocalDate.parse(iso);
    }

    private static EvaluationContext context(YearMonth competencia) {
        return EvaluationContext.endOfMonth(CanonicalFixtures.IBGE, competencia);
    }

    private static String cnes(String ine) {
        return INE_1.equals(ine) ? "2000001" : "2000002";
    }

    private static CanonicalProcedureEvent exam(String key, LocalDate date, String code) {
        return exam(key, date, code, MEDICO);
    }

    private static CanonicalProcedureEvent exam(String key, LocalDate date, String code, String cbo) {
        return CanonicalFixtures.procedure(key, date, code, "PERFORMED", cbo);
    }

    private static CanonicalCareEvent ssr(String key, LocalDate date, String ciap) {
        return CanonicalFixtures.encounterWithProblems(key, date, ENFERMEIRO, List.of(ciap), List.of());
    }

    private static CanonicalCareEvent ssrCid(String key, LocalDate date, String cid) {
        return CanonicalFixtures.encounterWithProblems(key, date, MEDICO, List.of(), List.of(cid));
    }

    private static CanonicalImmunization hpv(String key, LocalDate date) {
        return CanonicalFixtures.dose(key, date, "67", "1");
    }

    private static CanonicalRegistration registrationVersion(
            String key, LocalDate date, String ine, String exitReason, boolean inactive) {
        return new CanonicalRegistration(
                CanonicalFixtures.ref("tb_fat_cad_individual"),
                CanonicalFixtures.IBGE,
                key,
                date.toString(),
                cnes(ine),
                ine,
                false,
                inactive,
                false,
                exitReason,
                null,
                null,
                null);
    }

    // ---- result and evidence readers ----

    private static ResultComponent component(IndicatorResult result, String code) {
        return result.components().stream()
                .filter(c -> c.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no component " + code));
    }

    private static void assertComponent(
            IndicatorResult result, String code, long numerator, long denominator, IndicatorStatus status) {
        ResultComponent c = component(result, code);
        assertThat(c.numerator()).as(code + " numerator").isEqualTo(BigInteger.valueOf(numerator));
        assertThat(c.denominator()).as(code + " denominator").isEqualTo(BigInteger.valueOf(denominator));
        assertThat(c.status()).as(code + " status").isEqualTo(status);
        if (status == IndicatorStatus.COMPUTED) {
            assertThat(c.value()).as(code + " value").isEqualByComparingTo(ExactRatio.of(numerator, denominator));
        } else {
            assertThat(c.value()).as(code + " value").isNull();
        }
    }

    private static List<EvidenceItem> rows(RuleOutcome outcome, Predicate<EvidenceItem> filter) {
        return outcome.evidence().stream().filter(filter).toList();
    }

    /** The person-level decision (component null) of every person, by key. */
    private static Map<String, String> personReasons(RuleOutcome outcome) {
        List<EvidenceItem> personRows = rows(outcome, e -> e.component() == null);
        assertThat(personRows).extracting(EvidenceItem::subjectKey).doesNotHaveDuplicates();
        return personRows.stream()
                .collect(Collectors.toMap(
                        EvidenceItem::subjectKey, EvidenceItem::reasonCode, (a, b) -> a, TreeMap::new));
    }

    /** The single practice decision of {@code key} in {@code component} (met, not met or AMB-C7-05 exclusion). */
    private static EvidenceItem practiceRow(RuleOutcome outcome, String key, String component) {
        List<EvidenceItem> found = rows(
                outcome,
                e -> key.equals(e.subjectKey())
                        && component.equals(e.component())
                        && e.decision() != EvidenceDecision.SUPPORTING_EVENT);
        assertThat(found).as(key + " in " + component).hasSize(1);
        return found.get(0);
    }

    /** Subgroups where {@code key} was decided as met or not met (i.e. counted in the denominator). */
    private static Set<String> subgroups(RuleOutcome outcome, String key) {
        return rows(outcome, e -> key.equals(e.subjectKey()) && isPractice(e)).stream()
                .map(EvidenceItem::component)
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static Set<String> practiceSubjects(RuleOutcome outcome, String component) {
        return rows(outcome, e -> component.equals(e.component()) && isPractice(e)).stream()
                .map(EvidenceItem::subjectKey)
                .collect(Collectors.toSet());
    }

    private static List<SourceRef> supportingRefs(RuleOutcome outcome, String key, String component) {
        return rows(
                        outcome,
                        e -> key.equals(e.subjectKey())
                                && component.equals(e.component())
                                && e.decision() == EvidenceDecision.SUPPORTING_EVENT)
                .stream()
                .map(EvidenceItem::sourceRef)
                .toList();
    }

    private static boolean isPractice(EvidenceItem e) {
        return e.decision() == EvidenceDecision.PRACTICE_MET || e.decision() == EvidenceDecision.PRACTICE_NOT_MET;
    }

    /** Linked people (every person gets a registration version on 2020-01-01) plus their records. */
    private static final class Scenario {
        private final CanonicalDataset.Builder builder = CanonicalDataset.builder();

        Scenario woman(String key, LocalDate birth) {
            return person(key, birth, FEMININO, null);
        }

        Scenario person(String key, LocalDate birth, String sex, String genderIdentity) {
            return linked(CanonicalFixtures.person(key, birth, sex, genderIdentity), INE_1);
        }

        Scenario linked(CanonicalPerson person, String ine) {
            builder.add(person);
            builder.add(CanonicalFixtures.registration(person.personKey(), LINK_DATE, cnes(ine), ine));
            return this;
        }

        Scenario add(Record... records) {
            for (Record r : records) {
                builder.add(Objects.requireNonNull(r));
            }
            return this;
        }

        CanonicalDataset build() {
            return builder.build();
        }

        RuleOutcome compute(YearMonth competencia) {
            return C7Rule.compute(build(), context(competencia));
        }
    }
}
