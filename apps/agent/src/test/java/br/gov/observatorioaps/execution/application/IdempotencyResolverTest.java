package br.gov.observatorioaps.execution.application;

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
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;
import br.gov.observatorioaps.execution.domain.job.EnqueueRequest;
import br.gov.observatorioaps.execution.domain.job.Job;
import br.gov.observatorioaps.execution.domain.job.JobRequestConflictException;
import br.gov.observatorioaps.execution.domain.job.JobRepository;
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

    /**
     * ENG-24: two concurrent submissions under the same (principal, key) but different payload
     * hash can both pass the "no existing row yet" check before either commits — the loser then
     * hits the unique index as a {@code DuplicateKeyException}. Simulates that race
     * deterministically (rather than via real threads racing SQLite's single-writer lock, which
     * would be flaky) by stubbing the resolver's first lookup to miss, forcing it down the same
     * insert-then-catch path a real race takes.
     */
    @Test
    void raceLoserWithADifferentHashConflictsInsteadOfSilentlyAdoptingTheWinner() {
        resolver.resolve(request("job-1", "user-a", "key-1", "hash-x"));

        JobRepository racy = spy(fixture.jobRepository);
        when(racy.findByIdempotency("user-a", "key-1"))
                .thenReturn(java.util.Optional.empty()) // first call: miss, like a real race
                .thenCallRealMethod();                   // second call, inside the catch: real lookup
        IdempotencyResolver racingResolver = new IdempotencyResolver(racy, clock);

        assertThatThrownBy(() -> racingResolver.resolve(request("job-2", "user-a", "key-1", "hash-y")))
                .isInstanceOf(JobRequestConflictException.class);

        // Exactly the first job exists — the loser never created a second one.
        assertThat(fixture.jobRepository.findByIdempotency("user-a", "key-1").orElseThrow().jobId())
                .isEqualTo("job-1");
    }

    /** Same race, but the loser's hash actually matches the winner's — it should adopt the job. */
    @Test
    void raceLoserWithTheSameHashAdoptsTheWinnerInstead() {
        Job first = resolver.resolve(request("job-1", "user-a", "key-1", "hash-x"));

        JobRepository racy = spy(fixture.jobRepository);
        when(racy.findByIdempotency("user-a", "key-1"))
                .thenReturn(java.util.Optional.empty())
                .thenCallRealMethod();
        IdempotencyResolver racingResolver = new IdempotencyResolver(racy, clock);

        Job second = racingResolver.resolve(request("job-2", "user-a", "key-1", "hash-x"));
        assertThat(second.jobId()).isEqualTo(first.jobId());
    }
}
