package esusdata.indicator.pack.c3;

import static esusdata.indicator.pack.c3.C3Fixtures.ALL_PRACTICES;
import static esusdata.indicator.pack.c3.C3Fixtures.CNES;
import static esusdata.indicator.pack.c3.C3Fixtures.DUM;
import static esusdata.indicator.pack.c3.C3Fixtures.INE;
import static esusdata.indicator.pack.c3.C3Fixtures.NOVEMBER;
import static esusdata.indicator.pack.c3.C3Fixtures.OTHER_IBGE;
import static esusdata.indicator.pack.c3.C3Fixtures.OTHER_INE;
import static esusdata.indicator.pack.c3.C3Fixtures.OUTCOME;
import static esusdata.indicator.pack.c3.C3Fixtures.SUBSTITUTE_END;
import static esusdata.indicator.pack.c3.C3Fixtures.anchor;
import static esusdata.indicator.pack.c3.C3Fixtures.assertAmbiguous;
import static esusdata.indicator.pack.c3.C3Fixtures.assertMet;
import static esusdata.indicator.pack.c3.C3Fixtures.care;
import static esusdata.indicator.pack.c3.C3Fixtures.computeNovember;
import static esusdata.indicator.pack.c3.C3Fixtures.context;
import static esusdata.indicator.pack.c3.C3Fixtures.conventionPack;
import static esusdata.indicator.pack.c3.C3Fixtures.dataset;
import static esusdata.indicator.pack.c3.C3Fixtures.dental;
import static esusdata.indicator.pack.c3.C3Fixtures.dum;
import static esusdata.indicator.pack.c3.C3Fixtures.episode;
import static esusdata.indicator.pack.c3.C3Fixtures.episodeKey;
import static esusdata.indicator.pack.c3.C3Fixtures.episodeRow;
import static esusdata.indicator.pack.c3.C3Fixtures.fullEpisode;
import static esusdata.indicator.pack.c3.C3Fixtures.linked;
import static esusdata.indicator.pack.c3.C3Fixtures.outcome;
import static esusdata.indicator.pack.c3.C3Fixtures.person;
import static esusdata.indicator.pack.c3.C3Fixtures.practice;
import static esusdata.indicator.pack.c3.C3Fixtures.registration;
import static esusdata.indicator.pack.c3.C3Fixtures.supporting;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.Classification;
import esusdata.indicator.model.ComponentSpec;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.IndicatorResult.IndicatorStatus;
import esusdata.indicator.model.MonthlyEligibility;
import esusdata.indicator.model.PackDescriptor;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.ReleaseGates;
import esusdata.indicator.model.ResultComponent;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.SourceRef;
import esusdata.indicator.model.TeamResult;
import esusdata.indicator.model.ValueKind;
import java.math.BigInteger;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * C3 (cuidado na gestação e puerpério) as a pack: descriptor, requirements, bands, the result
 * shape (MET-03/04/20, CT-C3-01..08), the evidence that rebuilds the population (ENG-36), gates,
 * teams and deduplication (MET-32). Written from {@code docs/metodologia/c3-gestacao-puerperio.md}
 * and the rule contract, never from the implementation.
 */
class C3PackTest {

    private static final String P1 = "gestante-1";
    private static final String P2 = "gestante-2";
    private static final String P3 = "gestante-3";
    private static final String P4 = "gestante-4";
    private static final String P5 = "gestante-5";
    private static final BigInteger HUNDRED = BigInteger.valueOf(100);

    // ---- descriptor and requirements ----

    @Test
    void descriptor_declaresElevenPracticesTheLimitationsAndNoCompleteGate() {
        PackDescriptor d = new C3Pack().descriptor();
        assertThat(d.id()).isEqualTo(C3Pack.ID).isEqualTo("c3-gestacao-puerperio");
        assertThat(d.ruleVersion()).isEqualTo("c3-gestacao-puerperio@0.1.0");
        assertThat(d.code()).isEqualTo("C3");
        assertThat(d.valueKind()).isEqualTo(ValueKind.SCORE);
        assertThat(d.monthlyEligibility()).isEqualTo(MonthlyEligibility.MONTHS_WITH_COHORT_EVENT);
        assertThat(d.components())
                .extracting(ComponentSpec::code)
                .containsExactly("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K");
        assertThat(d.components())
                .extracting(ComponentSpec::weight)
                .containsExactly(
                        BigInteger.TEN, nine(), nine(), nine(), nine(), nine(), nine(), nine(), nine(), nine(), nine());
        assertThat(d.gates()).isEqualTo(ReleaseGates.noneComplete());
        assertThat(d.executionEnabled()).isFalse();
        assertThat(d.standingLimitations()).isNotEmpty().noneMatch(l -> l.contains("Regra em implementação"));
        // L1: the team type (eAP 76) is usually absent; L2: the outcome date is usually absent.
        assertThat(d.standingLimitations())
                .anySatisfy(l -> assertThat(l).containsAnyOf("L1", "tipo de equipe", "tipo da equipe"));
        assertThat(d.standingLimitations())
                .anySatisfy(l -> assertThat(l).containsAnyOf("L2", "data de desfecho", "Data de desfecho"));
    }

    @Test
    void requirements_readTenCapabilitiesWithThePacksOwnCodeLists() {
        DataRequirements r = new C3Pack().requirements(NOVEMBER);
        assertThat(r.canonicalSchemaVersion()).isEqualTo(DataRequirements.V2);
        assertThat(r.parts())
                .extracting(PartRequirement::capability)
                .containsExactlyInAnyOrder(
                        Capabilities.CITIZEN,
                        Capabilities.INDIVIDUAL_REGISTRATION,
                        Capabilities.CONDITION_LIST,
                        Capabilities.CARE_ENCOUNTER,
                        Capabilities.DENTAL_ENCOUNTER,
                        Capabilities.PROCEDURE_PERFORMED,
                        Capabilities.EXAM_REQUEST_EVALUATION,
                        Capabilities.HOME_VISIT,
                        Capabilities.MEASUREMENT_RECORD,
                        Capabilities.IMMUNIZATION_HISTORY);
        // EMENDA 1: condition_list right after individual_registration
        assertThat(new C3Pack().descriptor().requiredCapabilities())
                .hasSize(10)
                .containsSubsequence(Capabilities.INDIVIDUAL_REGISTRATION, Capabilities.CONDITION_LIST);
        PartRequirement conditions = part(r, Capabilities.CONDITION_LIST);
        assertThat(new HashSet<>(conditions.arrayParams().get(Capabilities.CIAP_CODES)))
                .isEqualTo(union(C3Codes.PREGNANCY_CIAP, C3Codes.PUERPERIUM_CIAP, C3Codes.EXCLUSION_CIAP));
        assertThat(new HashSet<>(conditions.arrayParams().get(Capabilities.CID_CODES)))
                .isEqualTo(union(C3Codes.PREGNANCY_CID, C3Codes.PUERPERIUM_CID, C3Codes.EXCLUSION_CID))
                .contains("O021", "Z303")
                .doesNotContain("O02.1");

        assertThat(part(r, Capabilities.PROCEDURE_PERFORMED).arrayParams().get(Capabilities.PROCEDURE_CODES))
                .hasSize(32)
                .containsExactlyInAnyOrderElementsOf(C3Codes.PROCEDURE_SIGTAP);
        assertThat(part(r, Capabilities.EXAM_REQUEST_EVALUATION).arrayParams().get(Capabilities.PROCEDURE_CODES))
                .hasSize(22)
                .containsExactlyInAnyOrderElementsOf(C3Codes.TEST_SIGTAP);
        assertThat(part(r, Capabilities.IMMUNIZATION_HISTORY).arrayParams().get(Capabilities.IMMUNOBIOLOGICAL_CODES))
                .containsExactly("57");
        for (String capability : List.of(
                Capabilities.CARE_ENCOUNTER,
                Capabilities.DENTAL_ENCOUNTER,
                Capabilities.HOME_VISIT,
                Capabilities.MEASUREMENT_RECORD,
                Capabilities.INDIVIDUAL_REGISTRATION)) {
            assertThat(part(r, capability).arrayParams()).as(capability).isEmpty();
        }
    }

    @Test
    void requirements_careDataCoverThirteenCivilMonthsAndRegistrationTwentyFour() {
        DataRequirements r = new C3Pack().requirements(NOVEMBER);
        for (String capability : List.of(
                Capabilities.CARE_ENCOUNTER,
                Capabilities.DENTAL_ENCOUNTER,
                Capabilities.PROCEDURE_PERFORMED,
                Capabilities.EXAM_REQUEST_EVALUATION,
                Capabilities.HOME_VISIT,
                Capabilities.MEASUREMENT_RECORD,
                Capabilities.IMMUNIZATION_HISTORY,
                Capabilities.CONDITION_LIST)) {
            PartRequirement p = part(r, capability);
            // DUM up to 336 days before the first day of the competência
            assertThat(p.periodStart()).as(capability).isEqualTo(LocalDate.of(2024, 11, 1));
            assertThat(p.periodEndExclusive()).as(capability).isEqualTo(LocalDate.of(2025, 12, 1));
        }
        PartRequirement registration = part(r, Capabilities.INDIVIDUAL_REGISTRATION);
        assertThat(registration.periodStart()).isEqualTo(LocalDate.of(2023, 12, 1));
        assertThat(registration.periodEndExclusive()).isEqualTo(LocalDate.of(2025, 12, 1));
        // the ficha has no age band: births of the last 130 years
        assertThat(registration.dateParams().get(PartRequirement.BIRTH_DATE_FROM))
                .isBetween(LocalDate.of(1895, 1, 1), LocalDate.of(1896, 1, 1));
    }

    // ---- ENG-25: band boundaries, exact and ±1/10^6 ----

    @Test
    void eng25_bandBoundariesAreDecidedExactly() {
        C3Pack pack = new C3Pack();
        assertThat(pack.classify(ExactRatio.of(0, 1))).contains(Classification.REGULAR);
        assertThat(pack.classify(ExactRatio.of(25, 1))).contains(Classification.REGULAR);
        assertThat(pack.classify(ExactRatio.of(24_999_999, 1_000_000))).contains(Classification.REGULAR);
        assertThat(pack.classify(ExactRatio.of(25_000_001, 1_000_000))).contains(Classification.SUFICIENTE);
        assertThat(pack.classify(ExactRatio.of(50, 1))).contains(Classification.SUFICIENTE);
        assertThat(pack.classify(ExactRatio.of(50_000_001, 1_000_000))).contains(Classification.BOM);
        assertThat(pack.classify(ExactRatio.of(49_999_999, 1_000_000))).contains(Classification.SUFICIENTE);
        assertThat(pack.classify(ExactRatio.of(75, 1))).contains(Classification.BOM);
        assertThat(pack.classify(ExactRatio.of(74_999_999, 1_000_000))).contains(Classification.BOM);
        assertThat(pack.classify(ExactRatio.of(75_000_001, 1_000_000))).contains(Classification.OTIMO);
        assertThat(pack.classify(ExactRatio.of(100, 1))).contains(Classification.OTIMO);
        assertThat(pack.classify(ExactRatio.of(100_000_001, 1_000_000))).isEmpty();
    }

    // ---- MET-20 / CT-C3-01: all eleven practices = 100 points, never ×100 ----

    @Test
    void met20_allElevenPracticesScoreOneHundredOnTheZeroToHundredScale() {
        RuleOutcome outcome = computeNovember(conventionPack(), fullEpisode(P1));
        IndicatorResult result = outcome.result();

        assertThat(result.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(result.valueKind()).isEqualTo(ValueKind.SCORE);
        assertThat(result.numerator()).isEqualTo(HUNDRED);
        assertThat(result.denominator()).isEqualTo(BigInteger.ONE);
        assertThat(result.valueExact()).isEqualTo(ExactRatio.of(100, 1));
        assertThat(result.valueText()).isEqualTo("100.0000");
        assertThat(result.classification()).isEqualTo(Classification.OTIMO);
        String key = episodeKey(P1, DUM);
        assertThat(episodeRow(outcome, key).points()).isEqualTo(HUNDRED);
        for (int i = 0; i < ALL_PRACTICES.length(); i++) {
            char code = ALL_PRACTICES.charAt(i);
            assertMet(practice(outcome, key, String.valueOf(code)), code == 'A' ? 10 : 9);
        }
        assertThat(result.components()).hasSize(11).allSatisfy(c -> {
            assertThat(c.numerator()).isEqualTo(BigInteger.ONE);
            assertThat(c.denominator()).isEqualTo(BigInteger.ONE);
            assertThat(c.status()).isEqualTo(IndicatorStatus.COMPUTED);
        });
    }

    @Test
    void met20_withoutTrimesterConventionGAndHAreAmbiguousAndTheOtherNineDecided() {
        RuleOutcome outcome = computeNovember(new C3Pack(), fullEpisode(P1));
        IndicatorResult result = outcome.result();

        assertThat(result.status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
        assertThat(result.numerator()).isNull();
        assertThat(result.valueExact()).isNull();
        assertThat(result.valueText()).isNull();
        assertThat(result.classification()).isNull();
        assertThat(result.denominator()).isEqualTo(BigInteger.ONE);
        assertThat(result.limitations()).anySatisfy(l -> assertThat(l).containsAnyOf("AMB-C3-02", "AMB_C3_02"));
        String key = episodeKey(P1, DUM);
        assertThat(episodeRow(outcome, key).decision()).isEqualTo(EvidenceDecision.ELIGIBLE);
        assertThat(episodeRow(outcome, key).points()).isNull();
        for (char code : List.of('A', 'B', 'C', 'D', 'E', 'F', 'I', 'J', 'K')) {
            assertMet(practice(outcome, key, String.valueOf(code)), code == 'A' ? 10 : 9);
        }
        assertAmbiguous(practice(outcome, key, "G"), "02");
        assertAmbiguous(practice(outcome, key, "H"), "02");
        assertThat(result.components())
                .filteredOn(c -> "G".equals(c.code()) || "H".equals(c.code()))
                .hasSize(2)
                .allSatisfy(c -> {
                    assertThat(c.status()).isEqualTo(IndicatorStatus.RULE_AMBIGUITY);
                    assertThat(c.value()).isNull();
                });
    }

    // ---- CT-C3-02..07: points per episode and the team mean ----

    @Test
    void ct02_onlyAScoresTen() {
        RuleOutcome outcome = computeNovember(conventionPack(), episode(P1, INE, "A"));
        assertThat(outcome.result().numerator()).isEqualTo(BigInteger.TEN);
        assertThat(outcome.result().valueExact()).isEqualByComparingTo(ExactRatio.of(10, 1));
        assertThat(outcome.result().classification()).isEqualTo(Classification.REGULAR);
    }

    @Test
    void ct03_allButAScoresNinety() {
        RuleOutcome outcome = computeNovember(conventionPack(), episode(P1, INE, "BCDEFGHIJK"));
        assertThat(outcome.result().numerator()).isEqualTo(BigInteger.valueOf(90));
        assertThat(outcome.result().valueExact()).isEqualByComparingTo(ExactRatio.of(90, 1));
        assertThat(outcome.result().classification()).isEqualTo(Classification.OTIMO);
    }

    @Test
    void ct04_hundredAndFortySixAverageSeventyThreeBom() {
        List<Record> records = new ArrayList<>(fullEpisode(P1));
        records.addAll(episode(P2, INE, "ABCDE"));
        IndicatorResult result = computeNovember(conventionPack(), records).result();
        assertThat(result.numerator()).isEqualTo(BigInteger.valueOf(146));
        assertThat(result.denominator()).isEqualTo(BigInteger.TWO);
        assertThat(result.valueExact()).isEqualByComparingTo(ExactRatio.of(73, 1));
        assertThat(result.valueText()).isEqualTo("73.0000");
        assertThat(result.classification()).isEqualTo(Classification.BOM);
    }

    @Test
    void ct05_exactlySeventyFiveIsBomNotOtimo() {
        List<Record> records = new ArrayList<>();
        records.addAll(fullEpisode(P1));
        records.addAll(fullEpisode(P2));
        records.addAll(fullEpisode(P3));
        records.addAll(episode(P4, INE, ""));
        IndicatorResult result = computeNovember(conventionPack(), records).result();
        assertThat(result.numerator()).isEqualTo(BigInteger.valueOf(300));
        assertThat(result.denominator()).isEqualTo(BigInteger.valueOf(4));
        assertThat(result.valueExact()).isEqualByComparingTo(ExactRatio.of(75, 1));
        assertThat(result.classification()).isEqualTo(Classification.BOM);
    }

    @Test
    void ct06_seventySixIsOtimo() {
        List<Record> records = new ArrayList<>();
        records.addAll(fullEpisode(P1));
        records.addAll(fullEpisode(P2));
        records.addAll(episode(P3, INE, "ABC"));
        IndicatorResult result = computeNovember(conventionPack(), records).result();
        assertThat(result.numerator()).isEqualTo(BigInteger.valueOf(228));
        assertThat(result.valueExact()).isEqualByComparingTo(ExactRatio.of(76, 1));
        assertThat(result.classification()).isEqualTo(Classification.OTIMO);
    }

    @Test
    void ct07_fiftyIsSuficienteAndTwentyFiveIsRegular() {
        List<Record> two = new ArrayList<>(fullEpisode(P1));
        two.addAll(episode(P2, INE, ""));
        IndicatorResult fifty = computeNovember(conventionPack(), two).result();
        assertThat(fifty.valueExact()).isEqualByComparingTo(ExactRatio.of(50, 1));
        assertThat(fifty.classification()).isEqualTo(Classification.SUFICIENTE);

        List<Record> four = new ArrayList<>(two);
        four.addAll(episode(P3, INE, ""));
        four.addAll(episode(P4, INE, ""));
        IndicatorResult twentyFive = computeNovember(conventionPack(), four).result();
        assertThat(twentyFive.valueExact()).isEqualByComparingTo(ExactRatio.of(25, 1));
        assertThat(twentyFive.classification()).isEqualTo(Classification.REGULAR);
    }

    // ---- MET-03 / MET-04 ----

    @Test
    void met03_eligibleEpisodesMeetingNothingAreARealZero() {
        List<Record> records = new ArrayList<>(episode(P1, INE, ""));
        records.addAll(episode(P2, INE, ""));
        C3Pack pack = new C3Pack();

        IndicatorResult computed = computeNovember(pack, records).result();
        assertThat(computed.status()).isEqualTo(IndicatorStatus.COMPUTED);
        assertThat(computed.numerator()).isEqualTo(BigInteger.ZERO);
        assertThat(computed.denominator()).isEqualTo(BigInteger.TWO);
        assertThat(computed.valueExact()).isEqualByComparingTo(ExactRatio.zero());
        assertThat(computed.valueText()).isEqualTo("0.0000");
        assertThat(computed.classification()).isEqualTo(Classification.REGULAR);

        IndicatorResult gated =
                pack.evaluate(dataset(NOVEMBER, records), context(NOVEMBER)).result();
        assertThat(gated.status()).isEqualTo(IndicatorStatus.BLOCKED);
        assertThat(gated.numerator()).isEqualTo(BigInteger.ZERO);
        assertThat(gated.denominator()).isEqualTo(BigInteger.TWO);
        assertThat(gated.valueText()).isNull();
        assertThat(gated.classification()).isNull();
    }

    @Test
    void met04_noEpisodeIsNoDenominatorNeverZero() {
        RuleOutcome outcome = computeNovember(new C3Pack(), List.of());
        assertNoDenominator(outcome.result());
        assertThat(outcome.evidence()).isEmpty();
        assertThat(outcome.result().consolidationEligible()).isFalse();
    }

    @Test
    void met04_linkedPersonWithoutPregnancyIsNoDenominator() {
        assertNoDenominator(computeNovember(new C3Pack(), linked(P1, INE)).result());
        assertNoDenominator(new C3Pack()
                .evaluate(dataset(NOVEMBER, linked(P1, INE)), context(NOVEMBER))
                .result());
    }

    // ---- evaluate(): gates, components and teams ----

    @Test
    void evaluate_isBlockedByTheGatesWithExactCountsComponentsAndTeamsPerIne() {
        List<Record> records = new ArrayList<>(fullEpisode(P1));
        records.addAll(episode(P2, OTHER_INE, ""));
        records.addAll(episode(P3, null, ""));
        C3Pack pack = conventionPack();

        RuleOutcome computed = pack.compute(dataset(NOVEMBER, records), context(NOVEMBER));
        assertThat(computed.result().status()).isEqualTo(IndicatorStatus.COMPUTED);
        // P3's registration has no INE: no link (integration review B2), so two eligible episodes
        assertThat(episodeRow(computed, episodeKey(P3, DUM)).reasonCode()).isEqualTo("EXCLUIDO_SEM_VINCULO");
        assertThat(computed.result().valueExact()).isEqualByComparingTo(ExactRatio.of(100, 2));
        assertThat(computed.result().valueText()).isEqualTo("50.0000");
        assertThat(computed.result().classification()).isEqualTo(Classification.SUFICIENTE);

        RuleOutcome outcome = pack.evaluate(dataset(NOVEMBER, records), context(NOVEMBER));
        IndicatorResult result = outcome.result();
        assertThat(result.status()).isEqualTo(IndicatorStatus.BLOCKED);
        assertThat(result.numerator()).isEqualTo(HUNDRED);
        assertThat(result.denominator()).isEqualTo(BigInteger.TWO);
        assertThat(result.valueText()).isNull();
        assertThat(result.valueExact()).isNull();
        assertThat(result.classification()).isNull();
        assertThat(result.ruleVersion()).isEqualTo(C3Pack.RULE_VERSION);
        assertThat(result.referencePeriod()).isEqualTo("2025-11");
        assertThat(result.dataCutoff()).isEqualTo("2025-11-30");
        assertThat(result.limitations())
                .contains("Portão A (fonte e vigência) incompleto")
                .containsAll(pack.descriptor().standingLimitations());
        assertThat(result.components())
                .extracting(ResultComponent::code)
                .containsExactly("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K");
        assertThat(result.components()).allSatisfy(c -> {
            assertThat(c.numerator()).isEqualTo(BigInteger.ONE);
            assertThat(c.denominator()).isEqualTo(BigInteger.TWO);
        });

        assertThat(outcome.teams()).extracting(TeamResult::ine).containsExactly(INE, OTHER_INE);
        TeamResult first = outcome.teams().get(0);
        assertThat(first.cnes()).isEqualTo(CNES);
        assertThat(first.result().status()).isEqualTo(IndicatorStatus.BLOCKED);
        assertThat(first.result().numerator()).isEqualTo(HUNDRED);
        assertThat(first.result().denominator()).isEqualTo(BigInteger.ONE);
        assertThat(outcome.teams().get(1).result().numerator()).isEqualTo(BigInteger.ZERO);
        assertThat(outcome.teams().get(1).result().denominator()).isEqualTo(BigInteger.ONE);
        assertThat(outcome.teams())
                .allSatisfy(t -> assertThat(t.result().valueText()).isNull());
    }

    @Test
    void municipality_aRecordOfAnotherMunicipalityIsRefused() {
        List<Record> records = new ArrayList<>(fullEpisode(P1));
        records.add(care(P1, dum(70)).municipality(OTHER_IBGE).ciap("W78").build());
        C3Pack pack = new C3Pack();
        assertThatThrownBy(() -> pack.compute(dataset(NOVEMBER, records), context(NOVEMBER)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pack.evaluate(dataset(NOVEMBER, records), context(NOVEMBER)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- ENG-36: the evidence rebuilds the population ----

    @Test
    void eng36_evidenceHasOneEpisodeRowPerSubjectIncludingExclusions() {
        List<Record> records = new ArrayList<>(fullEpisode(P1));
        // no registration at all
        records.add(person(P2, null));
        records.add(anchor(P2, dum(56), DUM));
        // the latest registration left the territory (MotivoSaida 136)
        records.add(person(P3, null));
        records.add(registration(P3, C3Fixtures.LINKED_ON, INE, "136"));
        records.add(anchor(P3, dum(56), DUM));
        // death recorded before the cutoff
        records.add(person(P4, LocalDate.of(2025, 10, 1)));
        records.add(registration(P4, C3Fixtures.LINKED_ON, INE, null));
        records.add(anchor(P4, dum(56), DUM));
        // abortion code (CIAP-2 W82) inside the pregnancy
        records.addAll(linked(P5, INE));
        records.add(anchor(P5, dum(56), DUM));
        records.add(care(P5, dum(120)).ciap("W82").build());

        RuleOutcome outcome = computeNovember(conventionPack(), records);

        List<EvidenceItem> episodes = outcome.evidence().stream()
                .filter(e -> e.subjectKind() == EvidenceSubjectKind.EPISODE && e.component() == null)
                .filter(e -> e.decision() == EvidenceDecision.ELIGIBLE || e.decision() == EvidenceDecision.EXCLUDED)
                .toList();
        assertThat(episodes).hasSize(5);
        assertThat(episodeRow(outcome, episodeKey(P1, DUM)).reasonCode()).isEqualTo("ELEGIVEL_DATA_SUBSTITUTIVA_294D");
        assertExcluded(outcome, P2, "EXCLUIDO_SEM_VINCULO");
        assertExcluded(outcome, P3, "INTERROMPIDO_MUDANCA_TERRITORIO");
        assertExcluded(outcome, P4, "EXCLUIDO_OBITO");
        assertExcluded(outcome, P5, "EXCLUIDO_ABORTO");
        assertThat(episodeRow(outcome, episodeKey(P5, DUM)).eventDate()).isEqualTo(dum(120).toString());

        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.ONE);
        assertThat(outcome.result().numerator()).isEqualTo(HUNDRED);

        // one practice row per eligible episode × practice, A..K in order
        List<String> practiceCodes = outcome.evidence().stream()
                .filter(e -> e.decision() == EvidenceDecision.PRACTICE_MET
                        || e.decision() == EvidenceDecision.PRACTICE_NOT_MET
                        || e.decision() == EvidenceDecision.PRACTICE_EXEMPT
                        || e.decision() == EvidenceDecision.PRACTICE_AMBIGUOUS)
                .map(e -> e.subjectKey() + "/" + e.component())
                .toList();
        String key = episodeKey(P1, DUM);
        assertThat(practiceCodes)
                .containsExactly(
                        key + "/A",
                        key + "/B",
                        key + "/C",
                        key + "/D",
                        key + "/E",
                        key + "/F",
                        key + "/G",
                        key + "/H",
                        key + "/I",
                        key + "/J",
                        key + "/K");

        // subjects are the opaque person key plus the DUM, never a name, CPF or CNS
        assertThat(outcome.evidence())
                .allSatisfy(e -> assertThat(e.subjectKey()).matches("gestante-\\d#(\\d{4}-\\d{2}-\\d{2}|sem-dum)"));
    }

    @Test
    void eng36_dumMilestoneRowPointsAtTheAnchor() {
        List<Record> records = new ArrayList<>(linked(P1, INE));
        var anchorConsult = anchor(P1, dum(56), DUM);
        records.add(anchorConsult);
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertThat(outcome.evidence())
                .filteredOn(e -> "MARCO_DUM".equals(e.reasonCode()))
                .singleElement()
                .satisfies(e -> {
                    assertThat(e.decision()).isEqualTo(EvidenceDecision.SUPPORTING_EVENT);
                    assertThat(e.component()).isNull();
                    assertThat(e.subjectKey()).isEqualTo(episodeKey(P1, DUM));
                    assertThat(e.eventDate()).isEqualTo("2025-01-01");
                    assertThat(e.sourceRef()).isEqualTo(anchorConsult.sourceRef());
                });
    }

    // ---- consolidation: a month enters the quadrimestral mean only with a D+42 in it ----

    @Test
    void consolidation_eligibleWhenAnEpisodeReachesTheFortySecondDayInTheMonth() {
        // D = 2025-09-28 recorded, D+42 = 2025-11-09
        List<Record> records = new ArrayList<>(linked(P1, INE));
        records.add(anchor(P1, dum(56), DUM));
        records.add(outcome(P1, OUTCOME));
        assertThat(computeNovember(new C3Pack(), records).result().consolidationEligible())
                .isTrue();
    }

    @Test
    void consolidation_notEligibleWhenTheFortySecondDayFallsNextMonth() {
        // no outcome: D = DUM+294 = 2025-10-22, D+42 = 2025-12-03
        RuleOutcome outcome = computeNovember(new C3Pack(), episode(P1, INE, ""));
        assertThat(episodeRow(outcome, episodeKey(P1, DUM)).eventDate()).isEqualTo(SUBSTITUTE_END.toString());
        assertThat(outcome.result().consolidationEligible()).isFalse();
    }

    @Test
    void consolidation_anExcludedEpisodeDoesNotMakeTheMonthEligible() {
        List<Record> records = new ArrayList<>();
        records.add(person(P1, LocalDate.of(2025, 10, 1)));
        records.add(registration(P1, C3Fixtures.LINKED_ON, INE, null));
        records.add(anchor(P1, dum(56), DUM));
        records.add(outcome(P1, OUTCOME));
        RuleOutcome outcome = computeNovember(new C3Pack(), records);
        assertThat(episodeRow(outcome, episodeKey(P1, DUM)).reasonCode()).isEqualTo("EXCLUIDO_OBITO");
        assertThat(outcome.result().consolidationEligible()).isFalse();
    }

    // ---- MET-32: deduplication ----

    @Test
    void met32_theSameRecordTwiceCountsOnceAndSupportsEachPracticeOnce() {
        List<Record> records = new ArrayList<>(fullEpisode(P1));
        var dentalVisit = dental(P1, dum(141));
        records.add(dentalVisit);
        records.add(dentalVisit);
        records.add(records.get(2)); // the anchor consultation (after person and registration) again
        RuleOutcome outcome = computeNovember(conventionPack(), records);

        String key = episodeKey(P1, DUM);
        assertThat(outcome.result().numerator()).isEqualTo(HUNDRED);
        assertMet(practice(outcome, key, "K"), 9);
        for (int i = 0; i < ALL_PRACTICES.length(); i++) {
            char code = ALL_PRACTICES.charAt(i);
            List<SourceRef> refs = supporting(outcome, key, String.valueOf(code)).stream()
                    .map(EvidenceItem::sourceRef)
                    .toList();
            assertThat(new HashSet<>(refs)).as("practice %s", code).hasSameSizeAs(refs);
        }
        assertThat(supporting(outcome, key, "K"))
                .filteredOn(e -> dentalVisit.sourceRef().equals(e.sourceRef()))
                .hasSize(1);
    }

    private static PartRequirement part(DataRequirements r, String capability) {
        return r.parts().stream()
                .filter(p -> p.capability().equals(capability))
                .findFirst()
                .orElseThrow();
    }

    @SafeVarargs
    private static Set<String> union(List<String>... lists) {
        Set<String> all = new HashSet<>();
        for (List<String> list : lists) {
            all.addAll(list);
        }
        return all;
    }

    private static BigInteger nine() {
        return BigInteger.valueOf(9);
    }

    private static void assertNoDenominator(IndicatorResult result) {
        assertThat(result.status()).isEqualTo(IndicatorStatus.NO_DENOMINATOR);
        assertThat(result.numerator()).isEqualTo(BigInteger.ZERO);
        assertThat(result.denominator()).isEqualTo(BigInteger.ZERO);
        assertThat(result.valueText()).isNull();
        assertThat(result.valueExact()).isNull();
        assertThat(result.classification()).isNull();
    }

    private static void assertExcluded(RuleOutcome outcome, String personKey, String reason) {
        EvidenceItem row = episodeRow(outcome, episodeKey(personKey, DUM));
        assertThat(row.decision()).isEqualTo(EvidenceDecision.EXCLUDED);
        assertThat(row.reasonCode()).isEqualTo(reason);
    }
}
