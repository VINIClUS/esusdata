package esusdata.indicator.pack.c1;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalModality;
import esusdata.indicator.model.DataRequirements;
import esusdata.indicator.model.DateWindow;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.EvidenceDecision;
import esusdata.indicator.model.EvidenceItem;
import esusdata.indicator.model.EvidenceSubjectKind;
import esusdata.indicator.model.IndicatorResult;
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
    void theMunicipalResultIsC1RuleUnchanged() {
        List<CanonicalEncounter> encounters = sample();
        RuleOutcome outcome = new C1Pack()
                .evaluate(
                        CanonicalDataset.ofEncounters(C1Pack.CAPABILITY, MARCH_WINDOW, encounters),
                        EvaluationContext.endOfMonth("3541307", MARCH));
        IndicatorResult expected = C1Rule.compute(encounters, "3541307", "2026-03", "2026-03-31");
        assertThat(outcome.result()).isEqualTo(expected);
        assertThat(outcome.result().status()).isEqualTo(IndicatorResult.IndicatorStatus.BLOCKED);
        assertThat(outcome.result().numerator()).isEqualTo(BigInteger.TWO);
        assertThat(outcome.result().denominator()).isEqualTo(BigInteger.valueOf(3));
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
    void classifiesWithC1sOwnNonMonotonicBands() {
        assertThat(new C1Pack().classify(esusdata.indicator.model.ExactRatio.of(80, 1)))
                .contains(esusdata.indicator.model.Classification.REGULAR);
    }
}
