package esusdata.indicator.pack.c3;

import static esusdata.indicator.pack.c3.C3Fixtures.ACS;
import static esusdata.indicator.pack.c3.C3Fixtures.DUM;
import static esusdata.indicator.pack.c3.C3Fixtures.HEPATITIS_B;
import static esusdata.indicator.pack.c3.C3Fixtures.HEPATITIS_C;
import static esusdata.indicator.pack.c3.C3Fixtures.HIV;
import static esusdata.indicator.pack.c3.C3Fixtures.INE;
import static esusdata.indicator.pack.c3.C3Fixtures.MORE_PRENATAL_DAYS;
import static esusdata.indicator.pack.c3.C3Fixtures.OUTCOME;
import static esusdata.indicator.pack.c3.C3Fixtures.PREGNANCY_CIAP;
import static esusdata.indicator.pack.c3.C3Fixtures.SUBSTITUTE_END;
import static esusdata.indicator.pack.c3.C3Fixtures.SYPHILIS;
import static esusdata.indicator.pack.c3.C3Fixtures.anchor;
import static esusdata.indicator.pack.c3.C3Fixtures.assertAmbiguous;
import static esusdata.indicator.pack.c3.C3Fixtures.assertMet;
import static esusdata.indicator.pack.c3.C3Fixtures.assertNotMet;
import static esusdata.indicator.pack.c3.C3Fixtures.care;
import static esusdata.indicator.pack.c3.C3Fixtures.computeNovember;
import static esusdata.indicator.pack.c3.C3Fixtures.context;
import static esusdata.indicator.pack.c3.C3Fixtures.conventionPack;
import static esusdata.indicator.pack.c3.C3Fixtures.dataset;
import static esusdata.indicator.pack.c3.C3Fixtures.dtpa;
import static esusdata.indicator.pack.c3.C3Fixtures.dum;
import static esusdata.indicator.pack.c3.C3Fixtures.episodeKey;
import static esusdata.indicator.pack.c3.C3Fixtures.episodeRow;
import static esusdata.indicator.pack.c3.C3Fixtures.linked;
import static esusdata.indicator.pack.c3.C3Fixtures.outcome;
import static esusdata.indicator.pack.c3.C3Fixtures.practice;
import static esusdata.indicator.pack.c3.C3Fixtures.prenatal;
import static esusdata.indicator.pack.c3.C3Fixtures.puerperal;
import static esusdata.indicator.pack.c3.C3Fixtures.supporting;
import static esusdata.indicator.pack.c3.C3Fixtures.tests;
import static esusdata.indicator.pack.c3.C3Fixtures.visit;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.RuleOutcome;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Episodes and their milestones: the recorded outcome versus the substitute date (MET-21,
 * CT-C3-62..65), one person's pregnancies kept apart (MET-08, CT-C3-41/66/67), cohort ambiguities
 * (CT-C3-68..73) and day arithmetic across month ends and 29/02 (ENG-27). The C3 rule counts days
 * (294, 42, weeks × 7), never months, so no anniversary convention is involved: every expected date
 * here is a plain day count.
 */
class C3CohortCasesTest {

    private static final String P1 = "gestante-1";
    private static final String EP1 = episodeKey(P1, DUM);
    private static final String SUBSTITUTE = "ELEGIVEL_DATA_SUBSTITUTIVA_294D";
    private static final String RECORDED = "ELEGIVEL_DESFECHO_REGISTRADO";

    // ---- MET-21: the recorded outcome or DUM+294, shown in the evidence ----

    @Test
    void met21_ct62_aRecordedOutcomeEndsThePregnancyAndTheConsultationCountsForINotB() {
        List<Record> records = withSixPrenatalDays();
        records.add(outcome(P1, OUTCOME));
        records.add(puerperal(P1, dum(280))); // D+10 with D = DUM+270
        RuleOutcome outcome = computeNovember(new C3Pack(), records);

        EvidenceItem eligible = episodeRow(outcome, EP1);
        assertThat(eligible.decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
        assertThat(eligible.reasonCode()).isEqualTo(RECORDED);
        assertThat(eligible.eventDate()).isEqualTo("2025-09-28");
        assertMet(practice(outcome, EP1, "I"), 9);
        assertNotMet(practice(outcome, EP1, "B"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void met21_withoutOutcomeTheSamePuerperalCodeFallsInsideThePregnancy() {
        List<Record> records = withSixPrenatalDays();
        records.add(puerperal(P1, dum(280))); // DUM+280 < DUM+294: still pregnancy
        RuleOutcome outcome = computeNovember(new C3Pack(), records);

        EvidenceItem eligible = episodeRow(outcome, EP1);
        assertThat(eligible.reasonCode()).isEqualTo(SUBSTITUTE);
        assertThat(eligible.eventDate()).isEqualTo("2025-10-22");
        assertNotMet(practice(outcome, EP1, "I"));
        // a seventh consultation with a non-pregnancy code: undecided (AMB-C3-11)
        EvidenceItem b = practice(outcome, EP1, "B");
        assertThat(b.reasonCode()).startsWith("AMBIGUIDADE_AMB_C3_");
        assertThat(b.points()).isNull();
    }

    @Test
    void met21_ct63_substitutePuerperiumCountsDumPlus300() {
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(anchor(P1, dum(56), DUM));
        records.add(puerperal(P1, dum(300))); // D+6 with D = DUM+294
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertThat(episodeRow(outcome, EP1).eventDate()).isEqualTo(SUBSTITUTE_END.toString());
        assertMet(practice(outcome, EP1, "I"), 9);
    }

    @Test
    void met21_ct63_substitutePuerperiumNeverPassesDumPlus336() {
        YearMonth december = YearMonth.of(2025, 12);
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(anchor(P1, dum(56), DUM));
        records.add(puerperal(P1, dum(340))); // D+46
        records.add(visit(P1, dum(340), ACS));
        RuleOutcome outcome = new C3Pack().compute(dataset(december, records), context(december));

        assertNotMet(practice(outcome, EP1, "I"));
        assertNotMet(practice(outcome, EP1, "J"));
        // the puerperal code outside every episode window is its own undecided subject (AMB-C3-03)
        assertThat(episodeRow(outcome, P1 + "#sem-dum").reasonCode()).isEqualTo("AMBIGUIDADE_AMB_C3_03");
        // D+42 = DUM+336 = 2025-12-03 falls in December
        assertThat(outcome.result().consolidationEligible()).isTrue();
    }

    @Test
    void ct64_aRetroactiveOutcomeRecomputesTheMonthAndShowsWhichDateWasUsed() {
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(anchor(P1, dum(56), DUM));
        records.add(visit(P1, LocalDate.of(2025, 11, 15), ACS)); // D+24 substitute, D+48 recorded
        RuleOutcome before = computeNovember(new C3Pack(), records);
        assertThat(episodeRow(before, EP1).reasonCode()).isEqualTo(SUBSTITUTE);
        assertThat(episodeRow(before, EP1).eventDate()).isEqualTo("2025-10-22");
        assertMet(practice(before, EP1, "J"), 9);

        records.add(outcome(P1, OUTCOME));
        RuleOutcome after = computeNovember(new C3Pack(), records);
        assertThat(episodeRow(after, EP1).reasonCode()).isEqualTo(RECORDED);
        assertThat(episodeRow(after, EP1).eventDate()).isEqualTo("2025-09-28");
        assertNotMet(practice(after, EP1, "J"));
    }

    @Test
    void ct65_anOutcomeRecordedAfterDumPlus294IsAmbiguous() {
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(anchor(P1, dum(56), DUM));
        records.add(outcome(P1, dum(300)));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        EvidenceItem row = episodeRow(outcome, EP1);
        assertThat(row.decision()).isEqualTo(EvidenceDecision.EXCLUDED);
        assertThat(row.reasonCode()).isEqualTo("AMBIGUIDADE_AMB_C3_05");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.ZERO);
        assertThat(outcome.result().valueExact()).isNull();
    }

    @Test
    void ct69_anEpisodePregnantAndPuerperalInTheSameMonthCountsOnce() {
        LocalDate lmp = LocalDate.of(2025, 2, 1);
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(anchor(P1, lmp.plusDays(56), lmp));
        records.add(outcome(P1, LocalDate.of(2025, 11, 10)));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertThat(outcome.evidence())
                .filteredOn(e -> e.decision() == EvidenceDecision.ELIGIBLE)
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.subjectKey()).isEqualTo(episodeKey(P1, lmp));
                    assertThat(e.eventDate()).isEqualTo("2025-11-10");
                });
        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.ONE);
    }

    // ---- MET-08: pregnancies of one person never exchange evidence ----

    @Test
    void met08_ct66_ct41_twoPregnanciesOfOnePersonDoNotShareEvidence() {
        YearMonth july = YearMonth.of(2025, 7);
        RuleOutcome outcome = conventionPack().compute(dataset(july, twoPregnancies()), context(july));

        String first = episodeKey(P1, LocalDate.of(2024, 11, 1));
        String second = episodeKey(P1, LocalDate.of(2025, 7, 10));
        assertThat(episodeRow(outcome, first).decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
        assertThat(episodeRow(outcome, first).reasonCode()).isEqualTo(RECORDED);
        assertThat(episodeRow(outcome, second).decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
        assertThat(episodeRow(outcome, second).reasonCode()).isEqualTo(SUBSTITUTE);
        assertMet(practice(outcome, first, "F"), 9);
        assertMet(practice(outcome, first, "G"), 9);
        assertNotMet(practice(outcome, second, "F"));
        assertNotMet(practice(outcome, second, "G"));
        assertThat(supporting(outcome, second, "F")).isEmpty();
        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.TWO);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
    }

    @Test
    void met08_ct67_anAbortionCodeInTheSecondPregnancyExcludesOnlyIt() {
        YearMonth july = YearMonth.of(2025, 7);
        List<Record> records = twoPregnancies();
        records.add(care(P1, LocalDate.of(2025, 7, 25)).cid("O03").build());
        RuleOutcome outcome = conventionPack().compute(dataset(july, records), context(july));

        String first = episodeKey(P1, LocalDate.of(2024, 11, 1));
        EvidenceItem second = episodeRow(outcome, episodeKey(P1, LocalDate.of(2025, 7, 10)));
        assertThat(second.decision()).isEqualTo(EvidenceDecision.EXCLUDED);
        assertThat(second.reasonCode()).isEqualTo("EXCLUIDO_ABORTO");
        assertThat(second.eventDate()).isEqualTo("2025-07-25");
        assertThat(episodeRow(outcome, first).decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
        assertMet(practice(outcome, first, "F"), 9);
        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.ONE);
    }

    @Test
    void amb07_anAbortionCodeInThePuerperiumIsAmbiguous() {
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(anchor(P1, dum(56), DUM));
        records.add(outcome(P1, OUTCOME));
        records.add(care(P1, OUTCOME.plusDays(10)).cid("O03").build());
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertThat(episodeRow(outcome, EP1).reasonCode()).isEqualTo("AMBIGUIDADE_AMB_C3_07");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
    }

    // ---- cohort ambiguities ----

    @Test
    void ct68_anAbortionSubcategoryMatchingOnlyByPrefixIsAmbiguous() {
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(anchor(P1, dum(56), DUM));
        records.add(care(P1, dum(120)).cid("O03.9").build());
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        EvidenceItem row = episodeRow(outcome, EP1);
        assertThat(row.decision()).isEqualTo(EvidenceDecision.EXCLUDED);
        assertThat(row.reasonCode()).isEqualTo("AMBIGUIDADE_AMB_C3_08");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
    }

    @Test
    void ct70_dumAndGestationalAgeThatMoveTheTwelfthWeekAreAmbiguous() {
        LocalDate consultDay = LocalDate.of(2025, 4, 5); // DUM+94 by the DUM, IG 10s by the IG field
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(anchor(P1, consultDay, DUM));
        records.add(care(P1, consultDay)
                .cbo(C3Fixtures.DOCTOR)
                .ciap(PREGNANCY_CIAP)
                .gestationalWeeks(10)
                .build());
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertThat(outcome.evidence())
                .filteredOn(e -> EP1.equals(e.subjectKey()))
                .anySatisfy(e -> assertThat(e.reasonCode()).isEqualTo("AMBIGUIDADE_AMB_C3_03"));
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
    }

    @Test
    void ct71_aPregnancyCodeWithoutDumOrGestationalAgeIsAmbiguous() {
        assertWithoutDumIsAmbiguous("Z34");
    }

    @Test
    void ct73_aCodeOfBothListsWithoutDatesIsAmbiguous() {
        assertWithoutDumIsAmbiguous("O10");
    }

    @Test
    void amb03_gestationalAgeAloneAnchorsTheEpisode() {
        List<Record> records = new ArrayList<>(linked(P1, INE));
        var consult = care(P1, LocalDate.of(2025, 2, 26))
                .ciap(PREGNANCY_CIAP)
                .gestationalWeeks(8)
                .build();
        records.add(consult);
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        // DUM = 2025-02-26 − 56 days = 2025-01-01
        assertThat(episodeRow(outcome, EP1).decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
        assertMet(practice(outcome, EP1, "A"), 10);
        assertThat(outcome.evidence())
                .filteredOn(e -> "MARCO_DUM".equals(e.reasonCode()))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.eventDate()).isEqualTo("2025-01-01");
                    assertThat(e.sourceRef()).isEqualTo(consult.sourceRef());
                });
    }

    // ---- ENG-27: month ends and 29/02, counted in days ----

    @Test
    void eng27_dumOnLeapDayEndsTheSubstitutePregnancy294DaysLater() {
        YearMonth january = YearMonth.of(2025, 1);
        RuleOutcome outcome = new C3Pack().compute(dataset(january, leapDayPregnancy()), context(january));
        // 2024-02-29 + 294 days = 2024-12-19; D+42 = 2025-01-30, inside January
        EvidenceItem row = episodeRow(outcome, episodeKey(P1, LocalDate.of(2024, 2, 29)));
        assertThat(row.reasonCode()).isEqualTo(SUBSTITUTE);
        assertThat(row.eventDate()).isEqualTo("2024-12-19");
        assertThat(outcome.result().consolidationEligible()).isTrue();
    }

    @Test
    void eng27_theFortySecondDayInTheNextMonthLeavesDecemberOutOfTheMean() {
        YearMonth december = YearMonth.of(2024, 12);
        RuleOutcome outcome = new C3Pack().compute(dataset(december, leapDayPregnancy()), context(december));
        assertThat(episodeRow(outcome, episodeKey(P1, LocalDate.of(2024, 2, 29)))
                        .eventDate())
                .isEqualTo("2024-12-19");
        assertThat(outcome.result().consolidationEligible()).isFalse();
    }

    @Test
    void eng27_twelfthWeekCountsDaysAcrossTheLeapDay() {
        // DUM 2024-02-01: DUM+83 = 2024-04-24 (29 days in February), DUM+84 = 2024-04-25
        YearMonth may = YearMonth.of(2024, 5);
        LocalDate lmp = LocalDate.of(2024, 2, 1);
        String key = episodeKey(P1, lmp);

        RuleOutcome onDay83 =
                new C3Pack().compute(dataset(may, leapYearAnchor(LocalDate.of(2024, 4, 24), lmp)), context(may));
        assertMet(practice(onDay83, key, "A"), 10);

        RuleOutcome onDay84 =
                new C3Pack().compute(dataset(may, leapYearAnchor(LocalDate.of(2024, 4, 25), lmp)), context(may));
        assertAmbiguous(practice(onDay84, key, "A"), "01");
    }

    @Test
    void eng27_twentiethWeekCountsDaysAcrossTheLeapDay() {
        // DUM 2024-01-01: DUM+139 = 2024-05-19, DUM+140 = 2024-05-20
        YearMonth june = YearMonth.of(2024, 6);
        LocalDate lmp = LocalDate.of(2024, 1, 1);
        String key = episodeKey(P1, lmp);

        List<Record> onDay140 = leapYearAnchor(lmp.plusDays(56), lmp);
        onDay140.add(dtpa(P1, LocalDate.of(2024, 5, 20)));
        assertMet(practice(new C3Pack().compute(dataset(june, onDay140), context(june)), key, "F"), 9);

        List<Record> onDay139 = leapYearAnchor(lmp.plusDays(56), lmp);
        onDay139.add(dtpa(P1, LocalDate.of(2024, 5, 19)));
        assertAmbiguous(practice(new C3Pack().compute(dataset(june, onDay139), context(june)), key, "F"), "01");
    }

    // ---- helpers ----

    /** The anchor on DUM+56 and five more prenatal consultations: six distinct days. */
    private static List<Record> withSixPrenatalDays() {
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(anchor(P1, dum(56), DUM));
        for (int day : MORE_PRENATAL_DAYS.subList(0, 5)) {
            records.add(prenatal(P1, dum(day)));
        }
        return records;
    }

    /**
     * G1: DUM 2024-11-01, recorded outcome 2025-05-21 (D+41 = 2025-07-01), dTpa on DUM+150 and the
     * four agents on DUM+30. G2: DUM 2025-07-10 (after D1+42 = 2025-07-02), anchor on 2025-07-20,
     * nothing else. Both are active in July 2025.
     */
    private static List<Record> twoPregnancies() {
        LocalDate firstLmp = LocalDate.of(2024, 11, 1);
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(anchor(P1, LocalDate.of(2024, 12, 1), firstLmp));
        records.addAll(tests(P1, LocalDate.of(2024, 12, 1), SYPHILIS, HIV, HEPATITIS_B, HEPATITIS_C));
        records.add(dtpa(P1, LocalDate.of(2025, 3, 31)));
        records.add(outcome(P1, LocalDate.of(2025, 5, 21)));
        records.add(anchor(P1, LocalDate.of(2025, 7, 20), LocalDate.of(2025, 7, 10)));
        return records;
    }

    private static List<Record> leapDayPregnancy() {
        LocalDate lmp = LocalDate.of(2024, 2, 29);
        return leapYearAnchor(lmp.plusDays(56), lmp);
    }

    private static List<Record> leapYearAnchor(LocalDate consultDay, LocalDate lmp) {
        List<Record> records = new ArrayList<>(linked(P1, INE, LocalDate.of(2023, 6, 1)));
        records.add(anchor(P1, consultDay, lmp));
        return records;
    }

    private static void assertWithoutDumIsAmbiguous(String cid) {
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(care(P1, dum(100)).cid(cid).build());
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        EvidenceItem row = episodeRow(outcome, P1 + "#sem-dum");
        assertThat(row.decision()).isEqualTo(EvidenceDecision.EXCLUDED);
        assertThat(row.reasonCode()).isEqualTo("AMBIGUIDADE_AMB_C3_03");
        assertThat(outcome.evidence()).noneMatch(e -> e.decision() == EvidenceDecision.ELIGIBLE);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
        assertThat(outcome.result().valueExact()).isNull();
    }
}
