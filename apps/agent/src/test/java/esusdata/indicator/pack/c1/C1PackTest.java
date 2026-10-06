package esusdata.indicator.pack.c1;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalModality;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.Capabilities;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.PartRequirement;
import esusdata.indicator.model.RuleOutcome;
import esusdata.indicator.model.SourceRef;
import esusdata.indicator.model.TeamResult;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** C1 behind the SPI computes exactly what C1Rule computes, per municipality and per team. */
class C1PackTest {

    private static final YearMonth MARCH = YearMonth.of(2026, 3);
    private static final DateWindow MARCH_WINDOW = new DateWindow(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 4, 1));

    private static CanonicalEncounter encounter(int id, CanonicalModality modality, String ine) {
        return new CanonicalEncounter(
                new SourceRef("pec-1", "tb_fat_atendimento_individual", String.valueOf(id)),
                "3541307",
                "2026-03-10",
                modality,
                ine == null ? null : "1234567",
                ine,
                "225142");
    }

    private static List<CanonicalEncounter> sample() {
        List<CanonicalEncounter> list = new ArrayList<>();
        list.add(encounter(1, CanonicalModality.PROGRAMADO, "0000000001"));
        list.add(encounter(2, CanonicalModality.ESPONTANEO, "0000000001"));
        list.add(encounter(3, CanonicalModality.PROGRAMADO, "0000000002"));
        list.add(encounter(4, CanonicalModality.UNMAPPED, null));
        return list;
    }

    @Test
    void readsItsOneFrozenCapabilityForTheCompetenciaMonth() {
        DataRequirements requirements = new C1Pack().requirements(MARCH);
        assertThat(requirements.canonicalSchemaVersion()).isEqualTo(DataRequirements.V1);
        assertThat(requirements.parts()).singleElement().satisfies(p -> {
            assertThat(p.capability()).isEqualTo(C1Pack.CAPABILITY);
            assertThat(p.periodStart()).isEqualTo(LocalDate.of(2026, 3, 1));
            assertThat(p.periodEndExclusive()).isEqualTo(LocalDate.of(2026, 4, 1));
        });
    }

    @Test
    void theMunicipalResultIsC1RuleUnchangedAndUngated() {
        List<CanonicalEncounter> encounters = sample();
        RuleOutcome outcome = new C1Pack()
                .evaluate(
                        CanonicalDataset.ofEncounters(C1Pack.CAPABILITY, MARCH_WINDOW, encounters),
                        EvaluationContext.endOfMonth("3541307", MARCH));
        IndicatorResult expected = C1Rule.compute(encounters, "3541307", "2026-03", "2026-03-31");
        assertThat(outcome.result()).isEqualTo(expected);
        // ADR 0032: the pack never gates; the executor blocks it while a gate has not passed.
        assertThat(outcome.result().status()).isEqualTo(IndicatorResult.IndicatorStatus.COMPUTED);
        assertThat(outcome.result().numerator()).isEqualTo(BigInteger.TWO);
        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.valueOf(3));
    }

    @Test
    void aTeamHasACnesOnlyWhenItsCountedEncountersAgreeOnOne() {
        CanonicalEncounter counted = encounter(1, CanonicalModality.PROGRAMADO, "0000000001");
        CanonicalEncounter otherUnit = new CanonicalEncounter(
                counted.sourceRef(),
                "3541307",
                "2026-03-11",
                CanonicalModality.ESPONTANEO,
                "7654321",
                "0000000001",
                "225142");
        CanonicalEncounter outsideFicha = new CanonicalEncounter(
                counted.sourceRef(),
                "3541307",
                "2026-03-12",
                CanonicalModality.ESPONTANEO,
                "7654321",
                "0000000002",
                "322205");
        CanonicalEncounter inFicha = encounter(4, CanonicalModality.PROGRAMADO, "0000000002");

        RuleOutcome outcome = new C1Pack()
                .evaluate(
                        CanonicalDataset.ofEncounters(
                                C1Pack.CAPABILITY, MARCH_WINDOW, List.of(counted, otherUnit, outsideFicha, inFicha)),
                        EvaluationContext.endOfMonth("3541307", MARCH));

        assertThat(outcome.teams())
                .extracting(TeamResult::ine, TeamResult::cnes)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("0000000001", null),
                        org.assertj.core.groups.Tuple.tuple("0000000002", "1234567"));
    }

    @Test
    void aCountedEncounterOfUnknownCnesLeavesTheTeamWithoutOne() {
        CanonicalEncounter known = encounter(1, CanonicalModality.PROGRAMADO, "0000000001");
        CanonicalEncounter unknown = new CanonicalEncounter(
                known.sourceRef(), "3541307", "2026-03-11", CanonicalModality.ESPONTANEO, null, "0000000001", "225142");

        RuleOutcome outcome = new C1Pack()
                .evaluate(
                        CanonicalDataset.ofEncounters(C1Pack.CAPABILITY, MARCH_WINDOW, List.of(known, unknown)),
                        EvaluationContext.endOfMonth("3541307", MARCH));

        assertThat(outcome.teams())
                .singleElement()
                .satisfies(team -> assertThat(team.cnes()).isNull());
    }

    /** The v1 read plus the team part (C1-D2): a dataset the production read does not yet produce. */
    private static CanonicalDataset withTeams(List<CanonicalEncounter> encounters, CanonicalTeam... teams) {
        CanonicalDataset.Builder builder = CanonicalDataset.builder();
        encounters.forEach(builder::encounter);
        for (CanonicalTeam team : teams) {
            builder.add(team);
        }
        builder.window(C1Pack.CAPABILITY, MARCH_WINDOW);
        builder.window(Capabilities.TEAM, MARCH_WINDOW);
        return builder.build();
    }

    @Test
    void c1_d2_withTheTeamPartOnlyEncountersOfEsfAndEapTeamsCountAndTheOthersAreCounted() {
        List<CanonicalEncounter> encounters = new ArrayList<>(sample());
        encounters.add(encounter(5, CanonicalModality.ESPONTANEO, "0000000003")); // eMulti
        encounters.add(encounter(6, CanonicalModality.ESPONTANEO, "0000000004")); // two types
        RuleOutcome outcome = new C1Pack()
                .evaluate(
                        withTeams(
                                encounters,
                                CanonicalFixtures.team("0000000001", "1234567", "70"),
                                CanonicalFixtures.team("0000000002", "1234567", "76"),
                                CanonicalFixtures.team("0000000003", "1234567", "72"),
                                CanonicalFixtures.team("0000000004", "1234567", "70"),
                                CanonicalFixtures.team("0000000004", "1234567", "76")),
                        EvaluationContext.endOfMonth("3541307", MARCH));

        assertThat(outcome.result().numerator()).isEqualTo(BigInteger.TWO);
        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.valueOf(3));
        assertThat(outcome.teams()).extracting(TeamResult::ine).containsExactly("0000000001", "0000000002");
        assertThat(outcome.result().limitations())
                .anyMatch(l -> l.startsWith("C1-LIM-10/contagem: 3 atendimento(s)")
                        && l.contains("1 sem INE ou de equipe sem tipo, 1 de tipo conflitante e 1 de outro tipo"));
        assertThat(outcome.evidence())
                .filteredOn(e -> e.decision() == EvidenceDecision.EXCLUDED)
                .extracting(EvidenceItem::reasonCode)
                .containsExactlyInAnyOrder(
                        "EXCLUIDO_EQUIPE_SEM_TIPO",
                        "EXCLUIDO_EQUIPE_FORA_DO_ESCOPO",
                        "EXCLUIDO_TIPO_EQUIPE_CONFLITANTE");
    }

    @Test
    void c1_d2_aBareDatasetWithoutTheTeamPartIsNotFilteredAndTheGapIsClosed() {
        RuleOutcome outcome = new C1Pack()
                .evaluate(
                        CanonicalDataset.ofEncounters(C1Pack.CAPABILITY, MARCH_WINDOW, sample()),
                        EvaluationContext.endOfMonth("3541307", MARCH));

        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.valueOf(3));
        assertThat(outcome.result().limitations()).noneMatch(l -> l.startsWith("C1-LIM-10"));
        // the executor always reads the teams (ADR 0033), so no blocking gap is left in the pack
        assertThat(new C1Pack().descriptor().blockingLimitations()).isEmpty();
        assertThat(new C1Pack().descriptor().requiredCapabilities()).contains(Capabilities.TEAM);
        assertThat(new C1Pack().supplements(MARCH))
                .extracting(PartRequirement::capability)
                .containsExactly(Capabilities.TEAM);
    }

    @Test
    void eachTeamIsComputedOnItsOwnEncountersAndTeamlessOnesStayApart() {
        RuleOutcome outcome = new C1Pack()
                .evaluate(
                        CanonicalDataset.ofEncounters(C1Pack.CAPABILITY, MARCH_WINDOW, sample()),
                        EvaluationContext.endOfMonth("3541307", MARCH));
        assertThat(outcome.teams()).extracting(TeamResult::ine).containsExactly("0000000001", "0000000002", null);
        TeamResult first = outcome.teams().get(0);
        assertThat(first.cnes()).isEqualTo("1234567");
        assertThat(first.result().numerator()).isEqualTo(BigInteger.ONE);
        assertThat(first.result().denominator()).isEqualTo(BigInteger.TWO);
        assertThat(outcome.teams().get(2).result().denominator()).isZero();
        assertThat(outcome.teams().get(2).cnes()).isNull();
    }

    @Test
    void evidenceKeepsTheV2RowPerEncounter() {
        List<EvidenceItem> evidence = C1Pack.evidence(sample());
        assertThat(evidence)
                .extracting(EvidenceItem::decision)
                .containsExactly(
                        EvidenceDecision.IN_NUMERATOR,
                        EvidenceDecision.DENOMINATOR_ONLY,
                        EvidenceDecision.IN_NUMERATOR,
                        EvidenceDecision.EXCLUDED_UNMAPPED);
        assertThat(evidence).allSatisfy(e -> {
            assertThat(e.subjectKind()).isEqualTo(EvidenceSubjectKind.EVENT);
            assertThat(e.subjectKey()).isNull();
            assertThat(e.eventDate()).isEqualTo("2026-03-10");
        });
        assertThat(evidence.get(0).modality()).isEqualTo("PROGRAMADO");
    }

    @Test
    void anEncounterOutsideTheFichaCboListIsExcludedWithItsReasonCode() {
        CanonicalEncounter outside = new CanonicalEncounter(
                new SourceRef("pec-1", "tb_fat_atendimento_individual", "9"),
                "3541307",
                "2026-03-10",
                CanonicalModality.PROGRAMADO,
                "1234567",
                "0000000001",
                "515105");
        List<EvidenceItem> evidence = C1Pack.evidence(List.of(outside));
        assertThat(evidence).singleElement().satisfies(e -> {
            assertThat(e.decision()).isEqualTo(EvidenceDecision.EXCLUDED);
            assertThat(e.reasonCode()).isEqualTo(C1Rule.REASON_CBO_OUTSIDE_FICHA);
            assertThat(e.cbo()).isEqualTo("515105");
        });
        assertThat(C1Pack.evidence(sample()))
                .allSatisfy(e -> assertThat(e.reasonCode()).isNull());
    }

    @Test
    void classifiesWithC1sOwnNonMonotonicBands() {
        assertThat(new C1Pack().classify(esusdata.indicator.model.ExactRatio.of(80, 1)))
                .contains(esusdata.indicator.model.Classification.REGULAR);
    }
}
