package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.pack.c6.C6Pack;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The team-type date probe on invented team states of the first quadrimestre of 2026. The datasets
 * carry nothing but the states, so only the teams are counted here; a change of result under the
 * reading on the first day is measured in {@code C6MethodologyProbesTest}.
 */
class CommonMethodologyProbesTest {

    private static final Quadrimestre Q1 = new Quadrimestre(2026, 1);
    private static final MethodologyProbe PROBE = CommonMethodologyProbes.all().getFirst();
    private static final String IBGE = "3541307";
    private static final String CNES = "2750325";
    private static final String INE = "0000000011";
    private static final String OTHER_INE = "0000000012";
    private static final String ESF_TYPE = "70";
    private static final String EAP_TYPE = "76";
    private static final String OTHER_TYPE = "72";

    private static ProbeResult probe(CanonicalDataset data, String revisionIne) {
        C6Pack rule = new C6Pack();
        List<PackInput> inputs = Q1.months().stream()
                .map(month -> new PackInput(rule, data, EvaluationContext.endOfMonth(IBGE, month)))
                .toList();
        return PROBE.evaluate(SyntheticProbeContexts.pack(inputs, Map.of(revisionIne, SiapsParser.ESF)));
    }

    private static CanonicalDataset states(CanonicalTeam... teams) {
        CanonicalDataset.Builder builder = CanonicalDataset.builder();
        for (CanonicalTeam team : teams) {
            builder.add(team);
        }
        return builder.build();
    }

    private static CanonicalTeam state(String ine, String type, String from, String to) {
        return CanonicalFixtures.teamState(ine, CNES, type, from, to);
    }

    @Test
    void theProbeIsTheTeamTypeDateOfEveryPack() {
        assertThat(CommonMethodologyProbes.all()).hasSize(1);
        assertThat(PROBE.id()).isEqualTo("common.team.type-reference-date");
        assertThat(PROBE.packs())
                .containsExactlyInAnyOrderElementsOf(
                        GatePack.all().stream().map(GatePack::packId).toList());
    }

    @Test
    void aTeamWhoseTypeHoldsThroughEveryMonthLeavesBothReadingsEqual() {
        ProbeResult result = probe(states(state(INE, ESF_TYPE, "2024-01-01", null)), INE);

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.divergent()).hasValue(0);
    }

    @Test
    void aTypeChangeOnAMonthBoundaryIsTheSameOnBothDaysOfEveryMonth() {
        CanonicalDataset data =
                states(state(INE, ESF_TYPE, "2024-01-01", "2026-03-01"), state(INE, EAP_TYPE, "2026-03-01", null));

        ProbeResult result = probe(data, INE);

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
    }

    @Test
    void aTypeChangeInsideAMonthOfATeamOfTheRevisionIsCountedAndTheRuleRunsAgain() {
        CanonicalDataset data =
                states(state(INE, ESF_TYPE, "2024-01-01", "2026-02-15"), state(INE, EAP_TYPE, "2026-02-15", null));

        ProbeResult result = probe(data, INE);

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.localDetail())
                .anyMatch(line -> line.contains("another type on the first day") && line.contains(INE));
        assertThat(result.versionedForm().values()).noneMatch(value -> value.contains(INE));
    }

    @Test
    void theFirstDayReadingKeepsTheTypeOfTheFirstDayThroughTheMonthAndTouchesNothingElse() {
        CanonicalTeam before = state(INE, ESF_TYPE, "2024-01-01", "2026-02-10");
        CanonicalTeam inside = state(INE, OTHER_TYPE, "2026-02-10", "2026-02-20");
        CanonicalTeam after = state(INE, EAP_TYPE, "2026-02-20", null);
        CanonicalTeam other = state(OTHER_INE, ESF_TYPE, "2026-02-10", null);

        CanonicalDataset rewritten = CommonMethodologyProbes.firstDayTypes(
                states(before, inside, after, other), YearMonth.of(2026, 2), Set.of(INE));

        assertThat(rewritten.teams())
                .extracting(
                        CanonicalTeam::ine,
                        CanonicalTeam::teamTypeCode,
                        CanonicalTeam::validFrom,
                        CanonicalTeam::validTo)
                .containsExactly(
                        tuple(INE, ESF_TYPE, "2024-01-01", "2026-03-01"),
                        tuple(INE, EAP_TYPE, "2026-03-01", null),
                        tuple(OTHER_INE, ESF_TYPE, "2026-02-10", null));
        assertThat(rewritten.teams())
                .extracting(CanonicalTeam::sourceRef)
                .containsExactly(before.sourceRef(), after.sourceRef(), other.sourceRef());
    }

    @Test
    void aTypeChangeInsideAMonthOfATeamOutsideTheRevisionDoesNotCount() {
        CanonicalDataset data = states(
                state(INE, ESF_TYPE, "2024-01-01", null),
                state(OTHER_INE, ESF_TYPE, "2024-01-01", "2026-02-15"),
                state(OTHER_INE, EAP_TYPE, "2026-02-15", null));

        ProbeResult result = probe(data, INE);

        assertThat(result.observability()).isEqualTo(Observability.COMPLETE);
        assertThat(result.affected()).hasValue(0);
    }

    @Test
    void aTypeReadThroughTheCurrentFallbackMakesZeroALowerBound() {
        CanonicalTeam fallback = new CanonicalTeam(
                CanonicalFixtures.ref("tb_equipe"),
                IBGE,
                INE,
                CNES,
                ESF_TYPE,
                null,
                null,
                null,
                CanonicalTeam.CURRENT_FALLBACK);

        ProbeResult result = probe(states(fallback), INE);

        assertThat(result.observability()).isEqualTo(Observability.PARTIAL);
        assertThat(result.affected()).hasValue(0);
        assertThat(result.reason()).contains("current type standing in");
        assertThat(result.localDetail()).anyMatch(line -> line.contains(INE));
        assertThat(result.versionedForm().values()).noneMatch(value -> value.contains(INE));
    }

    @Test
    void aChangeFromAStandInToAnAuditedTypeIsCountedButOnlyAsALowerBound() {
        CanonicalTeam standIn = new CanonicalTeam(
                CanonicalFixtures.ref("tb_equipe"),
                IBGE,
                INE,
                CNES,
                ESF_TYPE,
                null,
                null,
                "2026-02-15",
                CanonicalTeam.CURRENT_FALLBACK);

        ProbeResult result = probe(states(standIn, state(INE, EAP_TYPE, "2026-02-15", null)), INE);

        assertThat(result.observability()).isEqualTo(Observability.PARTIAL);
        assertThat(result.affected()).hasValue(1);
        assertThat(result.reason()).contains("current type standing in");
    }

    @Test
    void anExtractWithoutTeamStatesCannotBeRead() {
        ProbeResult result = probe(CanonicalDataset.builder().build(), INE);

        assertThat(result.observability()).isEqualTo(Observability.NONE);
        assertThat(result.reason()).contains("no team state");
    }
}
