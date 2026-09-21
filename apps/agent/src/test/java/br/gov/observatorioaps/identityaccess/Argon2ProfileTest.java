package br.gov.observatorioaps.identityaccess;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * §1.12.7 L534: "Argon2id com salt individual, parâmetros versionados... registrar parâmetros
 * efetivos." Uses cheap parameters for the fast checks and one dedicated test at the actual
 * production baseline ({@link SecurityProperties}'s own defaults) to prove the profile works at
 * the cost that will really run in production, not only at a test-shortcut cost.
 */
class Argon2ProfileTest {

    private final SecurityProperties cheap =
            new SecurityProperties(15, 8, 5, 5, 15, 15, 15, 128, 8, 1, 1, 16, 32, "v1", 30, 24);
    private final Argon2Profile profile = new Argon2Profile(cheap);

    @Test
    void encodesAndMatchesTheSamePassword() {
        String hash = profile.encode("a-strong-enough-passphrase");
        assertThat(profile.matches("a-strong-enough-passphrase", hash)).isTrue();
    }

    @Test
    void rejectsTheWrongPassword() {
        String hash = profile.encode("a-strong-enough-passphrase");
        assertThat(profile.matches("a-different-passphrase", hash)).isFalse();
    }

    @Test
    void individualSaltMeansTwoEncodesOfTheSamePasswordDiffer() {
        String first = profile.encode("a-strong-enough-passphrase");
        String second = profile.encode("a-strong-enough-passphrase");

        assertThat(first).isNotEqualTo(second);
        assertThat(profile.matches("a-strong-enough-passphrase", first)).isTrue();
        assertThat(profile.matches("a-strong-enough-passphrase", second)).isTrue();
    }

    @Test
    void effectiveParamsJsonRecordsWhatWasActuallyUsedNotTheCurrentBaseline() {
        String json = profile.effectiveParamsJson();

        assertThat(json).contains("\"saltLength\":16").contains("\"hashLength\":32")
                .contains("\"parallelism\":1").contains("\"memoryKib\":8").contains("\"iterations\":1");
    }

    @Test
    void verificationNeverDependsOnTheCurrentBaselineOnlyOnWhatTheHashItselfEncodes() {
        // The encoded string is self-describing (embeds its own m/t/p) — a profile constructed
        // with DIFFERENT current parameters must still verify a hash produced under the old ones.
        String hash = profile.encode("a-strong-enough-passphrase");
        SecurityProperties laterBaseline =
                new SecurityProperties(15, 8, 5, 5, 15, 15, 15, 128, 19456, 3, 1, 16, 32, "v2", 30, 24);
        Argon2Profile laterProfile = new Argon2Profile(laterBaseline);

        assertThat(laterProfile.matches("a-strong-enough-passphrase", hash)).isTrue();
    }

    @Test
    void worksAtTheActualProductionCostBaseline() {
        // SecurityProperties' own @DefaultValue set — not shortened for test speed. Proves the
        // profile is correct at the cost that will really run in production, not only at the
        // cheap cost every other test in this class uses for speed.
        SecurityProperties production =
                new SecurityProperties(15, 8, 5, 5, 15, 15, 15, 128, 19456, 2, 1, 16, 32, "v1", 30, 24);
        Argon2Profile productionProfile = new Argon2Profile(production);

        String hash = productionProfile.encode("a-strong-enough-passphrase");

        assertThat(productionProfile.matches("a-strong-enough-passphrase", hash)).isTrue();
        assertThat(productionProfile.matches("a-different-passphrase", hash)).isFalse();
        assertThat(productionProfile.effectiveParamsJson())
                .contains("\"memoryKib\":19456").contains("\"iterations\":2");
    }
}
