package br.gov.observatorioaps.sources.adapter.in.http;

import br.gov.observatorioaps.access.domain.AuthenticatedSession;
import br.gov.observatorioaps.access.domain.Permission;
import br.gov.observatorioaps.execution.application.SourceDiagnosticsService;
import br.gov.observatorioaps.sources.domain.SourceRecord;
import br.gov.observatorioaps.sources.domain.SourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Optional;
import br.gov.observatorioaps.platform.web.ApiNotFoundException;
import br.gov.observatorioaps.access.application.ApiAuthorization;

/**
 * §1.10: source registration and its network/read/capability/budget diagnostic.
 * {@code ModuleBoundaryTest} forbids this HTTP adapter from touching
 * {@code execution.adapter.out.pec} directly — {@link SourceDiagnosticsService} in {@code
 * execution.application} is the seam, reusing the exact {@code AllowedDestinations}/{@code
 * PecDataSourceFactory} the live acquisition path uses.
 */
@RestController
public class SourceController {

    private final SourceRepository sourceRepository;
    private final SourceDiagnosticsService sourceDiagnosticsService;
    private final ApiAuthorization authorization;
    private final Clock clock;

    public SourceController(
            SourceRepository sourceRepository, SourceDiagnosticsService sourceDiagnosticsService,
            ApiAuthorization authorization, Clock clock) {
        this.sourceRepository = sourceRepository;
        this.sourceDiagnosticsService = sourceDiagnosticsService;
        this.authorization = authorization;
        this.clock = clock;
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
                request.id(), version, request.sourceFamily(), request.pecInstallationRole(),
                request.sourceLocationKind(), request.host(), request.port(), request.databaseName(),
                request.dbUser(), request.secretRef(), request.municipalityIbge(), request.pecVersion(),
                request.readModel(), clock.instant().toString());
        sourceRepository.upsert(record);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(record));
    }

    @PostMapping("/api/v1/sources/{id}/test")
    public SourceTestResponse test(
            @AuthenticationPrincipal AuthenticatedSession session, @PathVariable("id") String id) {
        SourceRecord source = sourceDiagnosticsService.find(id)
                .orElseThrow(() -> new ApiNotFoundException("unknown source: " + id));
        authorization.requireObjectScope(session, Permission.MANAGE_SOURCE, source.municipalityIbge());
        authorization.requireRecentReauth(session);

        SourceDiagnosticsService.Diagnostics diagnostics = sourceDiagnosticsService.test(id);
        return new SourceTestResponse(
                diagnostics.outcome().name(), diagnostics.detail(), diagnostics.maxRows(),
                diagnostics.maxDurationMs(), diagnostics.statementTimeoutMs());
    }

    private SourceResponse toResponse(SourceRecord record) {
        return new SourceResponse(
                record.id(), record.sourceConfigurationVersion(), record.sourceFamily(),
                record.pecInstallationRole(), record.sourceLocationKind(), record.host(), record.port(),
                record.databaseName(), record.dbUser(), record.secretRef(), record.municipalityIbge(),
                record.pecVersion(), record.readModel(), record.createdAt());
    }
}
