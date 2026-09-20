package br.gov.observatorioaps.api;

import br.gov.observatorioaps.identityaccess.AuthenticatedSession;
import br.gov.observatorioaps.identityaccess.Permission;
import br.gov.observatorioaps.jobrunner.CancellationRegistry;
import br.gov.observatorioaps.jobrunner.EnqueueRequest;
import br.gov.observatorioaps.jobrunner.IdempotencyResolver;
import br.gov.observatorioaps.jobrunner.Job;
import br.gov.observatorioaps.jobrunner.JobRepository;
import br.gov.observatorioaps.jobrunner.JobState;
import br.gov.observatorioaps.resultstore.ResultRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * §1.10: run lifecycle — {@code POST /runs} (202, ENG-05/ENG-24 idempotent), {@code GET
 * /runs/{id}} (state/attempts/failure taxonomy — "a consulta do job é a fonte de verdade"), {@code
 * POST /runs/{id}/cancel} (cooperative, §1.9.4). Every route requires {@code RUN_INDICATOR} in the
 * job's own municipality — the SAME 3-argument {@code ScopeResolver.hasPermission} shape {@code
 * GrantRevalidator} uses before acquisition/publication, so a team-scoped grant that somehow
 * passed a laxer HTTP check could never fail later inside the worker as a confusing
 * {@code ACCESS_REVOKED}.
 *
 * <p>{@code indicatorPack}/{@code ruleVersion} are NOT validated here against {@code
 * IndicatorPackCatalog} — {@code IndicatorRunExecutor.requireC1} is the one authority on what is
 * computable, and an unsupported pack surfaces as a FAILED job with {@code INVALID_REQUEST}
 * through the exact same taxonomy any other definitive failure does (§1.10 L399), not as a
 * different shape swallowed at the door.
 */
@RestController
public class RunController {

    private static final Duration IDEMPOTENCY_KEY_TTL = Duration.ofHours(24);
    private static final int DEFAULT_MAX_ATTEMPTS = 3;

    private final JobRepository jobRepository;
    private final IdempotencyResolver idempotencyResolver;
    private final CancellationRegistry cancellationRegistry;
    private final ResultRepository resultRepository;
    private final ApiAuthorization authorization;
    private final Clock clock;

    public RunController(
            JobRepository jobRepository, IdempotencyResolver idempotencyResolver,
            CancellationRegistry cancellationRegistry, ResultRepository resultRepository,
            ApiAuthorization authorization, Clock clock) {
        this.jobRepository = jobRepository;
        this.idempotencyResolver = idempotencyResolver;
        this.cancellationRegistry = cancellationRegistry;
        this.resultRepository = resultRepository;
        this.authorization = authorization;
        this.clock = clock;
    }

    @PostMapping("/api/v1/runs")
    public ResponseEntity<RunResponse> create(
            @AuthenticationPrincipal AuthenticatedSession session,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody CreateRunRequest request) {
        requireFieldsPresent(request);
        try {
            YearMonth.parse(request.referencePeriod());
        } catch (java.time.format.DateTimeParseException e) {
            throw new IllegalArgumentException("referencePeriod must be an ISO YearMonth (yyyy-MM): "
                    + request.referencePeriod());
        }
        authorization.requireObjectScope(session, Permission.RUN_INDICATOR, request.municipalityIbge());

        Instant now = clock.instant();
        String requestHash = computeRequestHash(request);
        EnqueueRequest enqueueRequest = new EnqueueRequest(
                "job-" + UUID.randomUUID(), "run-" + UUID.randomUUID(), request.municipalityIbge(),
                request.indicatorPack(), request.ruleVersion(), request.referencePeriod(),
                DEFAULT_MAX_ATTEMPTS, request.sourceId(), request.extractionId(), session.userId(),
                idempotencyKey, requestHash,
                idempotencyKey == null ? null : now.plus(IDEMPOTENCY_KEY_TTL),
                requestedScopeJson(request.municipalityIbge()), now);

        Job job = idempotencyResolver.resolve(enqueueRequest);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .location(URI.create("/api/v1/runs/" + job.jobId()))
                .body(toResponse(job));
    }

    @GetMapping("/api/v1/runs/{id}")
    public RunResponse get(@AuthenticationPrincipal AuthenticatedSession session, @PathVariable("id") String id) {
        Job job = findAuthorized(session, id);
        return toResponse(job);
    }

    @PostMapping("/api/v1/runs/{id}/cancel")
    public RunResponse cancel(@AuthenticationPrincipal AuthenticatedSession session, @PathVariable("id") String id) {
        Job job = findAuthorized(session, id);
        if (job.state() == JobState.CANCEL_REQUESTED) {
            // Idempotent: a client that polls and re-clicks cancel on an already-cancelling job
            // gets the current (in-progress) state back, not an error for a cancel that is
            // genuinely proceeding.
            return toResponse(job);
        }
        Instant now = clock.instant();
        boolean cancelled = switch (job.state()) {
            case QUEUED -> jobRepository.cancelQueued(id, now);
            case RUNNING, STAGED -> {
                boolean requested = jobRepository.requestCancel(id, now);
                if (requested) {
                    // Best-effort interrupt of an in-flight statement — the CAS above is what
                    // actually matters; this only shortens how long it takes to notice.
                    cancellationRegistry.requestCancel(id);
                }
                yield requested;
            }
            default -> false;
        };
        if (!cancelled) {
            throw new JobNotCancellableException(
                    "job " + id + " cannot be cancelled from its current state (" + job.state() + ")");
        }
        return toResponse(jobRepository.findById(id).orElseThrow());
    }

    private Job findAuthorized(AuthenticatedSession session, String id) {
        Job job = jobRepository.findById(id).orElseThrow(() -> new ApiNotFoundException("unknown job: " + id));
        authorization.requireObjectScope(session, Permission.RUN_INDICATOR, job.municipalityIbge());
        return job;
    }

    /**
     * {@code sourceId} is required in BOTH acquisition modes, not just LIVE_READ_ONLY —
     * {@code results.source_id} is {@code NOT NULL REFERENCES sources(id)}, and {@code
     * IndicatorRunExecutor} publishes with {@code context.sourceId()} even when replaying an
     * {@code extractionId} (see {@code IndicatorRunExecutorExtractTest}, which enqueues both).
     * {@code extractionId}, when present, only SELECTS IMMUTABLE_EXTRACT replay over a fresh
     * LIVE_READ_ONLY acquisition — it is not an alternative to {@code sourceId}.
     */
    private void requireFieldsPresent(CreateRunRequest request) {
        if (request.municipalityIbge() == null || request.indicatorPack() == null
                || request.ruleVersion() == null || request.referencePeriod() == null
                || request.sourceId() == null || request.sourceId().isBlank()) {
            throw new IllegalArgumentException(
                    "municipalityIbge, indicatorPack, ruleVersion, referencePeriod, and sourceId are required");
        }
    }

    private String requestedScopeJson(String municipalityIbge) {
        return "{\"municipalityIbge\":\"" + municipalityIbge + "\"}";
    }

    /**
     * ENG-24: every field that changes what gets computed, canonically ordered — a request
     * differing only in {@code referencePeriod} (the field most likely to be dropped by accident)
     * must still conflict under a reused key, never silently adopt the first job.
     */
    private String computeRequestHash(CreateRunRequest request) {
        String canonical = String.join("|",
                nullToEmpty(request.municipalityIbge()), nullToEmpty(request.indicatorPack()),
                nullToEmpty(request.ruleVersion()), nullToEmpty(request.referencePeriod()),
                nullToEmpty(request.sourceId()), nullToEmpty(request.extractionId()));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private RunResponse toResponse(Job job) {
        String resultId = job.state() == JobState.SUCCEEDED
                ? resultRepository.findResultIdByJobId(job.jobId(), job.municipalityIbge()).orElse(null)
                : null;
        List<AttemptResponse> attempts = jobRepository.findAttempts(job.jobId()).stream()
                .map(a -> new AttemptResponse(a.attempt(), a.startedAt().toString(),
                        a.finishedAt() == null ? null : a.finishedAt().toString(), a.outcome(),
                        a.failureCode(), a.failureDetail()))
                .toList();
        return new RunResponse(
                job.jobId(), job.runId(), job.state().name(), job.attempt(), job.maxAttempts(),
                job.municipalityIbge(), job.indicatorPack(), job.ruleVersion(), job.referencePeriod(),
                job.sourceId(), job.extractionId(), job.createdAt() == null ? null : job.createdAt().toString(),
                job.startedAt() == null ? null : job.startedAt().toString(),
                job.finishedAt() == null ? null : job.finishedAt().toString(),
                job.lastProgressAt() == null ? null : job.lastProgressAt().toString(),
                job.failureCode(), job.failureDetail(), resultId, attempts);
    }
}
