package esusdata.indicator.model;

import static esusdata.indicator.model.CanonicalFixtures.team;
import static esusdata.indicator.model.CanonicalFixtures.teamState;
import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.TeamScope.Verdict;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The team-type rule of the decision records, on the last day of the competência: 70 eSF and 76 eAP
 * count; no type, two types and any other type leave the team out, each with its own reason. The
 * source's {@code valid_to} is exclusive (ADR 0031), so a state that ends on the last day does not
 * cover it and one that starts on it does.
 */
class TeamScopeTest {

    private static final LocalDate END = LocalDate.of(2026, 3, 31);
    private static final String INE = "0000100001";

    private static TeamScope.Decision decide(CanonicalTeam... states) {
        return TeamScope.of(List.of(states), END).decide(INE);
    }

    @Test
    void esfAndEapAreConsideredAndEapIsMarked() {
        assertThat(decide(team(INE, "1", "70")).verdict()).isEqualTo(Verdict.ESF);
        assertThat(decide(team(INE, "1", "70")).considered()).isTrue();
        TeamScope.Decision eap = decide(team(INE, "1", "76"));
        assertThat(eap.verdict()).isEqualTo(Verdict.EAP);
        assertThat(eap.eap76()).isTrue();
        assertThat(eap.exclusionReason()).isNull();
    }

    @Test
    void anotherTypeIsOutOfScopeWithItsReason() {
        TeamScope.Decision other = decide(team(INE, "1", "72"));
        assertThat(other.verdict()).isEqualTo(Verdict.OUT_OF_SCOPE);
        assertThat(other.typeCode()).isEqualTo("72");
        assertThat(other.exclusionReason()).isEqualTo("EXCLUIDO_EQUIPE_FORA_DO_ESCOPO");
    }

    @Test
    void noStateAndNoIneAreWithoutType() {
        assertThat(decide().verdict()).isEqualTo(Verdict.WITHOUT_TYPE);
        assertThat(decide().exclusionReason()).isEqualTo("EXCLUIDO_EQUIPE_SEM_TIPO");
        assertThat(TeamScope.of(List.of(team(INE, "1", "70")), END).decide(null).verdict())
                .isEqualTo(Verdict.WITHOUT_TYPE);
        assertThat(TeamScope.of(List.of(team(INE, "1", "70")), END).decide(" ").verdict())
                .isEqualTo(Verdict.WITHOUT_TYPE);
        assertThat(TeamScope.of(List.of(team("0000999999", "1", "70")), END)
                        .decide(INE)
                        .verdict())
                .isEqualTo(Verdict.WITHOUT_TYPE);
    }

    @Test
    void twoTypesOnTheSameDayConflict() {
        TeamScope.Decision conflict = decide(team(INE, "1", "70"), team(INE, "1", "76"));
        assertThat(conflict.verdict()).isEqualTo(Verdict.CONFLICT);
        assertThat(conflict.considered()).isFalse();
        assertThat(conflict.exclusionReason()).isEqualTo("EXCLUIDO_TIPO_EQUIPE_CONFLITANTE");
    }

    @Test
    void severalStatesWithTheSameCodeAreOne() {
        assertThat(decide(team(INE, "1", "70"), team(INE, "2", "70")).verdict()).isEqualTo(Verdict.ESF);
    }

    @Test
    void validToIsExclusiveSoAStateEndingOnTheLastDayDoesNotCoverIt() {
        TeamScope.Decision decision = decide(
                teamState(INE, "1", "70", "2025-01-01", "2026-03-31"), teamState(INE, "1", "76", "2026-03-31", null));
        assertThat(decision.verdict()).isEqualTo(Verdict.EAP); // no CONFLICT on the day the type changes
    }

    @Test
    void aStateStartingOnTheLastDayCoversItAndOneStartingAfterDoesNot() {
        assertThat(decide(teamState(INE, "1", "76", "2026-03-31", null)).verdict())
                .isEqualTo(Verdict.EAP);
        // starting after the last day: nothing earlier, so no type on the day (and nothing to fall back on)
        assertThat(decide(teamState(INE, "1", "76", "2026-04-01", null)).verdict())
                .isEqualTo(Verdict.WITHOUT_TYPE);
    }

    @Test
    void withoutAStateOnTheDayTheMostRecentOneThatBeganBeforeStandsIn() {
        TeamScope.Decision decision = decide(
                teamState(INE, "1", "70", "2024-01-01", "2025-06-01"),
                teamState(INE, "1", "76", "2025-06-01", "2026-02-01"),
                teamState(INE, "1", "72", "2026-04-01", null));
        assertThat(decision.verdict()).isEqualTo(Verdict.EAP);
    }

    @Test
    void theOpenFallbackStateBeforeTheFirstAuditCoversEarlierDays() {
        // the team's current type standing in for the time before its first audited state
        TeamScope.Decision decision = decide(teamState(INE, "1", "70", null, "2026-06-01"));
        assertThat(decision.verdict()).isEqualTo(Verdict.ESF);
    }

    @Test
    void statesWithoutACodeOrAnIneAreIgnored() {
        assertThat(decide(new CanonicalTeam(CanonicalFixtures.ref("t"), CanonicalFixtures.IBGE, INE, "1", null, null))
                        .verdict())
                .isEqualTo(Verdict.WITHOUT_TYPE);
    }
}
