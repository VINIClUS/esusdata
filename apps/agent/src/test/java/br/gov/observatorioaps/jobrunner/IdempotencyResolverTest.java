package br.gov.observatorioaps.jobrunner;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** ENG-24: same principal+key+hash reuses the same job; different hash conflicts; scope matters. */
class IdempotencyResolverTest {

    @TempDir
    Path dataDir;

    private JobRunnerTestFixture fixture;
    private IdempotencyResolver resolver;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-09-20T12:00:00Z"), ZoneOffset.UTC);
        fixture = new JobRunnerTestFixture(dataDir, clock);
        fixture.registerSource("src-1", "3541307");
        resolver = new IdempotencyResolver(fixture.jobRepository, clock);
    }

    @AfterEach
    void tearDown() {
        fixture.close();
    }

    private EnqueueRequest request(String jobId, String principal, String key, String hash) {
        return new EnqueueRequest(jobId, "run-" + jobId, "3541307", "c1-mais-acesso",
                "c1-mais-acesso@0.1.0", "2026-03", 3, "src-1", "ext-1",
                principal, key, hash, clock.instant().plusSeconds(3600), null, clock.instant());
    }

    @Test
    void identicalRepetitionReturnsTheSameJob() {
        Job first = resolver.resolve(request("job-1", "user-a", "key-1", "hash-x"));
        Job second = resolver.resolve(request("job-1-again", "user-a", "key-1", "hash-x"));
        assertThat(second.jobId()).isEqualTo(first.jobId());
    }

    @Test
    void sameKeyDifferentHashConflicts() {
        resolver.resolve(request("job-1", "user-a", "key-1", "hash-x"));
        assertThatThrownBy(() -> resolver.resolve(request("job-2", "user-a", "key-1", "hash-y")))
                .isInstanceOf(JobRequestConflictException.class);
    }

    @Test
    void differentPrincipalNeverCollidesOnTheSameKey() {
        Job userA = resolver.resolve(request("job-1", "user-a", "key-1", "hash-x"));
        Job userB = resolver.resolve(request("job-2", "user-b", "key-1", "hash-x"));
        assertThat(userA.jobId()).isNotEqualTo(userB.jobId());
    }

    @Test
    void noIdempotencyKeyAlwaysCreatesANewJob() {
        EnqueueRequest noKey = new EnqueueRequest("job-1", "run-job-1", "3541307",
                "c1-mais-acesso", "c1-mais-acesso@0.1.0", "2026-03", 3, "src-1", "ext-1",
                null, null, null, null, null, clock.instant());
        EnqueueRequest noKey2 = new EnqueueRequest("job-2", "run-job-2", "3541307",
                "c1-mais-acesso", "c1-mais-acesso@0.1.0", "2026-03", 3, "src-1", "ext-1",
                null, null, null, null, null, clock.instant());
        Job first = resolver.resolve(noKey);
        Job second = resolver.resolve(noKey2);
        assertThat(first.jobId()).isNotEqualTo(second.jobId());
    }

    @Test
    void expiredKeyStartsANewJobEvenWithTheSameHash() {
        EnqueueRequest expiring = new EnqueueRequest("job-1", "run-job-1", "3541307",
                "c1-mais-acesso", "c1-mais-acesso@0.1.0", "2026-03", 3, "src-1", "ext-1",
                "user-a", "key-1", "hash-x", clock.instant().minusSeconds(1), null, clock.instant());
        Job first = resolver.resolve(expiring);

        EnqueueRequest afterExpiry = new EnqueueRequest("job-2", "run-job-2", "3541307",
                "c1-mais-acesso", "c1-mais-acesso@0.1.0", "2026-03", 3, "src-1", "ext-1",
                "user-a", "key-1", "hash-x", clock.instant().plusSeconds(3600), null, clock.instant());
        Job second = resolver.resolve(afterExpiry);

        assertThat(second.jobId()).isNotEqualTo(first.jobId());
    }
}
