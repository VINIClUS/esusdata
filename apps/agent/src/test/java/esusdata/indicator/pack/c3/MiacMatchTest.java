package esusdata.indicator.pack.c3;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalMeasurement;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AMB-C3-19: the two MIAC conditions, given practice lists in LEDI codes. */
class MiacMatchTest {

    private static CanonicalMeasurement activity(String type, String practice) {
        return CanonicalFixtures.collectiveActivity(
                "p1", LocalDate.of(2025, 5, 1), "60", "160", "322405", type, List.of(practice));
    }

    @Test
    void bothConditionsCountWithTheQuadroOwnReading() {
        MiacMatch match = MiacMatch.of(activity("5", "2"), List.of("2"));
        assertThat(match).isEqualTo(MiacMatch.BOTH);
        assertThat(match.ambiguity(Ambiguity.AMB_C3_04)).isEqualTo(Ambiguity.AMB_C3_04);
    }

    @Test
    void onlyOneConditionIsAmb19AndNeitherDoesNotCount() {
        assertThat(MiacMatch.of(activity("5", "9"), List.of("2")).ambiguity(Ambiguity.AMB_C3_04))
                .isEqualTo(Ambiguity.AMB_C3_19);
        assertThat(MiacMatch.of(activity("4", "9"), List.of("2")).counts()).isFalse();
    }

    @Test
    void theFichaPracticesHaveNoLediCodeYet() {
        assertThat(C3Codes.MIAC_PRACTICES).isEmpty();
        assertThat(C3Codes.MIAC_PRACTICES_ANTHROPOMETRY).isEmpty();
        assertThat(C3Codes.MIAC_PRACTICES_ORAL_HEALTH).isEmpty();
        assertThat(MiacMatch.of(activity("05", "02"), C3Codes.MIAC_PRACTICES_ORAL_HEALTH))
                .isEqualTo(MiacMatch.ONE);
    }
}
