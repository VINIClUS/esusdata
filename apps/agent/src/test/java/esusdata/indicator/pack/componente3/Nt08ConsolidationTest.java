package esusdata.indicator.pack.componente3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.GateFixtures;
import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.IndicatorRule;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.pack.componente3.ComponentIIIInput.Monthly;
import esusdata.indicator.pack.componente3.ComponentIIIInput.Unit;
import esusdata.indicator.pack.componente3.ComponentIIIResult.IndicatorQuadrimestral;
import esusdata.indicator.pack.componente3.ComponentIIIResult.UnitResult;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * NT 8/2026 consolidation of C1–C7 into the Nota Final do Componente III, per the "Casos de teste
 * derivados" of {@code docs/metodologia/componente-iii-nt08-2026.md} (MET-05, MET-17 análogo,
 * MET-33…MET-38) plus §4.3 ENG-25 boundary coverage through the whole consolidation.
 */
class Nt08ConsolidationTest {

    private static final String C1 = "c1-mais-acesso";
    private static final String C2 = "c2-desenvolvimento-infantil";
    private static final String C3 = "c3-gestacao-puerperio";
    private static final String C4 = "c4-cuidado-diabetes";
    private static final String C5 = "c5-cuidado-hipertensao";
    private static final String C6 = "c6-cuidado-pessoa-idosa";
    private static final String C7 = "c7-prevencao-cancer";
    private static final List<String> PACKS = List.of(C1, C2, C3, C4, C5, C6, C7);
    private static final List<Long> WEIGHTS = List.of(1L, 2L, 2L, 1L, 1L, 1L, 2L);

    private static final String IBGE = "3541307";
    private static final String INE = "0000000001";
    private static final String CNES = "1234567";
    private static final long MILLION = 1_000_000;

    /** From 2027 on the financial classification equals the methodological one (§ 6º). */
    private static final Quadrimestre Q1_2027 = Quadrimestre.parse("2027-Q1");

    private static final Map<String, IndicatorRule> RULES = IndicatorRuleRegistry.all().stream()
            .collect(Collectors.toMap(r -> r.descriptor().id(), Function.identity()));

    private final ComponentIIIConsolidation consolidation = new Nt08Consolidation();

    // ======================================================================================
    // Fixtures
    // ======================================================================================

    private static ExactRatio pct(long whole) {
        return ExactRatio.of(whole, 1);
    }

    private static ExactRatio above(long bound) {
        return ExactRatio.of(bound * MILLION + 1, MILLION);
    }

    private static ExactRatio below(long bound) {
        return ExactRatio.of(bound * MILLION - 1, MILLION);
    }

    /** A monthly value inside the pack's band for {@code c} (C1's scale is non-monotonic). */
    private static ExactRatio valueFor(String pack, Classification c) {
        boolean c1 = C1.equals(pack);
        return switch (c) {
            case OTIMO -> pct(c1 ? 60 : 90);
            case BOM -> pct(c1 ? 40 : 60);
            case SUFICIENTE -> pct(c1 ? 20 : 40);
            case REGULAR -> pct(c1 ? 5 : 10);
        };
    }

    private static Monthly monthly(
            String pack, YearMonth month, IndicatorStatus status, ExactRatio value, boolean eligible) {
        return new Monthly(pack, month, pack + "@" + month, status, value, eligible);
    }

    private static Monthly computed(String pack, YearMonth month, ExactRatio value) {
        return monthly(pack, month, IndicatorStatus.COMPUTED, value, true);
    }

    /** One unit's monthly results; every indicator starts as four Ótimo months. */
    private static final class UnitBuilder {
        private final Quadrimestre quadrimestre;
        private final Map<String, List<Monthly>> byPack = new LinkedHashMap<>();
        private final List<Monthly> extra = new ArrayList<>();

        UnitBuilder(Quadrimestre quadrimestre) {
            this.quadrimestre = quadrimestre;
            PACKS.forEach(p -> concept(p, Classification.OTIMO));
        }

        YearMonth month(int index) {
            return quadrimestre.months().get(index);
        }

        UnitBuilder concept(String pack, Classification c) {
            return constant(pack, valueFor(pack, c));
        }

        UnitBuilder constant(String pack, ExactRatio value) {
            return values(pack, value, value, value, value);
        }

        /** One COMPUTED, eligible month per value, from the first month of the quadrimestre. */
        UnitBuilder values(String pack, ExactRatio... perMonth) {
            List<Monthly> list = new ArrayList<>();
            for (int i = 0; i < perMonth.length; i++) {
                list.add(computed(pack, month(i), perMonth[i]));
            }
            byPack.put(pack, list);
            return this;
        }

        /** Replaces the result of month {@code index} (0-based) of {@code pack}. */
        UnitBuilder set(String pack, int index, IndicatorStatus status, ExactRatio value, boolean eligible) {
            List<Monthly> list = new ArrayList<>(byPack.get(pack));
            list.removeIf(m -> m.month().equals(month(index)));
            list.add(monthly(pack, month(index), status, value, eligible));
            byPack.put(pack, list);
            return this;
        }

        UnitBuilder status(String pack, int index, IndicatorStatus status) {
            return set(pack, index, status, null, true);
        }

        /** Replaces every result of {@code pack} with exactly {@code results}, in the given order. */
        UnitBuilder results(String pack, Monthly... results) {
            byPack.put(pack, List.of(results));
            return this;
        }

        UnitBuilder without(String pack) {
            byPack.put(pack, List.of());
            return this;
        }

        UnitBuilder plus(Monthly result) {
            extra.add(result);
            return this;
        }

        Unit build(String ine, String cnes) {
            List<Monthly> all = new ArrayList<>();
            byPack.values().forEach(all::addAll);
            all.addAll(extra);
            return new Unit(ine, cnes, all);
        }
    }

    private static UnitBuilder unit() {
        return new UnitBuilder(Q1_2027);
    }

    /** C1…C7 in that order. */
    private static UnitBuilder concepts(Quadrimestre q, Classification... sevenConcepts) {
        UnitBuilder builder = new UnitBuilder(q);
        for (int i = 0; i < PACKS.size(); i++) {
            builder.concept(PACKS.get(i), sevenConcepts[i]);
        }
        return builder;
    }

    private static UnitBuilder all(Classification c) {
        return concepts(Q1_2027, c, c, c, c, c, c, c);
    }

    private static ComponentIIIInput input(Quadrimestre q, Unit... units) {
        return new ComponentIIIInput(IBGE, q, List.of(units));
    }

    private UnitResult single(UnitBuilder builder) {
        return single(builder, RULES);
    }

    private UnitResult single(UnitBuilder builder, Map<String, IndicatorRule> rules) {
        ComponentIIIResult result =
                consolidation.consolidate(input(builder.quadrimestre, builder.build(INE, CNES)), rules);
        assertThat(result.units()).hasSize(1);
        return result.units().get(0);
    }

    private static IndicatorQuadrimestral indicator(UnitResult unit, String pack) {
        return unit.indicators().stream()
                .filter(i -> i.indicatorPack().equals(pack))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no indicator " + pack));
    }

    private static void assertUnavailable(IndicatorQuadrimestral indicator, IndicatorStatus status) {
        assertThat(indicator.status()).as(indicator.indicatorPack()).isEqualTo(status);
        assertThat(indicator.mean()).as(indicator.indicatorPack() + " mean").isNull();
        assertThat(indicator.classification())
                .as(indicator.indicatorPack() + " classification")
                .isNull();
        assertThat(indicator.factor()).as(indicator.indicatorPack() + " factor").isNull();
    }

    /** No score at all — never zero, never re-weighted — and the limitation names the indicator. */
    private static void assertNoScore(UnitResult unit, IndicatorStatus status, String code, String pack) {
        assertThat(unit.status()).isEqualTo(status);
        assertThat(unit.score()).isNull();
        assertThat(unit.methodologicalClassification()).isNull();
        assertThat(unit.financialTransferClassification()).isNull();
        assertThat(unit.indicators()).hasSize(7);
        assertThat(unit.limitations()).anyMatch(l -> l.contains(code) || l.contains(pack));
    }

    private static void assertComputed(IndicatorQuadrimestral indicator, ExactRatio mean, Classification band) {
        String pack = indicator.indicatorPack();
        assertThat(indicator.status()).as(pack + " " + mean).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(indicator.mean()).as(pack + " mean").isEqualTo(mean.reduced());
        assertThat(indicator.classification()).as(pack + " band of " + mean).isEqualTo(band);
        assertThat(indicator.factor()).as(pack + " factor").isEqualByComparingTo(Nt08Tables.factor(band));
    }

    private void assertBand(String pack, ExactRatio monthlyValue, Classification band) {
        assertComputed(indicator(single(unit().constant(pack, monthlyValue)), pack), monthlyValue, band);
    }

    private void assertFinal(UnitBuilder builder, ExactRatio score, Classification band) {
        UnitResult unit = single(builder);
        assertThat(unit.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(unit.score()).as("score").isEqualTo(score.reduced());
        assertThat(unit.methodologicalClassification()).as("band of " + score).isEqualTo(band);
    }

    // ======================================================================================
    // Casos de teste derivados
    // ======================================================================================

    // ---- MET-05: Q1 jan–abr, Q2 mai–ago, Q3 set–dez; cutoffs 30/04, 31/08, 31/12 ----
    @Test
    void met05_quadrimestresOf2026HaveTheFederalMonthsAndCutoffs() {
        Quadrimestre q1 = Quadrimestre.parse("2026-Q1");
        Quadrimestre q2 = Quadrimestre.parse("2026-Q2");
        Quadrimestre q3 = Quadrimestre.parse("2026-Q3");
        assertThat(q1.months())
                .containsExactly(
                        YearMonth.of(2026, 1), YearMonth.of(2026, 2), YearMonth.of(2026, 3), YearMonth.of(2026, 4));
        assertThat(q2.months())
                .containsExactly(
                        YearMonth.of(2026, 5), YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8));
        assertThat(q3.months())
                .containsExactly(
                        YearMonth.of(2026, 9), YearMonth.of(2026, 10), YearMonth.of(2026, 11), YearMonth.of(2026, 12));
        assertThat(q1.cutoff()).isEqualTo(LocalDate.of(2026, 4, 30));
        assertThat(q2.cutoff()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(q3.cutoff()).isEqualTo(LocalDate.of(2026, 12, 31));

        // the consolidation of 2026-Q2 reads May–August
        UnitResult unit = single(new UnitBuilder(q2));
        assertThat(indicator(unit, C1).monthsUsed()).containsExactlyElementsOf(q2.months());
    }

    // ---- MET-17 análogo: C3 with "-" in all four months -> no Nota Final; not 0,25×2, not rescaled ----
    @Test
    void met17analog_c3WithoutAnyEligibleMonthLeavesTheUnitWithoutScore() {
        UnitBuilder builder = unit();
        for (int i = 0; i < 4; i++) {
            builder.set(C3, i, IndicatorStatus.NO_DENOMINATOR, null, false);
        }
        UnitResult unit = single(builder);

        assertUnavailable(indicator(unit, C3), IndicatorStatus.NO_DENOMINATOR);
        assertThat(indicator(unit, C3).monthsUsed()).isEmpty();
        assertNoScore(unit, IndicatorStatus.NO_DENOMINATOR, "C3", C3);
        // the six others stay classified on their own
        PACKS.stream()
                .filter(p -> !C3.equals(p))
                .forEach(p -> assertThat(indicator(unit, p).status()).as(p).isEqualTo(IndicatorStatus.COMPUTED));
    }

    // ---- MET-17 análogo, COMPUTED-but-ineligible variant: a value never makes the month count ----
    @Test
    void met17analog_c3WithComputedButIneligibleMonthsStillHasNoDenominator() {
        UnitBuilder builder = unit();
        for (int i = 0; i < 4; i++) {
            builder.set(C3, i, IndicatorStatus.COMPUTED, pct(100), false);
        }
        UnitResult unit = single(builder);

        assertUnavailable(indicator(unit, C3), IndicatorStatus.NO_DENOMINATOR);
        assertNoScore(unit, IndicatorStatus.NO_DENOMINATOR, "C3", C3);
    }

    // ---- MET-33: C1 40/50/60/70 -> 55 -> Ótimo; monthly bands do not enter ----
    @Test
    void met33_c1MeanOf40_50_60_70Is55AndOtimo() {
        UnitResult unit = single(unit().values(C1, pct(40), pct(50), pct(60), pct(70)));
        assertComputed(indicator(unit, C1), pct(55), Classification.OTIMO);
        assertThat(indicator(unit, C1).mean()).isEqualTo(ExactRatio.of(55, 1)); // reduced, not 220/4
        assertThat(indicator(unit, C1).monthsUsed()).containsExactlyElementsOf(Q1_2027.months());
    }

    // ---- MET-33, Quadro 1 variant: 42,62 / 40,87 / 41,9 / 51,98 -> 44,3425 -> Bom ----
    @Test
    void met33_quadro1C1MeanIs44_3425AndBom() {
        UnitResult unit = single(unit().values(
                        C1,
                        ExactRatio.of(4262, 100),
                        ExactRatio.of(4087, 100),
                        ExactRatio.of(419, 10),
                        ExactRatio.of(5198, 100)));
        assertComputed(indicator(unit, C1), ExactRatio.of(443_425, 10_000), Classification.BOM);
    }

    // ---- MET-34: C2 months 1 and 3 (80, 85) eligible, 2 and 4 without a cohort -> 82,5, not 41,25 ----
    @Test
    void met34_c2IneligibleMonthsLeaveTheMeanInsteadOfCountingAsZero() {
        UnitBuilder builder = unit().values(C2, pct(80), pct(0), pct(85), pct(0))
                .set(C2, 1, IndicatorStatus.COMPUTED, pct(0), false)
                .set(C2, 3, IndicatorStatus.COMPUTED, pct(0), false);
        IndicatorQuadrimestral c2 = indicator(single(builder), C2);

        assertComputed(c2, ExactRatio.of(165, 2), Classification.OTIMO);
        assertThat(c2.monthsUsed()).containsExactly(builder.month(0), builder.month(2));
        // every result read stays listed (ADR 0030), the ineligible months included
        assertThat(c2.monthsRead()).containsExactlyElementsOf(Q1_2027.months());
        assertThat(c2.resultIds())
                .containsExactlyElementsOf(
                        Q1_2027.months().stream().map(m -> C2 + "@" + m).toList());
    }

    // ---- MET-35: Quadro 2 example — Bom, Ótimo, Ótimo, Suficiente, Regular, Ótimo, Ótimo -> 8,5 Ótimo ----
    @Test
    void met35_quadro2ConceptsSumTo8_5AndOtimo() {
        UnitBuilder builder = concepts(
                Q1_2027,
                Classification.BOM,
                Classification.OTIMO,
                Classification.OTIMO,
                Classification.SUFICIENTE,
                Classification.REGULAR,
                Classification.OTIMO,
                Classification.OTIMO);
        assertFinal(builder, ExactRatio.of(17, 2), Classification.OTIMO);

        UnitResult unit = single(builder);
        assertThat(unit.financialTransferClassification()).isEqualTo(Classification.OTIMO);
        assertThat(indicator(unit, C1).factor()).isEqualByComparingTo(ExactRatio.of(3, 4));
        assertThat(indicator(unit, C4).factor()).isEqualByComparingTo(ExactRatio.of(1, 2));
        assertThat(indicator(unit, C5).factor()).isEqualByComparingTo(ExactRatio.of(1, 4));
        assertThat(indicator(unit, C7).factor()).isEqualByComparingTo(ExactRatio.of(1, 1));
    }

    // ---- MET-36b: attainable Quadro 6 boundaries through the whole consolidation ----
    @Test
    void met36b_attainableFinalScoreBoundaries() {
        Classification r = Classification.REGULAR;
        Classification s = Classification.SUFICIENTE;
        Classification b = Classification.BOM;
        Classification o = Classification.OTIMO;
        assertFinal(all(r), ExactRatio.of(5, 2), r); // 2,5
        assertFinal(concepts(Q1_2027, r, r, r, s, r, r, r), ExactRatio.of(11, 4), s); // 2,75
        assertFinal(concepts(Q1_2027, s, s, s, s, r, s, s), ExactRatio.of(19, 4), s); // 4,75
        assertFinal(all(s), pct(5), b); // 5,0
        assertFinal(all(b), ExactRatio.of(15, 2), b); // 7,5
        assertFinal(concepts(Q1_2027, b, b, b, b, b, o, b), ExactRatio.of(31, 4), o); // 7,75
    }

    // ---- MET-38: 2026-Q2 — Suficiente is paid as Bom, Ótimo as Ótimo; methodological preserved ----
    @Test
    void met38_q2_2026FinancialClassificationIsKeptApartFromTheMethodological() {
        Quadrimestre q2 = Quadrimestre.parse("2026-Q2");
        Classification s = Classification.SUFICIENTE;
        UnitResult suficiente = single(concepts(q2, s, s, s, s, Classification.REGULAR, s, s));
        assertThat(suficiente.score()).isEqualByComparingTo(ExactRatio.of(19, 4));
        assertThat(suficiente.methodologicalClassification()).isEqualTo(Classification.SUFICIENTE);
        assertThat(suficiente.financialTransferClassification()).isEqualTo(Classification.BOM);

        UnitResult otimo = single(new UnitBuilder(q2));
        assertThat(otimo.score()).isEqualByComparingTo(pct(10));
        assertThat(otimo.methodologicalClassification()).isEqualTo(Classification.OTIMO);
        assertThat(otimo.financialTransferClassification()).isEqualTo(Classification.OTIMO);
    }

    // ---- FIN-Q1-2026 through the consolidation: Ótimo is still paid as Bom ----
    @Test
    void finQ1_2026_otimoUnitIsPaidAsBomButKeepsItsMethodologicalBand() {
        UnitResult unit = single(new UnitBuilder(Quadrimestre.parse("2026-Q1")));
        assertThat(unit.methodologicalClassification()).isEqualTo(Classification.OTIMO);
        assertThat(unit.financialTransferClassification()).isEqualTo(Classification.BOM);
    }

    // ---- FIN-Q3-2026 / AMB-CIII-10: the derived regime is flagged on the result ----
    @Test
    void finQ3_2026_derivedRegimeIsAppliedAndFlaggedAsAmbCiii10() {
        Quadrimestre q3 = Quadrimestre.parse("2026-Q3");
        UnitBuilder builder = concepts(
                q3,
                Classification.BOM,
                Classification.BOM,
                Classification.BOM,
                Classification.BOM,
                Classification.BOM,
                Classification.BOM,
                Classification.BOM);
        ComponentIIIResult result = consolidation.consolidate(input(q3, builder.build(INE, CNES)), RULES);

        UnitResult unit = result.units().get(0);
        assertThat(unit.methodologicalClassification()).isEqualTo(Classification.BOM);
        assertThat(unit.financialTransferClassification()).isEqualTo(Classification.BOM);
        assertThat(result.limitations()).anyMatch(l -> l.contains("AMB-CIII-10"));
    }

    // ---- MET-39 (Componente III analog): Q3/2026 partial regime vs. Q1/2027 integral ----
    @Test
    void met39_q3_2026IsPartialAndQ1_2027UsesTheMethodologicalBand() {
        Quadrimestre q3 = Quadrimestre.parse("2026-Q3");
        Classification r = Classification.REGULAR;
        Classification o = Classification.OTIMO;
        UnitResult regular = single(concepts(q3, r, r, r, r, r, r, r));
        assertThat(regular.methodologicalClassification()).isEqualTo(r);
        assertThat(regular.financialTransferClassification()).isEqualTo(Classification.BOM);

        UnitResult otimo = single(new UnitBuilder(q3));
        assertThat(otimo.methodologicalClassification()).isEqualTo(o);
        assertThat(otimo.financialTransferClassification()).isEqualTo(o);

        UnitResult regular2027 = single(all(r));
        assertThat(regular2027.methodologicalClassification()).isEqualTo(r);
        assertThat(regular2027.financialTransferClassification()).isEqualTo(r);
    }

    // ---- C1 above 70 is Regular (non-monotonic) inside a Nota Final: 9 + 0,25 = 37/4 Ótimo ----
    @Test
    void met18_c1Above70IsRegularInsideTheNotaFinal() {
        UnitResult unit = single(unit().constant(C1, pct(80)));
        assertComputed(indicator(unit, C1), pct(80), Classification.REGULAR);
        assertThat(unit.score()).isEqualTo(ExactRatio.of(37, 4));
        assertThat(unit.methodologicalClassification()).isEqualTo(Classification.OTIMO);
    }

    // ---- Quadro 1 in full, with C3 partial (75 is Bom by the ficha, AMB-CIII-03) -> 8,0 Ótimo ----
    @Test
    void quadro1_fullExampleWithC3PartialIs8AndOtimo() {
        UnitBuilder builder = unit().values(
                        C1,
                        ExactRatio.of(4262, 100),
                        ExactRatio.of(4087, 100),
                        ExactRatio.of(419, 10),
                        ExactRatio.of(5198, 100))
                .values(C2, pct(80), pct(0), pct(85), pct(0))
                .set(C2, 1, IndicatorStatus.NO_DENOMINATOR, null, false)
                .set(C2, 3, IndicatorStatus.NO_DENOMINATOR, null, false)
                .values(C3, pct(75), pct(0), pct(0), pct(0))
                .set(C3, 1, IndicatorStatus.NO_DENOMINATOR, null, false)
                .set(C3, 2, IndicatorStatus.NO_DENOMINATOR, null, false)
                .set(C3, 3, IndicatorStatus.NO_DENOMINATOR, null, false)
                .values(
                        C4,
                        ExactRatio.of(452, 10),
                        ExactRatio.of(439, 10),
                        ExactRatio.of(498, 10),
                        ExactRatio.of(499, 10))
                .values(C5, pct(10), pct(11), pct(10), pct(50))
                .values(C6, pct(90), pct(84), pct(81), pct(79))
                .values(C7, pct(70), pct(79), pct(81), pct(82));
        UnitResult unit = single(builder);

        assertComputed(indicator(unit, C1), ExactRatio.of(443_425, 10_000), Classification.BOM);
        assertComputed(indicator(unit, C2), ExactRatio.of(165, 2), Classification.OTIMO);
        assertComputed(indicator(unit, C3), pct(75), Classification.BOM);
        assertComputed(indicator(unit, C4), ExactRatio.of(472, 10), Classification.SUFICIENTE);
        assertComputed(indicator(unit, C5), ExactRatio.of(81, 4), Classification.REGULAR);
        assertComputed(indicator(unit, C6), ExactRatio.of(167, 2), Classification.OTIMO);
        assertComputed(indicator(unit, C7), pct(78), Classification.OTIMO);
        assertThat(unit.score()).isEqualTo(ExactRatio.of(8, 1));
        assertThat(unit.methodologicalClassification()).isEqualTo(Classification.OTIMO);
    }

    // ---- the municipality is an aggregate of the product: Nota Final yes, financial classification no ----
    @Test
    void municipalUnitHasNoFinancialClassification() {
        UnitBuilder builder = new UnitBuilder(Quadrimestre.parse("2026-Q2"));
        ComponentIIIResult result = consolidation.consolidate(
                input(builder.quadrimestre, builder.build(INE, CNES), builder.build(null, null)), RULES);
        UnitResult team = result.units().get(0);
        UnitResult municipality = result.units().get(1);

        assertThat(team.financialTransferClassification()).isEqualTo(Classification.OTIMO);
        assertThat(municipality.score()).isEqualTo(pct(10));
        assertThat(municipality.methodologicalClassification()).isEqualTo(Classification.OTIMO);
        assertThat(municipality.financialTransferClassification()).isNull();
        assertThat(municipality.limitations()).anyMatch(l -> l.contains("repasse é por equipe"));
    }

    // ---- before 2026 there is no financial classification (Portaria § 2º), the methodological stays ----
    @Test
    void noFinancialClassificationBefore2026() {
        Quadrimestre q = Quadrimestre.parse("2025-Q3");
        UnitBuilder builder = new UnitBuilder(q);
        ComponentIIIResult result = consolidation.consolidate(input(q, builder.build(INE, CNES)), RULES);
        UnitResult unit = result.units().get(0);
        assertThat(unit.methodologicalClassification()).isEqualTo(Classification.OTIMO);
        assertThat(unit.financialTransferClassification()).isNull();
        assertThat(result.limitations()).anyMatch(l -> l.contains("antes de 2026-Q1"));
    }

    // ---- FIN-Q1-2027: financial equals methodological ----
    @Test
    void finQ1_2027_financialEqualsMethodologicalThroughTheConsolidation() {
        UnitResult unit = single(all(Classification.SUFICIENTE)
                .concept(C1, Classification.REGULAR)
                .concept(C2, Classification.REGULAR)); // 5 - 0,25 - 0,5 = 4,25
        assertThat(unit.score()).isEqualByComparingTo(ExactRatio.of(17, 4));
        assertThat(unit.methodologicalClassification()).isEqualTo(Classification.SUFICIENTE);
        assertThat(unit.financialTransferClassification()).isEqualTo(Classification.SUFICIENTE);
    }

    // ======================================================================================
    // Status of an indicator and of the unit
    // ======================================================================================

    // ---- one BLOCKED month blocks the indicator and leaves the unit without score ----
    @Test
    void blockedMonthBlocksTheIndicatorAndTheUnit() {
        UnitResult unit = single(unit().status(C5, 1, IndicatorStatus.BLOCKED));
        assertUnavailable(indicator(unit, C5), IndicatorStatus.BLOCKED);
        assertNoScore(unit, IndicatorStatus.BLOCKED, "C5", C5);
        // the ids read stay listed and the limitation names the competência
        assertThat(indicator(unit, C5).resultIds()).hasSize(4).contains(C5 + "@2027-02");
        assertThat(indicator(unit, C5).monthsUsed()).isEmpty();
        assertThat(unit.limitations()).anyMatch(l -> l.contains("2027-02") && l.contains("BLOCKED"));
    }

    // ---- months: BLOCKED > UNSUPPORTED_SOURCE > RULE_AMBIGUITY, even on an ineligible C2 month ----
    @Test
    void monthStatusPrecedenceWithinAnIndicator() {
        UnitResult blocked = single(unit().status(C4, 0, IndicatorStatus.RULE_AMBIGUITY)
                .status(C4, 1, IndicatorStatus.UNSUPPORTED_SOURCE)
                .status(C4, 2, IndicatorStatus.BLOCKED));
        assertUnavailable(indicator(blocked, C4), IndicatorStatus.BLOCKED);

        UnitResult unsupported = single(
                unit().status(C4, 0, IndicatorStatus.RULE_AMBIGUITY).status(C4, 3, IndicatorStatus.UNSUPPORTED_SOURCE));
        assertUnavailable(indicator(unsupported, C4), IndicatorStatus.UNSUPPORTED_SOURCE);

        UnitResult ambiguity = single(unit().status(C1, 2, IndicatorStatus.RULE_AMBIGUITY));
        assertUnavailable(indicator(ambiguity, C1), IndicatorStatus.RULE_AMBIGUITY);
        assertNoScore(ambiguity, IndicatorStatus.RULE_AMBIGUITY, "C1", C1);

        // regardless of eligibility: a blocked month without cohort still blocks C2
        UnitResult ineligibleBlocked = single(unit().set(C2, 1, IndicatorStatus.BLOCKED, null, false));
        assertUnavailable(indicator(ineligibleBlocked, C2), IndicatorStatus.BLOCKED);

        // a status month wins over a NO_DENOMINATOR month
        UnitResult blockedOverNoDenominator =
                single(unit().status(C4, 0, IndicatorStatus.NO_DENOMINATOR).status(C4, 1, IndicatorStatus.BLOCKED));
        assertUnavailable(indicator(blockedOverNoDenominator, C4), IndicatorStatus.BLOCKED);
    }

    // ---- unit: worst indicator by BLOCKED > UNSUPPORTED_SOURCE > RULE_AMBIGUITY > NO_DENOMINATOR ----
    @Test
    void unitStatusIsTheWorstIndicatorStatus() {
        UnitBuilder noDenominatorC3 = unit();
        for (int i = 0; i < 4; i++) {
            noDenominatorC3.set(C3, i, IndicatorStatus.COMPUTED, pct(90), false);
        }
        noDenominatorC3.status(C4, 0, IndicatorStatus.RULE_AMBIGUITY);
        assertNoScore(single(noDenominatorC3), IndicatorStatus.RULE_AMBIGUITY, "C4", C4);

        noDenominatorC3.status(C2, 0, IndicatorStatus.UNSUPPORTED_SOURCE);
        assertNoScore(single(noDenominatorC3), IndicatorStatus.UNSUPPORTED_SOURCE, "C2", C2);

        noDenominatorC3.status(C7, 3, IndicatorStatus.BLOCKED);
        assertNoScore(single(noDenominatorC3), IndicatorStatus.BLOCKED, "C7", C7);
    }

    // ---- AMB-CIII-07: an eligible NO_DENOMINATOR month is never zero and never skipped ----
    @Test
    void ambCiii07_eligibleNoDenominatorMonthIsRuleAmbiguityAndNotEligibleOneIsADashMonthOutOfTheMean() {
        UnitResult unit = single(unit().status(C4, 2, IndicatorStatus.NO_DENOMINATOR));
        assertUnavailable(indicator(unit, C4), IndicatorStatus.RULE_AMBIGUITY);
        assertNoScore(unit, IndicatorStatus.RULE_AMBIGUITY, "C4", C4);

        // flagged not eligible, the empty month is a "-" month out of the mean in every pack
        UnitBuilder flagged = unit();
        flagged.set(C4, 2, IndicatorStatus.NO_DENOMINATOR, null, false);
        IndicatorQuadrimestral c4 = indicator(single(flagged), C4);
        assertComputed(c4, pct(90), Classification.OTIMO);
        assertThat(c4.monthsUsed()).doesNotContain(flagged.month(2)).hasSize(3);

        // in C2/C3 an eligible NO_DENOMINATOR month would enter the mean: also an ambiguity
        UnitResult eligibleC2 = single(unit().set(C2, 0, IndicatorStatus.NO_DENOMINATOR, null, true));
        assertUnavailable(indicator(eligibleC2, C2), IndicatorStatus.RULE_AMBIGUITY);
    }

    // ---- the eligibility flag is ignored outside C2/C3: the month enters the mean ----
    @Test
    void eligibilityFlagIsIgnoredOutsideC2AndC3() {
        UnitBuilder builder = unit().values(C4, pct(40), pct(60), pct(80), pct(100))
                .set(C4, 1, IndicatorStatus.COMPUTED, pct(60), false);
        IndicatorQuadrimestral c4 = indicator(single(builder), C4);
        assertComputed(c4, pct(70), Classification.BOM);
        assertThat(c4.monthsUsed()).hasSize(4);
    }

    // ---- a month without a published result blocks the indicator ----
    @Test
    void missingMonthBlocksTheIndicator() {
        UnitBuilder builder = unit();
        builder.values(C6, pct(90), pct(90), pct(90)); // fourth month never published
        UnitResult unit = single(builder);
        assertUnavailable(indicator(unit, C6), IndicatorStatus.BLOCKED);
        assertNoScore(unit, IndicatorStatus.BLOCKED, "C6", C6);
    }

    // ---- AMB-C2-03: a team with no child completing 2 years has no C2 row: that is a "-" month ----
    @Test
    void teamWithoutC2RowInAMonthTheMunicipalityPublishedIsADashMonthOutOfTheMean() {
        UnitBuilder municipal = unit().values(C2, pct(80), pct(60), pct(80), pct(60));
        UnitBuilder team = unit();
        team.results(C2, computed(C2, team.month(0), pct(90)), computed(C2, team.month(2), pct(70)));

        ComponentIIIResult result =
                consolidation.consolidate(input(Q1_2027, municipal.build(null, null), team.build(INE, CNES)), RULES);

        UnitResult teamResult = result.units().stream()
                .filter(u -> INE.equals(u.ine()))
                .findFirst()
                .orElseThrow();
        IndicatorQuadrimestral c2 = indicator(teamResult, C2);
        assertComputed(c2, pct(80), Classification.OTIMO);
        assertThat(c2.monthsUsed()).containsExactly(team.month(0), team.month(2));
        assertThat(teamResult.status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    // ---- C3 emits no TeamResult for a team without an eligible episode: a "-" month, not a block ----
    @Test
    void teamWithoutC3RowInAMonthTheMunicipalityPublishedIsADashMonthOutOfTheMean() {
        UnitBuilder municipal = unit().values(C3, pct(80), pct(60), pct(80), pct(60));
        UnitBuilder team = unit();
        team.results(C3, computed(C3, team.month(0), pct(90)), computed(C3, team.month(3), pct(70)));

        ComponentIIIResult result =
                consolidation.consolidate(input(Q1_2027, municipal.build(null, null), team.build(INE, CNES)), RULES);

        IndicatorQuadrimestral c3 = indicator(unitOf(result, INE), C3);
        assertComputed(c3, pct(80), Classification.OTIMO);
        assertThat(c3.monthsUsed()).containsExactly(team.month(0), team.month(3));
        assertThat(indicator(unitOf(result, INE), C3).status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void teamWithNoC2RowInAnyMonthHasNoC2MeanAndNoScoreNeverZero() {
        UnitBuilder team = unit().without(C2);

        ComponentIIIResult result =
                consolidation.consolidate(input(Q1_2027, unit().build(null, null), team.build(INE, CNES)), RULES);

        UnitResult teamResult = result.units().stream()
                .filter(u -> INE.equals(u.ine()))
                .findFirst()
                .orElseThrow();
        assertUnavailable(indicator(teamResult, C2), IndicatorStatus.NO_DENOMINATOR);
        assertNoScore(teamResult, IndicatorStatus.NO_DENOMINATOR, "C2", C2);
        assertThat(teamResult.limitations()).anyMatch(l -> l.contains("AMB-CIII-06"));
    }

    @Test
    void absentTeamRowStillBlocksWhenTheMunicipalityDidNotPublishThatMonthOrThePackIsNotCohortOnly() {
        // the municipality is missing the month too: a real publication gap, not a "-" month
        UnitBuilder team = unit();
        team.results(C2, computed(C2, team.month(0), pct(90)));
        UnitBuilder municipalGap = unit();
        municipalGap.results(C2, computed(C2, municipalGap.month(0), pct(90)));
        ComponentIIIResult gap =
                consolidation.consolidate(input(Q1_2027, municipalGap.build(null, null), team.build(INE, CNES)), RULES);
        assertUnavailable(indicator(unitOf(gap, INE), C2), IndicatorStatus.BLOCKED);

        // C4 is not a cohort-event pack: an absent month blocks even when the municipality has it
        UnitBuilder c4Team = unit();
        c4Team.values(C4, pct(60), pct(60), pct(60));
        ComponentIIIResult notCohort =
                consolidation.consolidate(input(Q1_2027, unit().build(null, null), c4Team.build(INE, CNES)), RULES);
        assertUnavailable(indicator(unitOf(notCohort, INE), C4), IndicatorStatus.BLOCKED);

        // the municipality itself never has a "-" month by absence
        UnitBuilder municipalMissing = unit();
        municipalMissing.values(C2, pct(80), pct(60), pct(80));
        assertUnavailable(indicator(single(municipalMissing), C2), IndicatorStatus.BLOCKED);
    }

    // ---- a NO_DENOMINATOR month that is not eligible is a "-" month for every pack ----
    @Test
    void c7MonthWithoutDenominatorAndNotEligibleIsADashMonthOutOfTheMean() {
        UnitBuilder builder = unit().values(C7, pct(90), pct(0), pct(60), pct(60));
        builder.set(C7, 1, IndicatorStatus.NO_DENOMINATOR, null, false);

        IndicatorQuadrimestral c7 = indicator(single(builder), C7);

        assertComputed(c7, pct(70), Classification.BOM);
        assertThat(c7.monthsUsed()).containsExactly(builder.month(0), builder.month(2), builder.month(3));
    }

    @Test
    void c7WithEveryMonthWithoutDenominatorHasNoMeanAndNoScore() {
        UnitBuilder builder = unit();
        for (int i = 0; i < 4; i++) {
            builder.set(C7, i, IndicatorStatus.NO_DENOMINATOR, null, false);
        }
        UnitResult unit = single(builder);

        assertUnavailable(indicator(unit, C7), IndicatorStatus.NO_DENOMINATOR);
        assertThat(indicator(unit, C7).monthsUsed()).isEmpty();
        assertNoScore(unit, IndicatorStatus.NO_DENOMINATOR, "C7", C7);
    }

    @Test
    void c3MonthWithoutDenominatorAmongValidMonthsIsADashMonthOutOfTheMean() {
        UnitBuilder builder = unit().values(C3, pct(90), pct(0), pct(60), pct(60));
        builder.set(C3, 1, IndicatorStatus.NO_DENOMINATOR, null, false);

        IndicatorQuadrimestral c3 = indicator(single(builder), C3);

        assertComputed(c3, pct(70), Classification.BOM);
        assertThat(c3.monthsUsed()).containsExactly(builder.month(0), builder.month(2), builder.month(3));
    }

    @Test
    void c3TeamWithoutRowInTheMonthsOfNoPuerperalCohortIsDashMonthsToo() {
        UnitBuilder team = unit();
        team.results(C3, computed(C3, team.month(1), pct(60)), computed(C3, team.month(3), pct(80)));

        ComponentIIIResult result =
                consolidation.consolidate(input(Q1_2027, unit().build(null, null), team.build(INE, CNES)), RULES);

        IndicatorQuadrimestral c3 = indicator(unitOf(result, INE), C3);
        assertComputed(c3, pct(70), Classification.BOM);
        assertThat(c3.monthsUsed()).containsExactly(team.month(1), team.month(3));
    }

    private static UnitResult unitOf(ComponentIIIResult result, String ine) {
        return result.units().stream()
                .filter(u -> ine.equals(u.ine()))
                .findFirst()
                .orElseThrow();
    }

    // ---- COMPUTED without a value, or two results for one month, block the indicator ----
    @Test
    void computedMonthWithoutValueOrDuplicateMonthBlocksTheIndicator() {
        UnitResult nullValue = single(unit().set(C5, 0, IndicatorStatus.COMPUTED, null, true));
        assertUnavailable(indicator(nullValue, C5), IndicatorStatus.BLOCKED);

        UnitBuilder duplicate = unit();
        duplicate.plus(new Monthly(C5, duplicate.month(2), "c5-duplicate", IndicatorStatus.COMPUTED, pct(90), true));
        UnitResult duplicated = single(duplicate);
        assertUnavailable(indicator(duplicated, C5), IndicatorStatus.BLOCKED);
        assertNoScore(duplicated, IndicatorStatus.BLOCKED, "C5", C5);
    }

    // ---- a value-less month blocks before a NO_DENOMINATOR one is read as an ambiguity, even ineligible ----
    @Test
    void computedMonthWithoutValueBlocksBeforeNoDenominatorAndEvenWhenIneligible() {
        UnitResult mixed = single(
                unit().status(C4, 0, IndicatorStatus.NO_DENOMINATOR).set(C4, 1, IndicatorStatus.COMPUTED, null, true));
        assertUnavailable(indicator(mixed, C4), IndicatorStatus.BLOCKED);

        UnitResult ineligible = single(unit().set(C2, 1, IndicatorStatus.COMPUTED, null, false));
        assertUnavailable(indicator(ineligible, C2), IndicatorStatus.BLOCKED);
    }

    // ---- a missing indicator never becomes zero nor redistributes its weight (MET-17) ----
    @Test
    void missingIndicatorLeavesTheUnitWithoutScore() {
        UnitResult unit = single(unit().without(C7));
        assertUnavailable(indicator(unit, C7), IndicatorStatus.BLOCKED);
        assertThat(indicator(unit, C7).weight()).isEqualTo(BigInteger.TWO);
        assertNoScore(unit, IndicatorStatus.BLOCKED, "C7", C7);
    }

    // ---- a pack without a rule in the map cannot be banded: BLOCKED ----
    @Test
    void missingRuleBlocksTheIndicator() {
        Map<String, IndicatorRule> rules = new HashMap<>(RULES);
        rules.remove(C6);
        UnitResult unit = single(unit(), rules);
        assertUnavailable(indicator(unit, C6), IndicatorStatus.BLOCKED);
        assertNoScore(unit, IndicatorStatus.BLOCKED, "C6", C6);
    }

    // ---- a rule registered under another pack id cannot band this one ----
    @Test
    void ruleOfAnotherPackBlocksTheIndicator() {
        Map<String, IndicatorRule> rules = new HashMap<>(RULES);
        rules.put(C4, RULES.get(C5));
        assertNoScore(single(unit(), rules), IndicatorStatus.BLOCKED, "C4", C4);
    }

    // ---- months of another rule version are never averaged with the current one ----
    @Test
    void monthOfAnotherRuleVersionBlocksTheIndicator() {
        String current = RULES.get(C6).descriptor().ruleVersion();
        UnitBuilder builder = unit();
        List<Monthly> versioned = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            String version = i == 2 ? C6 + "@0.0.1" : current;
            versioned.add(new Monthly(
                    C6,
                    builder.month(i),
                    C6 + "@" + builder.month(i),
                    IndicatorStatus.COMPUTED,
                    pct(90),
                    true,
                    version));
        }
        UnitResult other = single(builder.results(C6, versioned.toArray(Monthly[]::new)));
        assertUnavailable(indicator(other, C6), IndicatorStatus.BLOCKED);
        assertThat(other.limitations()).anyMatch(l -> l.contains(C6 + "@0.0.1") && l.contains("2027-03"));

        versioned.set(
                2, new Monthly(C6, builder.month(2), "c6-current", IndicatorStatus.COMPUTED, pct(90), true, current));
        UnitResult same = single(builder.results(C6, versioned.toArray(Monthly[]::new)));
        assertThat(indicator(same, C6).status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(same.limitations()).noneMatch(l -> l.startsWith("C6") && l.contains("versão"));
    }

    // ---- a month without rule version is accepted, with a limitation naming it ----
    @Test
    void monthWithoutRuleVersionIsAcceptedWithALimitation() {
        UnitResult unit = single(unit());
        assertThat(unit.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(unit.limitations())
                .anyMatch(l -> l.contains("versão da regra não informada") && l.contains("2027-01"));
    }

    // ---- the input refuses a monthly result without id, pack, month or status ----
    @Test
    void monthlyRequiresIdPackMonthAndStatus() {
        YearMonth month = Q1_2027.firstMonth();
        assertThatThrownBy(() -> new Monthly(C1, month, null, IndicatorStatus.COMPUTED, pct(1), true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Monthly(null, month, "id", IndicatorStatus.COMPUTED, pct(1), true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Monthly(C1, null, "id", IndicatorStatus.COMPUTED, pct(1), true))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Monthly(C1, month, "id", null, pct(1), true))
                .isInstanceOf(NullPointerException.class);
    }

    // ---- months outside the quadrimestre and unknown packs are ignored ----
    @Test
    void monthsOutsideTheQuadrimestreAndUnknownPacksAreIgnored() {
        UnitBuilder builder = unit().plus(monthly(C1, YearMonth.of(2026, 12), IndicatorStatus.BLOCKED, null, true))
                .plus(computed(C1, YearMonth.of(2027, 5), pct(0)))
                .plus(monthly("c9-desconhecido", Q1_2027.firstMonth(), IndicatorStatus.BLOCKED, null, true));
        UnitResult unit = single(builder);

        assertThat(unit.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertComputed(indicator(unit, C1), valueFor(C1, Classification.OTIMO), Classification.OTIMO);
        assertThat(indicator(unit, C1).monthsUsed()).containsExactlyElementsOf(Q1_2027.months());
        assertThat(unit.score()).isEqualByComparingTo(pct(10));
    }

    // ---- output: always the 7 indicators in descriptor order with their weights; months chronological ----
    @Test
    void indicatorsFollowTheDescriptorOrderAndMonthsAreChronological() {
        UnitBuilder builder = unit();
        builder.results(
                C1,
                computed(C1, builder.month(3), pct(70)),
                computed(C1, builder.month(0), pct(40)),
                computed(C1, builder.month(2), pct(60)),
                computed(C1, builder.month(1), pct(50)));
        UnitResult unit = single(builder);

        assertThat(unit.indicators())
                .extracting(IndicatorQuadrimestral::indicatorPack)
                .containsExactlyElementsOf(PACKS);
        assertThat(unit.indicators())
                .extracting(IndicatorQuadrimestral::weight)
                .containsExactlyElementsOf(
                        WEIGHTS.stream().map(BigInteger::valueOf).toList());
        IndicatorQuadrimestral c1 = indicator(unit, C1);
        assertThat(c1.monthsUsed()).containsExactlyElementsOf(Q1_2027.months());
        assertThat(c1.resultIds())
                .containsExactlyElementsOf(
                        Q1_2027.months().stream().map(m -> C1 + "@" + m).toList());
        assertComputed(c1, pct(55), Classification.OTIMO);
    }

    // ---- teams and the municipality are consolidated independently, in input order ----
    @Test
    void unitsAreConsolidatedIndependentlyInInputOrder() {
        Unit teamA = unit().build(INE, CNES);
        Unit teamB = unit().status(C1, 0, IndicatorStatus.BLOCKED).build("0000000002", "7654321");
        Unit municipality = all(Classification.BOM).build(null, null);
        ComponentIIIResult result = consolidation.consolidate(input(Q1_2027, teamA, teamB, municipality), RULES);

        assertThat(result.units()).extracting(UnitResult::ine).containsExactly(INE, "0000000002", null);
        assertThat(result.units()).extracting(UnitResult::cnes).containsExactly(CNES, "7654321", null);

        UnitResult a = result.units().get(0);
        assertThat(a.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(a.score()).isEqualByComparingTo(pct(10));
        assertThat(a.methodologicalClassification()).isEqualTo(Classification.OTIMO);

        assertNoScore(result.units().get(1), IndicatorStatus.BLOCKED, "C1", C1);

        UnitResult m = result.units().get(2);
        assertThat(m.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(m.score()).isEqualByComparingTo(ExactRatio.of(15, 2));
        assertThat(m.methodologicalClassification()).isEqualTo(Classification.BOM);
    }

    // ======================================================================================
    // ENG-25: exact bands through the consolidation
    // ======================================================================================

    // ---- C1: x≤10 Regular · 10<x≤30 Suficiente · 30<x≤50 Bom · 50<x≤70 Ótimo · x>70 Regular ----
    @Test
    void eng25_c1BandBoundariesThroughTheConsolidation() {
        assertBand(C1, below(10), Classification.REGULAR);
        assertBand(C1, pct(10), Classification.REGULAR);
        assertBand(C1, above(10), Classification.SUFICIENTE);
        assertBand(C1, below(30), Classification.SUFICIENTE);
        assertBand(C1, pct(30), Classification.SUFICIENTE);
        assertBand(C1, above(30), Classification.BOM);
        assertBand(C1, below(50), Classification.BOM);
        assertBand(C1, pct(50), Classification.BOM);
        assertBand(C1, above(50), Classification.OTIMO);
        assertBand(C1, below(70), Classification.OTIMO);
        assertBand(C1, pct(70), Classification.OTIMO);
        assertBand(C1, above(70), Classification.REGULAR);
    }

    // ---- C2–C7: ≤25 Regular · >25 Suficiente · >50 Bom · >75 Ótimo (≤100); >100 is outside the ficha ----
    @Test
    void eng25_c2ToC7BandBoundariesThroughTheConsolidation() {
        for (String pack : PACKS.subList(1, PACKS.size())) {
            assertBand(pack, below(25), Classification.REGULAR);
            assertBand(pack, pct(25), Classification.REGULAR);
            assertBand(pack, above(25), Classification.SUFICIENTE);
            assertBand(pack, below(50), Classification.SUFICIENTE);
            assertBand(pack, pct(50), Classification.SUFICIENTE);
            assertBand(pack, above(50), Classification.BOM);
            assertBand(pack, below(75), Classification.BOM);
            assertBand(pack, pct(75), Classification.BOM);
            assertBand(pack, above(75), Classification.OTIMO);
            assertBand(pack, below(100), Classification.OTIMO);
            assertBand(pack, pct(100), Classification.OTIMO);
        }
    }

    // ---- C2–C7 above 100: no band in the ficha -> RULE_AMBIGUITY, never forced into Ótimo ----
    @Test
    void eng25_c2ToC7MeanAbove100IsRuleAmbiguity() {
        for (String pack : PACKS.subList(1, PACKS.size())) {
            UnitResult unit = single(unit().constant(pack, above(100)));
            IndicatorQuadrimestral indicator = indicator(unit, pack);
            assertThat(indicator.status()).as(pack).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
            assertThat(indicator.classification()).as(pack).isNull();
            assertThat(indicator.factor()).as(pack).isNull();
            assertThat(unit.status()).as(pack).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
            assertThat(unit.score()).as(pack).isNull();
        }
    }

    // ---- AMB-CIII-04: non-terminating means are banded exactly, never rounded first ----
    @Test
    void ambCiii04_nonTerminatingMeansAreBandedWithoutRounding() {
        // C4 50, 50, 50, 50+1/3 -> 50 1/12 -> Bom (> 50)
        assertComputed(
                indicator(single(unit().values(C4, pct(50), pct(50), pct(50), ExactRatio.of(151, 3))), C4),
                ExactRatio.of(601, 12),
                Classification.BOM);
        // C4 75, 75, 75, 76 -> 75,25 -> Ótimo
        assertComputed(
                indicator(single(unit().values(C4, pct(75), pct(75), pct(75), pct(76))), C4),
                ExactRatio.of(301, 4),
                Classification.OTIMO);
        // C4 75, 75, 75, 75+1/10^6 -> 75,00000025: Ótimo; rounded to two places it would read Bom
        assertComputed(
                indicator(single(unit().values(C4, pct(75), pct(75), pct(75), above(75))), C4),
                ExactRatio.of(75 * 4 * MILLION + 1, 4 * MILLION),
                Classification.OTIMO);
        // C1 50, 50, 50, 50+1/10^6 -> just above 50: Ótimo, not Bom
        assertComputed(
                indicator(single(unit().values(C1, pct(50), pct(50), pct(50), above(50))), C1),
                ExactRatio.of(50 * 4 * MILLION + 1, 4 * MILLION),
                Classification.OTIMO);
    }

    // ======================================================================================
    // Result, determinism, wiring and descriptor
    // ======================================================================================

    @Test
    void emptyInputYieldsNoUnitsAndKeepsTheQuadrimestreAndLimitations() {
        ComponentIIIResult result = consolidation.consolidate(input(Q1_2027), RULES);
        assertThat(result.units()).isEmpty();
        assertThat(result.quadrimestre()).isEqualTo(Q1_2027);
        assertThat(result.limitations()).isNotEmpty();
    }

    @Test
    void sameInputTwiceYieldsEqualResults() {
        ComponentIIIInput in = input(
                Q1_2027,
                unit().values(C1, pct(40), pct(50), pct(60), ExactRatio.of(211, 3))
                        .build(INE, CNES),
                unit().status(C3, 1, IndicatorStatus.BLOCKED).build(null, null));
        ComponentIIIResult first = consolidation.consolidate(in, RULES);
        assertThat(consolidation.consolidate(in, RULES)).isEqualTo(first);
        assertThat(new Nt08Consolidation().consolidate(in, RULES)).isEqualTo(first);
        assertThat(first.quadrimestre()).isEqualTo(Q1_2027);
        assertThat(first.limitations()).isNotEmpty();
    }

    @Test
    void componentIiiWiresTheNt08Consolidation() {
        assertThat(ComponentIII.consolidation()).isInstanceOf(Nt08Consolidation.class);
    }

    @Test
    void descriptorKeepsGatesClosedWeightsSumTo10AndNoLongerSaysInImplementation() {
        assertThat(GateFixtures.shipped(ComponentIII.DESCRIPTOR).isComplete()).isFalse();
        assertThat(ComponentIII.DESCRIPTOR.components())
                .extracting(ComponentSpec::code)
                .containsExactlyElementsOf(PACKS);
        assertThat(ComponentIII.DESCRIPTOR.components())
                .extracting(ComponentSpec::weight)
                .containsExactlyElementsOf(
                        WEIGHTS.stream().map(BigInteger::valueOf).toList());
        assertThat(ComponentIII.DESCRIPTOR.components().stream()
                        .map(ComponentSpec::weight)
                        .reduce(BigInteger.ZERO, BigInteger::add))
                .isEqualTo(BigInteger.TEN);
        assertThat(ComponentIII.DESCRIPTOR.standingLimitationLines())
                .noneMatch(l -> l.toLowerCase(Locale.ROOT).contains("em implementação"));
    }
}
