package esusdata.indicator.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import esusdata.indicator.model.CanonicalDataset;
import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalTeam;
import esusdata.indicator.model.EvaluationContext;
import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.pack.c6.C6Pack;
import esusdata.run.worker.SensitivityExtracts.PackInput;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The team-month tally on invented team states of the first quadrimestre of 2026. */
class TeamTypeCoverageTest {

    private static final Quadrimestre Q1 = new Quadrimestre(2026, 1);
    private static final String IBGE = "3541307";
    private static final String CNES = "2750325";
    private static final String INE = "0000000011";
    private static final String ESF_TYPE = "70";
    private static final String EAP_TYPE = "76";

    private static List<PackInput> inputs(CanonicalTeam... teams) {
        CanonicalDataset.Builder builder = CanonicalDataset.builder();
        for (CanonicalTeam team : teams) {
            builder.add(team);
        }
        CanonicalDataset data = builder.build();
        C6Pack rule = new C6Pack();
        return Q1.months().stream()
                .map(month -> new PackInput(rule, data, EvaluationContext.endOfMonth(IBGE, month)))
                .toList();
    }

    private static CanonicalTeam audited(String type) {
        return CanonicalFixtures.teamState(INE, CNES, type, "2024-01-01", null);
    }

    private static CanonicalTeam fallback(String type) {
        return new CanonicalTeam(
                CanonicalFixtures.ref("tb_equipe"),
                IBGE,
                INE,
                CNES,
                type,
                null,
                null,
                null,
                CanonicalTeam.CURRENT_FALLBACK);
    }

    @Test
    void anAuditedTypeThatMatchesTheRevisionIsCountedOncePerMonth() {
        TeamTypeCoverage coverage = TeamTypeCoverage.of(inputs(audited(ESF_TYPE)), Map.of(INE, SiapsParser.ESF));

        assertThat(coverage.teamMonths()).isEqualTo(4);
        assertThat(coverage.audited()).isEqualTo(4);
        assertThat(coverage.typeDisagreeing()).isZero();
        assertThat(coverage.leavesTypeOpen()).isFalse();
    }

    @Test
    void anAuditedTypeOfAnotherKindIsRecordedButDoesNotLeaveTheTypeOpen() {
        TeamTypeCoverage coverage = TeamTypeCoverage.of(inputs(audited(EAP_TYPE)), Map.of(INE, SiapsParser.ESF));

        assertThat(coverage.audited()).isEqualTo(4);
        assertThat(coverage.typeDisagreeing()).isEqualTo(4);
        assertThat(coverage.leavesTypeOpen()).isFalse();
    }

    @Test
    void aCurrentTypeThatAgreesWithTheRevisionStandsInWithoutLeavingTheTypeOpen() {
        TeamTypeCoverage coverage = TeamTypeCoverage.of(inputs(fallback(EAP_TYPE)), Map.of(INE, SiapsParser.EAP));

        assertThat(coverage.fallbackAgreeing()).isEqualTo(4);
        assertThat(coverage.fallbackDisagreeing()).isZero();
        assertThat(coverage.leavesTypeOpen()).isFalse();
    }

    @Test
    void aCurrentTypeThatDisagreesWithTheRevisionLeavesTheTypeOpen() {
        TeamTypeCoverage coverage = TeamTypeCoverage.of(inputs(fallback(ESF_TYPE)), Map.of(INE, SiapsParser.EAP));

        assertThat(coverage.fallbackDisagreeing()).isEqualTo(4);
        assertThat(coverage.leavesTypeOpen()).isTrue();
    }

    @Test
    void aTeamWithNoStateOnTheLastDayIsUnresolved() {
        TeamTypeCoverage coverage = TeamTypeCoverage.of(inputs(), Map.of(INE, SiapsParser.ESF));

        assertThat(coverage.unresolved()).isEqualTo(4);
        assertThat(coverage.leavesTypeOpen()).isTrue();
    }

    @Test
    void twoContradictoryTypesOnTheLastDayAreUnresolved() {
        TeamTypeCoverage coverage =
                TeamTypeCoverage.of(inputs(audited(ESF_TYPE), audited(EAP_TYPE)), Map.of(INE, SiapsParser.ESF));

        assertThat(coverage.unresolved()).isEqualTo(4);
    }

    @Test
    void theVersionedFormMasksEveryCountBelowTen() {
        TeamTypeCoverage coverage = new TeamTypeCoverage(12, 9, 1, 2, 0, 3);

        assertThat(coverage.versionedForm())
                .containsEntry("team_months", "12")
                .containsEntry("audited", "<10")
                .containsEntry("fallback_agreeing", "<10")
                .containsEntry("fallback_disagreeing", "<10")
                .containsEntry("unresolved", "<10")
                .containsEntry("type_disagreeing", "<10");
    }

    @Test
    void aTallyThatDoesNotAddUpIsRefused() {
        assertThatThrownBy(() -> new TeamTypeCoverage(5, 1, 1, 1, 1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new TeamTypeCoverage(1, 1, 0, 0, 0, 2)).isInstanceOf(IllegalArgumentException.class);
    }
}
