package esusdata.indicator.pack.c3;

import static esusdata.indicator.pack.c3.C3Fixtures.ACS;
import static esusdata.indicator.pack.c3.C3Fixtures.CNES;
import static esusdata.indicator.pack.c3.C3Fixtures.DTPA;
import static esusdata.indicator.pack.c3.C3Fixtures.DUM;
import static esusdata.indicator.pack.c3.C3Fixtures.HEPATITIS_B;
import static esusdata.indicator.pack.c3.C3Fixtures.HEPATITIS_C;
import static esusdata.indicator.pack.c3.C3Fixtures.HIV;
import static esusdata.indicator.pack.c3.C3Fixtures.IBGE;
import static esusdata.indicator.pack.c3.C3Fixtures.INE;
import static esusdata.indicator.pack.c3.C3Fixtures.LINKED_ON;
import static esusdata.indicator.pack.c3.C3Fixtures.NURSE;
import static esusdata.indicator.pack.c3.C3Fixtures.NURSING_TECHNICIAN;
import static esusdata.indicator.pack.c3.C3Fixtures.OTHER_INE;
import static esusdata.indicator.pack.c3.C3Fixtures.OUTCOME;
import static esusdata.indicator.pack.c3.C3Fixtures.PREGNANCY_CIAP;
import static esusdata.indicator.pack.c3.C3Fixtures.SUBSTITUTE_END;
import static esusdata.indicator.pack.c3.C3Fixtures.SYPHILIS;
import static esusdata.indicator.pack.c3.C3Fixtures.anchor;
import static esusdata.indicator.pack.c3.C3Fixtures.assertMet;
import static esusdata.indicator.pack.c3.C3Fixtures.assertNotMet;
import static esusdata.indicator.pack.c3.C3Fixtures.bloodPressure;
import static esusdata.indicator.pack.c3.C3Fixtures.care;
import static esusdata.indicator.pack.c3.C3Fixtures.collectivePressure;
import static esusdata.indicator.pack.c3.C3Fixtures.computeNovember;
import static esusdata.indicator.pack.c3.C3Fixtures.condition;
import static esusdata.indicator.pack.c3.C3Fixtures.dose;
import static esusdata.indicator.pack.c3.C3Fixtures.dtpa;
import static esusdata.indicator.pack.c3.C3Fixtures.dum;
import static esusdata.indicator.pack.c3.C3Fixtures.episodeKey;
import static esusdata.indicator.pack.c3.C3Fixtures.episodeRow;
import static esusdata.indicator.pack.c3.C3Fixtures.fullEpisode;
import static esusdata.indicator.pack.c3.C3Fixtures.linked;
import static esusdata.indicator.pack.c3.C3Fixtures.outcome;
import static esusdata.indicator.pack.c3.C3Fixtures.person;
import static esusdata.indicator.pack.c3.C3Fixtures.practice;
import static esusdata.indicator.pack.c3.C3Fixtures.puerperal;
import static esusdata.indicator.pack.c3.C3Fixtures.registration;
import static esusdata.indicator.pack.c3.C3Fixtures.registrationVersion;
import static esusdata.indicator.pack.c3.C3Fixtures.supporting;
import static esusdata.indicator.pack.c3.C3Fixtures.team;
import static esusdata.indicator.pack.c3.C3Fixtures.tests;
import static esusdata.indicator.pack.c3.C3Fixtures.visit;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalCareEvent;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalHomeVisit;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.SourceRef;
import esusdata.indicator.model.TeamResult;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Cases from the adversarial review of the C3 rule (fixes M1–M6 and minors) and the branches the
 * first test pass left uncovered. Competência 2025-11, cutoff 2025-11-30, unless said otherwise.
 */
class C3ReviewCasesTest {

    private static final String P1 = "gestante-1";
    private static final String P2 = "gestante-2";
    private static final String EP1 = episodeKey(P1, DUM);
    private static final LocalDate NOVEMBER_FIRST = LocalDate.of(2025, 11, 1);
    private static final String EARLY = episodeKey(P1, LocalDate.of(2025, 9, 11));

    /** The person linked to {@link C3Fixtures#INE} with an anchor consultation on {@code dum + 56}. */
    private static List<Record> pregnancy(String personKey, LocalDate dum) {
        List<Record> records = new ArrayList<>(linked(personKey, INE));
        records.add(anchor(personKey, dum.plusDays(56), dum));
        return records;
    }

    private static RuleOutcome compute(List<Record> records) {
        return computeNovember(new C3Pack(), records);
    }

    private static boolean hasSubject(RuleOutcome outcome, String key) {
        return outcome.evidence().stream().anyMatch(e -> key.equals(e.subjectKey()));
    }

    // ---- M1: readings that agree on the decision but not on its date ----

    @Test
    void m1_theEarliestAbortionCodeOfThePregnancyDatesTheExclusion() {
        List<Record> records = pregnancy(P1, DUM);
        records.add(care(P1, dum(60)).ciap(PREGNANCY_CIAP).lmp(dum(20)).build());
        records.add(care(P1, dum(10)).cid("O03").build());
        records.add(care(P1, dum(40)).ciap("W82").build());
        EvidenceItem row = episodeRow(compute(records), EP1);
        assertThat(row.decision()).isEqualTo(EvidenceDecision.EXCLUDED);
        assertThat(row.reasonCode()).isEqualTo("EXCLUIDO_ABORTO");
        assertThat(row.eventDate()).isEqualTo(dum(10).toString());
    }

    @Test
    void m1_anAbortionCodeBeforeTheDumOfTheFirstRecordDoesNotExclude() {
        // AMB-C3-03 (i): the DUM of the first record opens the window; a code before it is another pregnancy's
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(care(P1, dum(30)).ciap(PREGNANCY_CIAP).lmp(dum(20)).build());
        records.add(anchor(P1, dum(56), DUM)); // a later record with an earlier DUM: same pregnancy
        records.add(care(P1, dum(10)).cid("O03").build());
        EvidenceItem row = episodeRow(compute(records), episodeKey(P1, dum(20)));
        assertThat(row.decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
    }

    // ---- M2: codes of both lists ----

    /** An older pregnancy (DUM 2024-02-01, coverage to 2025-01-02) and the current one (DUM 2025-03-01). */
    private static List<Record> twoPregnancies() {
        List<Record> records = pregnancy(P1, LocalDate.of(2025, 3, 1));
        records.add(anchor(P1, LocalDate.of(2024, 3, 1), LocalDate.of(2024, 2, 1)));
        return records;
    }

    @Test
    void m2_aPuerperalCodeThatIsOnlyAPregnancyPrefixIsExplainedByAnEarlierDum() {
        List<Record> records = twoPregnancies();
        records.add(care(P1, LocalDate.of(2025, 2, 1)).cid("O26.6").build());
        assertThat(hasSubject(compute(records), P1 + "#sem-dum")).isFalse();
    }

    @Test
    void m2_aPregnancyPrefixCodeOutsideEveryEpisodeIsWithoutDum() {
        List<Record> records = twoPregnancies();
        records.add(care(P1, LocalDate.of(2025, 2, 1)).cid("O26.8").build());
        EvidenceItem row = episodeRow(compute(records), P1 + "#sem-dum");
        assertThat(row.decision()).isEqualTo(EvidenceDecision.EXCLUDED);
        assertThat(row.reasonCode()).isEqualTo("EXCLUIDO_SEM_DUM_NEM_IG");
    }

    @Test
    void puerperalCodeBeforeAnyDumIsWithoutDum() {
        List<Record> records = pregnancy(P1, DUM);
        records.add(puerperal(P1, LocalDate.of(2024, 12, 15)));
        RuleOutcome outcome = compute(records);
        EvidenceItem row = episodeRow(outcome, P1 + "#sem-dum");
        assertThat(row.eventDate()).isEqualTo("2024-12-15");
        assertThat(row.reasonCode()).isEqualTo("EXCLUIDO_SEM_DUM_NEM_IG");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.ONE);
    }

    @Test
    void withoutDumSubjectFollowsTheLinkExclusionFirst() {
        List<Record> records = new ArrayList<>();
        records.add(person(P1, null));
        records.add(registration(P1, LINKED_ON, INE, "136"));
        records.add(care(P1, dum(100)).cid("Z34").build());
        RuleOutcome outcome = compute(records);
        assertThat(episodeRow(outcome, P1 + "#sem-dum").reasonCode()).isEqualTo("INTERROMPIDO_MUDANCA_TERRITORIO");
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
    }

    @Test
    void invalidGestationalAgeTextGivesNoDum() {
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(new CanonicalCareEvent(
                CanonicalFixtures.ref("tb_fat_atendimento_individual"),
                IBGE,
                P1,
                dum(100).toString(),
                "INDIVIDUAL",
                NURSE,
                CNES,
                INE,
                null,
                null,
                false,
                List.of(PREGNANCY_CIAP),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                null,
                null,
                null,
                "dez",
                null,
                null));
        RuleOutcome outcome = compute(records);
        assertThat(episodeRow(outcome, P1 + "#sem-dum").reasonCode()).isEqualTo("EXCLUIDO_SEM_DUM_NEM_IG");
    }

    // ---- M3: a late recorded outcome is never overridden by the LPC ----

    @Test
    void m3_lateRecordedOutcomeIsIgnoredAndTheLpcResolutionInRangeEndsThePregnancy() {
        // AMB-C3-05: an outcome after DUM+294 is ignored; the resolution of W78 in range decides
        List<Record> records = pregnancy(P1, DUM);
        records.add(condition(P1, "CIAP2", PREGNANCY_CIAP, dum(56), "2", dum(273)));
        records.add(outcome(P1, dum(304)));
        EvidenceItem row = episodeRow(compute(records), EP1);
        assertThat(row.reasonCode()).isEqualTo("ELEGIVEL_DESFECHO_RESOLUCAO_LPC");
        assertThat(row.eventDate()).isEqualTo(dum(273).toString());
    }

    @Test
    void lpcResolutionInRangeEndsThePregnancy() {
        List<Record> records = pregnancy(P1, DUM);
        records.add(condition(P1, "CIAP2", PREGNANCY_CIAP, dum(56), "2", dum(273)));
        EvidenceItem row = episodeRow(compute(records), EP1);
        assertThat(row.reasonCode()).isEqualTo("ELEGIVEL_DESFECHO_RESOLUCAO_LPC");
        assertThat(row.eventDate()).isEqualTo(dum(273).toString());
    }

    // ---- M5: a DUM derived from the IG within a week of a recorded DUM ----

    @Test
    void m5_theDumDerivedFromTheIgOfTheFirstRecordIsTheEpisodeDum() {
        // AMB-C3-03 (i): the first record (IG 10 on DUM+73, so DUM+3) opens the episode, whatever its CBO
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(anchor(P1, dum(86), DUM));
        records.add(
                care(P1, dum(73)).cbo(NURSING_TECHNICIAN).gestationalWeeks(10).build());
        RuleOutcome outcome = compute(records);
        assertThat(episodeRow(outcome, episodeKey(P1, dum(3))).decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
        assertThat(hasSubject(outcome, EP1)).isFalse();
        // the anchor on DUM+86 is on day 83 of that DUM: A met
        assertMet(practice(outcome, episodeKey(P1, dum(3)), "A"), 10);
    }

    // ---- M6: deduplication by whole record ----

    private static CanonicalHomeVisit visitWithRef(SourceRef ref, LocalDate date) {
        return new CanonicalHomeVisit(
                ref, IBGE, P1, date.toString(), ACS, CNES, INE, "1", List.of("ACOMP_GESTANTE"), null, null);
    }

    @Test
    void m6_distinctFactsSharingASourceReferenceAreKept() {
        SourceRef shared = new SourceRef("pec", "tb_fat_visita_domiciliar", "42");
        List<Record> records = pregnancy(P1, DUM);
        records.add(visitWithRef(shared, dum(150)));
        records.add(visitWithRef(shared, dum(160)));
        records.add(visitWithRef(shared, dum(170)));
        assertMet(practice(compute(records), EP1, "E"), 9);
    }

    @Test
    void m6_theSameRecordTwiceCountsOnce() {
        CanonicalHomeVisit repeated = visit(P1, dum(150), ACS);
        List<Record> records = pregnancy(P1, DUM);
        records.add(repeated);
        records.add(repeated);
        records.add(visit(P1, dum(160), ACS));
        assertNotMet(practice(compute(records), EP1, "E"));
    }

    // ---- minors ----

    /** A pregnancy with DUM 2025-09-11 and a recorded outcome on 2025-11-10 (D = DUM+60). */
    private static List<Record> earlyOutcome() {
        LocalDate dum = LocalDate.of(2025, 9, 11);
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(anchor(P1, dum.plusDays(10), dum));
        records.add(outcome(P1, dum.plusDays(60)));
        return records;
    }

    @Test
    void minor1_firstTrimesterRecordOnTheDayDCounts() {
        // AMB-C3-04: D is inclusive; the first trimester is truncated at D
        List<Record> records = earlyOutcome();
        records.addAll(tests(P1, LocalDate.of(2025, 11, 10), SYPHILIS, HIV, HEPATITIS_B, HEPATITIS_C));
        assertMet(practice(computeNovember(new C3Pack(), records), EARLY, "G"), 9);
    }

    @Test
    void minor1_firstTrimesterEndsAtD() {
        List<Record> records = earlyOutcome();
        records.addAll(tests(P1, LocalDate.of(2025, 11, 15), SYPHILIS, HIV, HEPATITIS_B, HEPATITIS_C));
        assertNotMet(practice(computeNovember(new C3Pack(), records), EARLY, "G"));
    }

    @Test
    void minor2_dtpaBeforeTheTwentiethWeekAfterAnEarlyOutcomeIsNotMet() {
        List<Record> records = earlyOutcome();
        records.add(dtpa(P1, LocalDate.of(2025, 11, 15))); // D+5 but DUM+65
        assertNotMet(practice(compute(records), EARLY, "F"));
    }

    @Test
    void dtpaAfterDPlus42IsNotMet() {
        List<Record> records = pregnancy(P1, DUM);
        records.add(dtpa(P1, SUBSTITUTE_END.plusDays(43)));
        assertNotMet(practice(compute(records), EP1, "F"));
    }

    @Test
    void minor3_lpcExclusionWithUnknownStatusDoesNotExclude() {
        List<Record> records = pregnancy(P1, DUM);
        records.add(condition(P1, "CID10", "O03", dum(100), null, null));
        assertThat(episodeRow(compute(records), EP1).decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
    }

    @Test
    void minor3_latentLpcExclusionExcludes() {
        List<Record> records = pregnancy(P1, DUM);
        records.add(condition(P1, "CID10", "O03", dum(100), "1", null));
        EvidenceItem row = episodeRow(compute(records), EP1);
        assertThat(row.reasonCode()).isEqualTo("EXCLUIDO_ABORTO");
        assertThat(row.eventDate()).isEqualTo(dum(100).toString());
    }

    @Test
    void conditionWithUnknownCodeSystemIsMatchedAgainstBothLists() {
        List<Record> records = pregnancy(P1, DUM);
        records.add(condition(P1, null, "W82", dum(100), "0", null));
        assertThat(episodeRow(compute(records), EP1).reasonCode()).isEqualTo("EXCLUIDO_ABORTO");
    }

    @Test
    void exclusionCodeOnARecordedOutcomeExcludes() {
        LocalDate dum = LocalDate.of(2025, 7, 28);
        LocalDate end = dum.plusDays(100); // 2025-11-05
        List<Record> records = pregnancy(P1, dum);
        records.add(outcome(P1, end, "O03"));
        EvidenceItem row = episodeRow(compute(records), episodeKey(P1, dum));
        assertThat(row.reasonCode()).isEqualTo("EXCLUIDO_ABORTO");
        assertThat(row.eventDate()).isEqualTo(end.toString());
    }

    @Test
    void b3_aResolvedPregnancyCodeOtherThanW78DoesNotEndThePregnancy() {
        // integration review B3 (replaces the AMB-C3-08 prefix path): only W78 resolved is the outcome
        List<Record> records = pregnancy(P1, DUM);
        records.add(condition(P1, "CID10", "O26.8", dum(56), "2", dum(200)));
        EvidenceItem row = episodeRow(compute(records), EP1);
        assertThat(row.reasonCode()).isEqualTo("ELEGIVEL_DATA_SUBSTITUTIVA_294D");
        assertThat(row.eventDate()).isEqualTo(SUBSTITUTE_END.toString());
    }

    @Test
    void minor5_anExcludedEpisodeReachingDPlus42DoesNotMakeTheMonthCount() {
        LocalDate dum = LocalDate.of(2024, 12, 15); // D = 2025-10-05, D+42 = 2025-11-16
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(care(P1, dum.plusDays(56)).lmp(dum).build()); // no 24 f code
        RuleOutcome outcome = compute(records);
        assertThat(episodeRow(outcome, episodeKey(P1, dum)).reasonCode()).isEqualTo("EXCLUIDO_SEM_CODIGO_GESTACAO");
        assertThat(outcome.result().consolidationEligible()).isFalse();
    }

    @Test
    void minor5_anEligibleEpisodeReachingDPlus42MakesTheMonthCount() {
        LocalDate dum = LocalDate.of(2024, 12, 15);
        RuleOutcome outcome = compute(pregnancy(P1, dum));
        assertThat(episodeRow(outcome, episodeKey(P1, dum)).decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
        assertThat(outcome.result().consolidationEligible()).isTrue();
    }

    @Test
    void minor6_anExcludedSubjectIsNotInTheDenominatorOfAnyComponent() {
        List<Record> records = new ArrayList<>(fullEpisode(P1));
        records.addAll(linked(P2, INE));
        records.add(care(P2, dum(56)).lmp(DUM).build()); // no 24 f code
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertThat(outcome.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(outcome.result().components()).hasSize(11).allSatisfy(c -> {
            assertThat(c.status()).isEqualTo(IndicatorStatus.COMPUTED);
            assertThat(c.value()).isNotNull();
            assertThat(c.numerator()).isEqualTo(BigInteger.ONE);
            assertThat(c.denominator()).isEqualTo(BigInteger.ONE);
        });
    }

    @Test
    void minor10_recordIdsCompareNumerically() {
        List<Record> records = new ArrayList<>();
        records.add(person(P1, null));
        records.add(registrationVersion(
                P1, LINKED_ON, INE, new SourceRef("pec", "tb_fat_cad_individual", "9"), false, false, false));
        records.add(registrationVersion(
                P1, LINKED_ON, OTHER_INE, new SourceRef("pec", "tb_fat_cad_individual", "10"), false, false, false));
        records.add(anchor(P1, dum(56), DUM));
        assertThat(episodeRow(compute(records), EP1).ine()).isEqualTo(OTHER_INE);
    }

    @Test
    void minor11_deathRowCarriesTheDeathDate() {
        List<Record> records = new ArrayList<>();
        records.add(person(P1, LocalDate.of(2025, 6, 1)));
        records.add(registration(P1, LINKED_ON, INE, null));
        records.add(anchor(P1, dum(56), DUM));
        EvidenceItem row = episodeRow(compute(records), EP1);
        assertThat(row.reasonCode()).isEqualTo("EXCLUIDO_OBITO");
        assertThat(row.eventDate()).isEqualTo("2025-06-01");
    }

    @Test
    void minor11_exit135RowCarriesTheRegistrationDate() {
        List<Record> records = new ArrayList<>();
        records.add(person(P1, null));
        records.add(registration(P1, LocalDate.of(2025, 5, 1), INE, "135"));
        records.add(anchor(P1, dum(56), DUM));
        EvidenceItem row = episodeRow(compute(records), EP1);
        assertThat(row.reasonCode()).isEqualTo("EXCLUIDO_OBITO");
        assertThat(row.eventDate()).isEqualTo("2025-05-01");
    }

    @Test
    void minor13_theSameDoseFromMivAndTranscriptionSupportsFOnce() {
        List<Record> records = pregnancy(P1, DUM);
        records.add(dose(P1, dum(200), DTPA, NURSE, false));
        records.add(dose(P1, dum(200), DTPA, NURSE, true));
        RuleOutcome outcome = compute(records);
        assertMet(practice(outcome, EP1, "F"), 9);
        assertThat(supporting(outcome, EP1, "F")).hasSize(1);
    }

    // ---- cohort branches ----

    @Test
    void onlyDPlus42InTheCompetenciaIsActiveAndEligible() {
        // AMB-C3-04: the puerperium reaches D+42 inclusive
        LocalDate dum = NOVEMBER_FIRST.minusDays(336); // D+42 = 2025-11-01
        EvidenceItem row = episodeRow(compute(pregnancy(P1, dum)), episodeKey(P1, dum));
        assertThat(row.decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
        assertThat(row.reasonCode()).isEqualTo("ELEGIVEL_DATA_SUBSTITUTIVA_294D");
    }

    @Test
    void anEpisodeWhoseDPlus42IsBeforeTheCompetenciaIsNotActive() {
        LocalDate dum = NOVEMBER_FIRST.minusDays(337); // D+42 = 2025-10-31
        assertThat(hasSubject(compute(pregnancy(P1, dum)), episodeKey(P1, dum))).isFalse();
    }

    @Test
    void theDumOfTheFirstRecordDecidesTheActivity() {
        // D+42 = 2025-10-31 by the DUM of the first record; a later record's DUM (+20) does not reopen it
        LocalDate dum = LocalDate.of(2025, 10, 31).minusDays(336);
        List<Record> records = pregnancy(P1, dum);
        records.add(care(P1, dum.plusDays(60))
                .ciap(PREGNANCY_CIAP)
                .lmp(dum.plusDays(20))
                .build());
        assertThat(hasSubject(compute(records), episodeKey(P1, dum))).isFalse();
    }

    @Test
    void refusedRegistrationHasNoLink() {
        List<Record> records = new ArrayList<>();
        records.add(person(P1, null));
        records.add(registrationVersion(
                P1, LINKED_ON, INE, new SourceRef("pec", "tb_fat_cad_individual", "1"), false, false, true));
        records.add(anchor(P1, dum(56), DUM));
        assertThat(episodeRow(compute(records), EP1).reasonCode()).isEqualTo("EXCLUIDO_SEM_VINCULO");
    }

    @Test
    void simplifiedAndInactiveVersionsAreIgnored() {
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(registrationVersion(
                P1, dum(10), OTHER_INE, new SourceRef("pec", "tb_fat_cad_individual", "s"), true, false, false));
        records.add(registrationVersion(
                P1, dum(20), OTHER_INE, new SourceRef("pec", "tb_fat_cad_individual", "i"), false, true, false));
        records.add(anchor(P1, dum(56), DUM));
        assertThat(episodeRow(compute(records), EP1).ine()).isEqualTo(INE);
    }

    @Test
    void registrationAfterTheCutoffIsNoLink() {
        List<Record> records = new ArrayList<>();
        records.add(person(P1, null));
        records.add(registration(P1, LocalDate.of(2025, 12, 5), INE, null));
        records.add(anchor(P1, dum(56), DUM));
        assertThat(episodeRow(compute(records), EP1).reasonCode()).isEqualTo("EXCLUIDO_SEM_VINCULO");
    }

    @Test
    void teamTypeObservedWithoutDateProvesNothing() {
        // integration review I5: an observation without a date does not prove the type
        List<Record> records = pregnancy(P1, DUM);
        records.add(team(INE, "76", null));
        assertNotMet(practice(compute(records), EP1, "E"));
    }

    @Test
    void teamTypeObservedAfterTheCutoffDoesNotCount() {
        List<Record> records = pregnancy(P1, DUM);
        records.add(team(INE, "76", "2025-12-15"));
        assertNotMet(practice(compute(records), EP1, "E"));
    }

    @Test
    void twoReadingsWithARecordedOutcomeShareIt() {
        List<Record> records = pregnancy(P1, DUM);
        records.add(care(P1, dum(60)).ciap(PREGNANCY_CIAP).lmp(dum(20)).build());
        records.add(outcome(P1, OUTCOME));
        EvidenceItem row = episodeRow(compute(records), EP1);
        assertThat(row.reasonCode()).isEqualTo("ELEGIVEL_DESFECHO_REGISTRADO");
        assertThat(row.eventDate()).isEqualTo(OUTCOME.toString());
    }

    // ---- practice branches ----

    @Test
    void collectiveBloodPressureWithTheFichaCodesCounts() {
        List<Record> records = pregnancy(P1, DUM);
        for (int i = 0; i < 6; i++) {
            records.add(bloodPressure(P1, dum(101 + i), NURSE));
        }
        records.add(collectivePressure(P1, dum(120), "05", "20")); // LEDI 20 = antropometria (ficha 01)
        assertMet(practice(compute(records), EP1, "C"), 9);
    }

    @Test
    void collectiveBloodPressureWithOnlyTheActivityDoesNotCount() {
        List<Record> records = pregnancy(P1, DUM);
        for (int i = 0; i < 6; i++) {
            records.add(bloodPressure(P1, dum(101 + i), NURSE));
        }
        records.add(collectivePressure(P1, dum(120), "5"));
        assertNotMet(practice(compute(records), EP1, "C"));
    }

    @Test
    void thirdTrimesterRecordOnTheDayDCounts() {
        List<Record> records = pregnancy(P1, DUM);
        records.add(outcome(P1, OUTCOME));
        records.addAll(tests(P1, OUTCOME, SYPHILIS, HIV));
        assertMet(practice(computeNovember(new C3Pack(), records), EP1, "H"), 9);
    }

    @Test
    void theFirstConsultationOfEIsTheFirstWithAPregnancyCode() {
        List<Record> records = pregnancy(P1, DUM); // first prenatal consultation on DUM+56
        records.add(care(P1, dum(30)).cid("Z00").build()); // a consultation with another code: not the first
        records.add(visit(P1, dum(40), ACS)); // before the first prenatal consultation
        records.add(visit(P1, dum(150), ACS));
        records.add(visit(P1, dum(160), ACS));
        assertNotMet(practice(compute(records), EP1, "E"));
    }

    @Test
    void unmappedPuerperalCiapIsNotAPuerperalConsultation() {
        List<Record> records = pregnancy(P1, DUM);
        records.add(care(P1, SUBSTITUTE_END.plusDays(10)).ciap("48").build());
        assertThat(C3Codes.PUERPERIUM_CIAP).doesNotContain("48", "49");
        assertNotMet(practice(compute(records), EP1, "I"));
    }

    // ---- results ----

    @Test
    void teamWithOnlyAnExcludedSubjectHasNoResult() {
        List<Record> records = new ArrayList<>(fullEpisode(P1));
        records.addAll(linked(P2, OTHER_INE));
        records.add(care(P2, dum(56)).lmp(DUM).build()); // no 24 f code
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertThat(outcome.teams()).extracting(TeamResult::ine).containsExactly(INE);
        assertThat(episodeRow(outcome, episodeKey(P2, DUM)).reasonCode()).isEqualTo("EXCLUIDO_SEM_CODIGO_GESTACAO");
    }
}
