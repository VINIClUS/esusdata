package esusdata.indicator.pack.c3;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.CanonicalFixtures;
import esusdata.indicator.model.CanonicalMeasurement;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/** AMB-C3-19: the two MIAC conditions (conjunction), given practice lists in LEDI codes. */
class MiacMatchTest {

    private static CanonicalMeasurement activity(String type, String practice) {
        return CanonicalFixtures.collectiveActivity(
                "p1", LocalDate.of(2025, 5, 1), "60", "160", "322405", type, List.of(practice));
    }

    @Test
    void bothConditionsCount() {
        assertThat(MiacMatch.counts(activity("5", "2"), List.of("2"))).isTrue();
    }

    @Test
    void onlyOneConditionOrNeitherDoesNotCount() {
        assertThat(MiacMatch.counts(activity("5", "9"), List.of("2"))).isFalse();
        assertThat(MiacMatch.counts(activity("4", "2"), List.of("2"))).isFalse();
        assertThat(MiacMatch.counts(activity("4", "9"), List.of("2"))).isFalse();
    }

    @Test
    void theFichaPracticesAreReadAsLediCodes() {
        // ficha 01 antropometria = LEDI 20, 02 flúor = 2, 04 escovação = 9 (capacidades-dw-v2 §3.7)
        assertThat(C3Codes.MIAC_PRACTICES).containsExactly("20", "2", "9");
        assertThat(C3Codes.MIAC_PRACTICES_ANTHROPOMETRY).containsExactly("20");
        assertThat(C3Codes.MIAC_PRACTICES_ORAL_HEALTH).containsExactly("2", "9");
        assertThat(MiacMatch.counts(activity("05", "02"), C3Codes.MIAC_PRACTICES_ORAL_HEALTH))
                .isTrue();
        assertThat(MiacMatch.counts(activity("05", "01"), C3Codes.MIAC_PRACTICES_ANTHROPOMETRY))
                .isFalse();
        assertThat(MiacMatch.counts(activity("4", "20"), C3Codes.MIAC_PRACTICES_ANTHROPOMETRY))
                .isFalse();
        assertThat(MiacMatch.counts(activity("06", "20"), C3Codes.MIAC_PRACTICES_ANTHROPOMETRY))
                .isTrue();
    }
}
