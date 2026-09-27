package esusdata.report;

import esusdata.auth.ApiAuthorization;
import esusdata.auth.model.AuthAuditWriter;
import esusdata.auth.model.AuthenticatedSession;
import esusdata.auth.model.Permission;
import esusdata.report.dto.CreateExportRequest;
import esusdata.report.dto.ExportResponse;
import esusdata.report.model.ReportExport;
import esusdata.report.model.ReportExportContent;
import esusdata.web.ApiNotFoundException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Aggregate CSV exports of published results (ADR 0024, §1.10 L393). Same permission as {@code GET
 * /results}: a municipality-wide {@code READ_CLINICAL} — a team-scoped grant never reaches the
 * aggregate, and a technical admin holds no {@code READ_CLINICAL}. No recent reauth: §1.12.7 L539
 * asks for it only on an individualized export, and this one holds no record. Every download
 * checks the scope again and reads only an unexpired export (L409).
 */
@RestController
public class ReportExportController {

    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final ReportExportService exportService;
    private final ApiAuthorization authorization;
    private final AuthAuditWriter authAuditWriter;
    private final Clock clock;

    public ReportExportController(
            ReportExportService exportService,
            ApiAuthorization authorization,
            AuthAuditWriter authAuditWriter,
            Clock clock) {
        this.exportService = exportService;
        this.authorization = authorization;
        this.authAuditWriter = authAuditWriter;
        this.clock = clock;
    }

    @PostMapping("/api/v1/exports")
    public ResponseEntity<ExportResponse> create(
            @AuthenticationPrincipal AuthenticatedSession session, @RequestBody CreateExportRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("a request body is required");
        }
        authorization.requireObjectScope(session, Permission.READ_CLINICAL, request.municipalityIbge());

        ReportExport export = exportService.create(
                session.userId(),
                request.municipalityIbge(),
                request.indicatorPack(),
                request.fromPeriod(),
                request.toPeriod());
        // Validated values only: the scope check vouched for the municipality, the service parsed
        // the periods, and the row count is a number.
        authAuditWriter.record(
                clock.instant(),
                session.userId(),
                "EXPORT_CREATED",
                export.exportId(),
                "SUCCESS",
                "{\"municipalityIbge\":\"" + export.municipalityIbge() + "\",\"fromPeriod\":\""
                        + export.fromPeriod() + "\",\"toPeriod\":\"" + export.toPeriod() + "\",\"rowCount\":"
                        + export.rowCount() + "}");
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(export));
    }

    /** The municipality's unexpired exports, newest first — not only the caller's own. */
    @GetMapping("/api/v1/exports")
    public List<ExportResponse> list(
            @AuthenticationPrincipal AuthenticatedSession session, @RequestParam String municipalityIbge) {
        authorization.requireObjectScope(session, Permission.READ_CLINICAL, municipalityIbge);
        return exportService.recent(municipalityIbge).stream()
                .map(ReportExportController::toResponse)
                .toList();
    }

    @GetMapping("/api/v1/exports/{id}/content")
    public ResponseEntity<byte[]> content(
            @AuthenticationPrincipal AuthenticatedSession session,
            @PathVariable("id") String id,
            @RequestParam String municipalityIbge) {
        authorization.requireObjectScope(session, Permission.READ_CLINICAL, municipalityIbge);
        ReportExportContent content = exportService
                .content(id, municipalityIbge)
                .orElseThrow(() -> new ApiNotFoundException("unknown or expired export: " + id));
        authAuditWriter.record(
                clock.instant(),
                session.userId(),
                "EXPORT_DOWNLOADED",
                content.export().exportId(),
                "SUCCESS",
                "{\"municipalityIbge\":\"" + content.export().municipalityIbge() + "\"}");
        return ResponseEntity.ok()
                .contentType(TEXT_CSV)
                .cacheControl(CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(ReportExportService.fileName(content.export()))
                                .build()
                                .toString())
                .body(content.content());
    }

    private static ExportResponse toResponse(ReportExport export) {
        return new ExportResponse(
                export.exportId(),
                ReportExportService.fileName(export),
                export.municipalityIbge(),
                export.indicatorPack(),
                export.fromPeriod(),
                export.toPeriod(),
                export.format(),
                export.rowCount(),
                export.createdAt(),
                export.expiresAt());
    }
}
