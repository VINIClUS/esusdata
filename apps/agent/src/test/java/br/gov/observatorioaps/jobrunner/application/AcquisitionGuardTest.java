package br.gov.observatorioaps.jobrunner.application;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import br.gov.observatorioaps.jobrunner.domain.SourceAcquisitionBlockedException;
import br.gov.observatorioaps.jobrunner.infrastructure.jdbc.JdbcAcquisitionGuardStore;
/**
 * ENG-51: a source blocked after an abandoned live acquisition refuses a new LIVE_READ_ONLY
 * attempt until the cooldown expires; IMMUTABLE_EXTRACT never consults this guard at all (proven
 * separately — nothing in {@link IndicatorRunExecutor#runFromExtract} calls it).
 */
class AcquisitionGuardTest {

    @TempDir
    Path dataDir;

    private JobRunnerTestFixture fixture;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);
        fixture = new JobRunnerTestFixture(dataDir, clock);
        fixture.registerSource("src-1", "3541307");
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    @Test
    void unblockedSourcePassesSilently() {
        assertThatCode(() -> fixture.acquisitionGuard().requireUnblocked("src-1"))
                .doesNotThrowAnyException();
    }

    @Test
    void blockedSourceRefusesUntilCooldownExpires() {
        AcquisitionGuard guard = fixture.acquisitionGuard();
        guard.block("src-1", clock.instant().plusSeconds(60), "recovered abandoned RUNNING job");

        assertThatThrownBy(() -> guard.requireUnblocked("src-1"))
                .isInstanceOf(SourceAcquisitionBlockedException.class)
                .hasMessageContaining("src-1");
    }

    @Test
    void cooldownExpiresAndAllowsAcquisitionAgain() {
        AcquisitionGuard guard = fixture.acquisitionGuard();
        guard.block("src-1", clock.instant().plusSeconds(60), "recovered abandoned RUNNING job");

        Clock later = Clock.fixed(clock.instant().plusSeconds(61), ZoneOffset.UTC);
        AcquisitionGuard laterGuard = new AcquisitionGuard(new JdbcAcquisitionGuardStore(fixture.jdbc), later);
        assertThatCode(() -> laterGuard.requireUnblocked("src-1")).doesNotThrowAnyException();
    }

    @Test
    void aLaterBlockNeverShortensAnExistingCooldown() {
        AcquisitionGuard guard = fixture.acquisitionGuard();
        Instant farFuture = clock.instant().plusSeconds(3600);
        guard.block("src-1", farFuture, "first abandonment");
        guard.block("src-1", clock.instant().plusSeconds(10), "second, shorter, abandonment");

        assertThatThrownBy(() -> guard.requireUnblocked("src-1"))
                .isInstanceOf(SourceAcquisitionBlockedException.class);
        // Still blocked well past the shorter request's cooldown — the longer one won.
        Clock past10s = Clock.fixed(clock.instant().plusSeconds(20), ZoneOffset.UTC);
        assertThatThrownBy(() -> new AcquisitionGuard(new JdbcAcquisitionGuardStore(fixture.jdbc), past10s)
                        .requireUnblocked("src-1"))
                .isInstanceOf(SourceAcquisitionBlockedException.class);
    }
}
