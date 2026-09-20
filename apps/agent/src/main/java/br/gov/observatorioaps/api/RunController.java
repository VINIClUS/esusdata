package br.gov.observatorioaps.api;

import br.gov.observatorioaps.identityaccess.AuthenticatedSession;
import br.gov.observatorioaps.identityaccess.Permission;
import br.gov.observatorioaps.jobrunner.CancellationRegistry;
import br.gov.observatorioaps.jobrunner.EnqueueRequest;
import br.gov.observatorioaps.jobrunner.IdempotencyResolver;
import br.gov.observatorioaps.jobrunner.Job;
import br.gov.observatorioaps.jobrunner.JobRepository;
import br.gov.observatorioaps.jobrunner.JobState;
import br.gov.observatorioaps.resultstore.ExtractionManifestRepository;
import br.gov.observatorioaps.resultstore.ResultRepository;
import br.gov.observatorioaps.resultstore.SourceRepository;
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
import java.nio.ByteBuffer;
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
    private static final int MAX_CANCEL_ATTEMPTS = 4;

    private final JobRepository jobRepository;
    private final IdempotencyResolver idempotencyResolver;
    private final CancellationRegistry cancellationRegistry;
    private final ResultRepository resultRepository;
    private final SourceRepository sourceRepository;
    private final ExtractionManifestRepository extractionManifestRepository;
    private final ApiAuthorization authorization;
    private final Clock clock;

    public RunController(
            JobRepository jobRepository, IdempotencyResolver idempotencyResolver,
            CancellationRegistry cancellationRegistry, ResultRepository resultRepository,
            SourceRepository sourceRepository, ExtractionManifestRepository extractionManifestRepository,
            ApiAuthorization authorization, Clock clock) {
        this.jobRepository = jobRepository;
        this.idempotencyResolver = idempotencyResolver;
        this.cancellationRegistry = cancellationRegistry;
        this.resultRepository = resultRepository;
        this.sourceRepository = sourceRepository;
        this.extractionManifestRepository = extractionManifestRepository;
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
        requireIdempotencyKey(idempotencyKey);
        requireReferencedObjectsInScope(session, request);

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
        for (int attempt = 0; attempt < MAX_CANCEL_ATTEMPTS; attempt++) {
            if (job.state() == JobState.CANCEL_REQUESTED) {
                // Idempotent: a client that polls and re-clicks cancel on an already-cancelling job
                // gets the current (in-progress) state back, not an error for a cancel that is
                // genuinely proceeding.
                job = findAuthorized(session, id);
                if (job.state() == JobState.CANCEL_REQUESTED) {
                    return toResponse(job);
                }
            }
            Instant now = clock.instant();
            boolean cancelled = switch (job.state()) {
                case QUEUED -> jobRepository.cancelQueued(id, now);
                case RUNNING, STAGED -> {
                    boolean requested = jobRepository.requestCancel(
                            id, job.processInstanceId(), job.executionGeneration(), now);
                    if (requested) {
                        // Best-effort interrupt of an in-flight statement — the CAS above is what
                        // actually matters; this only shortens how long it takes to notice.
                        cancellationRegistry.requestCancel(id);
                    }
                    yield requested;
                }
                default -> false;
            };
            if (cancelled) {
                return toResponse(jobRepository.findById(id).orElseThrow());
            }
            // A worker may win more than one ownership CAS while this request is in flight.
            // Reload while the job remains cancellable so the request follows the newest
            // process/generation instead of dropping the cancellation after one race.
            if (isCancellable(job.state()) && attempt + 1 < MAX_CANCEL_ATTEMPTS) {
                job = findAuthorized(session, id);
            } else {
                break;
            }
        }
        throw new JobNotCancellableException(
                "job " + id + " cannot be cancelled from its current state (" + job.state() + ")");
    }

    private boolean isCancellable(JobState state) {
        return state == JobState.QUEUED || state == JobState.RUNNING || state == JobState.STAGED;
    }

    private Job findAuthorized(AuthenticatedSession session, String id) {
        Job job = jobRepository.findById(id).orElse(null);
        if (job == null) {
            authorization.auditDenied(session, Permission.RUN_INDICATOR, "unknown");
            throw new ApiNotFoundException("unknown job: " + id);
        }
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
        if (request.municipalityIbge() == null || request.municipalityIbge().isBlank()
                || request.indicatorPack() == null || request.indicatorPack().isBlank()
                || request.ruleVersion() == null || request.ruleVersion().isBlank()
                || request.referencePeriod() == null || request.referencePeriod().isBlank()
                || request.sourceId() == null || request.sourceId().isBlank()) {
            throw new IllegalArgumentException(
                "municipalityIbge, indicatorPack, ruleVersion, referencePeriod, and sourceId are required");
        }
        if (request.extractionId() != null && request.extractionId().isBlank()) {
            throw new IllegalArgumentException("extractionId must not be blank when supplied");
        }
    }

    private void requireIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("Idempotency-Key is required");
        }
    }

    /**
     * Resolve every persisted object named by the request before creating the job. A missing or
     * differently-scoped reference is deliberately reported as the same external 404, so the
     * caller cannot use a job failure detail as a cross-municipality metadata oracle.
     */
    private void requireReferencedObjectsInScope(
            AuthenticatedSession session, CreateRunRequest request) {
        var source = sourceRepository.findById(request.sourceId()).orElse(null);
        if (source == null || !request.municipalityIbge().equals(source.municipalityIbge())) {
            authorization.auditDenied(session, Permission.RUN_INDICATOR, request.municipalityIbge());
            throw new ApiNotFoundException("source not found");
        }

        if (request.extractionId() != null) {
            var stored = extractionManifestRepository.findById(request.extractionId()).orElse(null);
            if (stored == null
                    || !request.municipalityIbge().equals(stored.manifest().municipalityIbge())
                    || !request.sourceId().equals(stored.manifest().sourceId())) {
                authorization.auditDenied(session, Permission.RUN_INDICATOR, request.municipalityIbge());
                throw new ApiNotFoundException("extraction not found");
            }
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
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateCanonicalField(digest, request.municipalityIbge());
            updateCanonicalField(digest, request.indicatorPack());
            updateCanonicalField(digest, request.ruleVersion());
            updateCanonicalField(digest, request.referencePeriod());
            updateCanonicalField(digest, request.sourceId());
            updateCanonicalField(digest, request.extractionId());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    /** Length-prefixed UTF-8 fields make delimiters data, not structure, and preserve null vs empty. */
    private void updateCanonicalField(MessageDigest digest, String value) {
        if (value == null) {
            digest.update((byte) 0);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update((byte) 1);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
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
