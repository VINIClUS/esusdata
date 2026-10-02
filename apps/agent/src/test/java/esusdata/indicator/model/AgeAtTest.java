package esusdata.indicator.model;

import static org.assertj.core.api.Assertions.assertThat;

import esusdata.indicator.model.AgeAt.AnniversaryRule;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** ENG-27: ages on the calendar, including month ends and 29 February, under an explicit rule. */
class AgeAtTest {

    @Test
    void completesYearsOnTheBirthday() {
        LocalDate birth = LocalDate.of(2024, 3, 15);
        for (AnniversaryRule rule : AnniversaryRule.values()) {
            assertThat(AgeAt.completedYears(birth, LocalDate.of(2026, 3, 14), rule))
                    .isEqualTo(1);
            assertThat(AgeAt.completedYears(birth, LocalDate.of(2026, 3, 15), rule))
                    .isEqualTo(2);
            assertThat(AgeAt.anniversaryYears(birth, 2, rule)).isEqualTo(LocalDate.of(2026, 3, 15));
        }
    }

    @Test
    void aLeapDayBirthDependsOnTheDeclaredRule() {
        LocalDate birth = LocalDate.of(2024, 2, 29);
        assertThat(AgeAt.anniversaryYears(birth, 2, AnniversaryRule.CLAMP_TO_MONTH_END))
                .isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(AgeAt.anniversaryYears(birth, 2, AnniversaryRule.NEXT_DAY)).isEqualTo(LocalDate.of(2026, 3, 1));
        LocalDate feb28 = LocalDate.of(2026, 2, 28);
        assertThat(AgeAt.completedYears(birth, feb28, AnniversaryRule.CLAMP_TO_MONTH_END))
                .isEqualTo(2);
        assertThat(AgeAt.completedYears(birth, feb28, AnniversaryRule.NEXT_DAY)).isEqualTo(1);
        assertThat(AgeAt.completedYears(birth, LocalDate.of(2028, 2, 29), AnniversaryRule.NEXT_DAY))
                .isEqualTo(4);
    }

    @Test
    void countsMonthsFromAMonthEndUnderEitherRule() {
        LocalDate birth = LocalDate.of(2026, 1, 31);
        LocalDate feb28 = LocalDate.of(2026, 2, 28);
        assertThat(AgeAt.completedMonths(birth, feb28, AnniversaryRule.CLAMP_TO_MONTH_END))
                .isEqualTo(1);
        assertThat(AgeAt.completedMonths(birth, feb28, AnniversaryRule.NEXT_DAY))
                .isZero();
        assertThat(AgeAt.anniversaryMonths(birth, 1, AnniversaryRule.NEXT_DAY)).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(AgeAt.completedMonths(birth, LocalDate.of(2026, 3, 31), AnniversaryRule.NEXT_DAY))
                .isEqualTo(2);
        assertThat(AgeAt.completedMonths(birth, birth, AnniversaryRule.NEXT_DAY))
                .isZero();
    }

    @Test
    void countsDaysFromTheDayOfBirth() {
        LocalDate birth = LocalDate.of(2026, 1, 31);
        assertThat(AgeAt.daysSinceBirth(birth, birth)).isZero();
        assertThat(AgeAt.daysSinceBirth(birth, LocalDate.of(2026, 3, 2))).isEqualTo(30);
    }
}
