package esusdata.source;

import esusdata.auth.ApiAuthorization;
import esusdata.auth.model.AuthenticatedSession;
import esusdata.auth.model.Permission;
import esusdata.source.dto.CoverageResponse;
import esusdata.source.dto.CreateSourceRequest;
import esusdata.source.dto.IsolationCheckRequest;
import esusdata.source.dto.IsolationCheckResponse;
import esusdata.source.dto.LastDiagnosticResponse;
import esusdata.source.dto.SourceRequirementResponse;
import esusdata.source.dto.SourceResponse;
import esusdata.source.dto.SourceTestResponse;
import esusdata.source.model.LastCoverage;
import esusdata.source.model.LastDiagnostic;
import esusdata.source.model.LastIsolationCheck;
import esusdata.source.model.SourceRecord;
import esusdata.web.ApiNotFoundException;
import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * §1.10: source registration and its network/read/capability/budget diagnostic. {@code
 * ModuleBoundaryTest.apiDoesNotDependOnPecAdapterOrSourceConnector} forbids this controller from
 * touching {@code sourceconnector} directly — {@link SourceDiagnosticsService} is the seam, running
 * the diagnostic through the same {@code AllowedDestinations} check and execution plane binary the
 * live acquisition path uses (ADR 0017).
 */
@RestController
public class SourceController {

    private static final String UNKNOWN_SOURCE = "unknown source: ";

    private final SourceRepository sourceRepository;
    private final SourceDiagnosticsService sourceDiagnosticsService;
    private final SourceRequirementsService sourceRequirementsService;
    private final SourceIsolationService sourceIsolationService;
    private final SourceCoverageService sourceCoverageService;
    private final ApiAuthorization authorization;
    private final Clock clock;

    public SourceController(
            SourceRepository sourceRepository,
            SourceDiagnosticsService sourceDiagnosticsService,
            SourceRequirementsService sourceRequirementsService,
            SourceIsolationService sourceIsolationService,
            SourceCoverageService sourceCoverageService,
            ApiAuthorization authorization,
            Clock clock) {
        this.sourceRepository = sourceRepository;
        this.sourceDiagnosticsService = sourceDiagnosticsService;
        this.sourceRequirementsService = sourceRequirementsService;
        this.sourceIsolationService = sourceIsolationService;
        this.sourceCoverageService = sourceCoverageService;
        this.authorization = authorization;
        this.clock = clock;
    }

    /**
     * Every source the caller may manage, narrowed rather than refused: an installation-scoped
     * {@code MANAGE_SOURCE} (the bootstrap admin's grant) sees all of them, a municipal one only its
     * own municipality's. Takes no municipality because {@code GET /auth/me} lists only
     * {@code READ_CLINICAL} municipalities, which a technical admin never holds (ENG-45). Read-only,
     * so no recent reauth; {@code secretRef} is a reference, never the secret value (§1.12.7 L550).
     * Each source carries its last diagnostic, isolation check and coverage check, each read for all
     * of them in one query and kept only if it ran against the configuration version this listing
     * read.
     */
    @GetMapping("/api/v1/sources")
    public List<SourceResponse> list(@AuthenticationPrincipal AuthenticatedSession session) {
        Predicate<String> permitted = authorization.permittedMunicipalities(session, Permission.MANAGE_SOURCE);
        Map<String, LastDiagnostic> diagnostics = sourceRepository.findLastDiagnostics();
        Map<String, LastIsolationCheck> isolationChecks = sourceRepository.findLastIsolationChecks();
        Map<String, LastCoverage> coverages = sourceRepository.findLastCoverages();
        return sourceRepository.findAll().stream()
                .filter(source -> permitted.test(source.municipalityIbge()))
                .map(source -> toResponse(
                        source,
                        Optional.ofNullable(diagnostics.get(source.id()))
                                .filter(diagnostic -> diagnostic.appliesTo(source))
                                .orElse(null),
                        Optional.ofNullable(isolationChecks.get(source.id()))
                                .filter(check -> check.appliesTo(source))
                                .orElse(null),
                        Optional.ofNullable(coverages.get(source.id()))
                                .filter(coverage -> coverage.appliesTo(source))
                                .orElse(null)))
                .toList();
    }

    /**
     * The requirements one source meets (issue #22). Resolved before the scope check, in the same
     * order as {@code POST /sources/{id}/test}, so an unknown id and another municipality's source
     * are the same opaque 404. Read-only, so no recent reauth.
     */
    @GetMapping("/api/v1/sources/{id}/requirements")
    public List<SourceRequirementResponse> requirements(
            @AuthenticationPrincipal AuthenticatedSession session, @PathVariable("id") String id) {
        SourceRecord source =
                sourceRepository.findById(id).orElseThrow(() -> new ApiNotFoundException(UNKNOWN_SOURCE + id));
        authorization.requireObjectScope(session, Permission.MANAGE_SOURCE, source.municipalityIbge());

        return sourceRequirementsService.requirements(source, sourceRepository.findLastDiagnostic(id)).stream()
                .map(requirement ->
                        new SourceRequirementResponse(requirement.code().name(), requirement.ok()))
                .toList();
    }

    @PostMapping("/api/v1/sources")
    public ResponseEntity<SourceResponse> createSource(
            @AuthenticationPrincipal AuthenticatedSession session, @RequestBody CreateSourceRequest request) {
        authorization.requireObjectScope(session, Permission.MANAGE_SOURCE, request.municipalityIbge());
        authorization.requireRecentReauth(session);

        Optional<SourceRecord> existing = sourceRepository.findById(request.id());
        if (existing.isPresent() && !existing.get().municipalityIbge().equals(request.municipalityIbge())) {
            throw new ApiNotFoundException("source not found");
        }
        int version = existing.map(s -> s.sourceConfigurationVersion() + 1).orElse(1);

        SourceRecord record = new SourceRecord(
                request.id(),
                version,
                request.sourceFamily(),
                request.pecInstallationRole(),
                request.sourceLocationKind(),
                request.host(),
                request.port(),
                request.databaseName(),
                request.dbUser(),
                request.secretRef(),
                request.municipalityIbge(),
                request.pecVersion(),
                request.readModel(),
                clock.instant().toString());
        sourceRepository.upsert(record);
        // A new configuration version has no diagnostic nor isolation check yet.
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(record, null, null, null));
    }

    @PostMapping("/api/v1/sources/{id}/test")
    public SourceTestResponse test(
            @AuthenticationPrincipal AuthenticatedSession session, @PathVariable("id") String id) {
        SourceRecord source =
                sourceDiagnosticsService.find(id).orElseThrow(() -> new ApiNotFoundException(UNKNOWN_SOURCE + id));
        authorization.requireObjectScope(session, Permission.MANAGE_SOURCE, source.municipalityIbge());
        authorization.requireRecentReauth(session);

        SourceDiagnosticsService.Diagnostics diagnostics = sourceDiagnosticsService.test(id);
        return new SourceTestResponse(
                diagnostics.outcome().name(),
                diagnostics.detail(),
                diagnostics.maxRows(),
                diagnostics.maxDurationMs(),
                diagnostics.statementTimeoutMs());
    }

    /**
     * Counts one competência's atendimentos in the source's PEC per municipality code (ADR 0023).
     * Same order as {@code POST /sources/{id}/test}: an unknown id and another municipality's source
     * are the same opaque 404, and it needs a recent reauth because it opens a session to the PEC.
     */
    @PostMapping("/api/v1/sources/{id}/isolation-check")
    public IsolationCheckResponse checkIsolation(
            @AuthenticationPrincipal AuthenticatedSession session,
            @PathVariable("id") String id,
            @RequestBody IsolationCheckRequest request) {
        SourceRecord source =
                sourceIsolationService.find(id).orElseThrow(() -> new ApiNotFoundException(UNKNOWN_SOURCE + id));
        authorization.requireObjectScope(session, Permission.MANAGE_SOURCE, source.municipalityIbge());
        YearMonth referencePeriod = parseReferencePeriod(request == null ? null : request.referencePeriod());
        if (!SourceIsolationService.canCheck(source)) {
            throw new IllegalArgumentException("source is not a PEC source with a complete identity: " + id);
        }
        authorization.requireRecentReauth(session);

        return toResponse(sourceIsolationService.check(id, referencePeriod));
    }

    /**
     * Which competências of the last {@value SourceCoverageService#WINDOW_MONTHS} months (plus the
     * current one) hold atendimentos of the source's municipality (ADR 0027). Same order and the
     * same recent reauth as {@code POST /sources/{id}/isolation-check}: it opens a PEC session.
     */
    @PostMapping("/api/v1/sources/{id}/coverage-check")
    public CoverageResponse checkCoverage(
            @AuthenticationPrincipal AuthenticatedSession session, @PathVariable("id") String id) {
        SourceRecord source =
                sourceCoverageService.find(id).orElseThrow(() -> new ApiNotFoundException(UNKNOWN_SOURCE + id));
        authorization.requireObjectScope(session, Permission.MANAGE_SOURCE, source.municipalityIbge());
        if (!SourceIsolationService.canCheck(source)) {
            throw new IllegalArgumentException("source is not a PEC source with a complete identity: " + id);
        }
        authorization.requireRecentReauth(session);

        return toResponse(sourceCoverageService.check(id));
    }

    private static YearMonth parseReferencePeriod(String referencePeriod) {
        if (referencePeriod == null || referencePeriod.isBlank()) {
            throw new IllegalArgumentException("referencePeriod is required");
        }
        try {
            return YearMonth.parse(referencePeriod);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(
                    "referencePeriod must be an ISO YearMonth (yyyy-MM): " + referencePeriod, e);
        }
    }

    private static IsolationCheckResponse toResponse(LastIsolationCheck check) {
        return new IsolationCheckResponse(
                check.referencePeriod(),
                check.outcome(),
                check.registeredCount(),
                check.otherMunicipalityCount(),
                check.otherMunicipalityCodes(),
                check.unidentifiedCount(),
                check.checkedAt());
    }

    private static CoverageResponse toResponse(LastCoverage coverage) {
        return new CoverageResponse(
                coverage.windowFrom(),
                coverage.windowToExclusive(),
                coverage.outcome(),
                coverage.periods().stream()
                        .map(period -> new CoverageResponse.PeriodCount(period.referencePeriod(), period.count()))
                        .toList(),
                coverage.checkedAt());
    }

    private static SourceResponse toResponse(
            SourceRecord record,
            LastDiagnostic lastDiagnostic,
            LastIsolationCheck lastIsolationCheck,
            LastCoverage lastCoverage) {
        return new SourceResponse(
                record.id(),
                record.sourceConfigurationVersion(),
                record.sourceFamily(),
                record.pecInstallationRole(),
                record.sourceLocationKind(),
                record.host(),
                record.port(),
                record.databaseName(),
                record.dbUser(),
                record.secretRef(),
                record.municipalityIbge(),
                record.pecVersion(),
                record.readModel(),
                record.createdAt(),
                lastDiagnostic == null
                        ? null
                        : new LastDiagnosticResponse(
                                lastDiagnostic.outcome(), lastDiagnostic.detail(), lastDiagnostic.testedAt()),
                lastIsolationCheck == null ? null : toResponse(lastIsolationCheck),
                lastCoverage == null ? null : toResponse(lastCoverage));
    }
}
