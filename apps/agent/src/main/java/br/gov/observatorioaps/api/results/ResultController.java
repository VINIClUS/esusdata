package br.gov.observatorioaps.api.results;

import br.gov.observatorioaps.identityaccess.domain.AuthAuditWriter;
import br.gov.observatorioaps.identityaccess.domain.AuthenticatedSession;
import br.gov.observatorioaps.identityaccess.domain.Permission;
import br.gov.observatorioaps.indicatorpacks.IndicatorPackCatalog;
import br.gov.observatorioaps.resultstore.domain.EvidencePage;
import br.gov.observatorioaps.resultstore.domain.EvidenceRepository;
import br.gov.observatorioaps.resultstore.domain.EvidenceRecord;
import br.gov.observatorioaps.resultstore.domain.PublishedResult;
import br.gov.observatorioaps.resultstore.domain.ResultRepository;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.time.Clock;
import java.util.List;
import br.gov.observatorioaps.api.auth.ScopeResponse;
import br.gov.observatorioaps.api.security.ApiAuthorization;

/**
 * §1.10 L390/L391: published results and their evidence, filtered by authorized scope. Reads
 * only {@code resultstore} — navigating the panel never consults the PEC or recalculates (L306).
 */
@RestController
public class ResultController {

    private static final String EVIDENCE_ORDERING = "seq_asc";

    private final ResultRepository resultRepository;
    private final EvidenceRepository evidenceRepository;
    private final ApiAuthorization authorization;
    private final AuthAuditWriter authAuditWriter;
    private final Clock clock;
    private final ObjectMapper mapper = new ObjectMapper();

    public ResultController(
            ResultRepository resultRepository, EvidenceRepository evidenceRepository,
            ApiAuthorization authorization, AuthAuditWriter authAuditWriter, Clock clock) {
        this.resultRepository = resultRepository;
        this.evidenceRepository = evidenceRepository;
        this.authorization = authorization;
        this.authAuditWriter = authAuditWriter;
        this.clock = clock;
    }

    @GetMapping("/api/v1/results")
    public List<ResultResponse> results(
            @AuthenticationPrincipal AuthenticatedSession session,
            @RequestParam String municipalityIbge,
            @RequestParam String indicatorPack,
            @RequestParam String referencePeriod) {
        // The municipal aggregate is unscoped by team (§1.12 L427 note in the plan) — a
        // team-scoped grant never authorizes it, only a municipality-wide one.
        authorization.requireObjectScope(session, Permission.READ_CLINICAL, municipalityIbge);
        return resultRepository.findPublished(municipalityIbge, indicatorPack, referencePeriod).stream()
                .map(this::toResponse)
                .toList();
    }

    @GetMapping("/api/v1/results/{id}/evidence")
    public EvidenceResponse evidence(
            @AuthenticationPrincipal AuthenticatedSession session,
            @PathVariable("id") String id,
            @RequestParam String municipalityIbge,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "" + EvidenceRepository.DEFAULT_PAGE_SIZE) int limit) {
        // Unlike the aggregate above, a team-scoped grant IS enough to reach evidence — the rows
        // are narrowed to the caller's own team below, never rejected outright (§1.12 L427).
        authorization.requireAnyMunicipalScope(session, Permission.READ_CLINICAL, municipalityIbge);
        ApiAuthorization.TeamScopeFilter filter =
                authorization.resolveEvidenceTeamFilter(session, Permission.READ_CLINICAL, municipalityIbge);
        String scopeKey = municipalityIbge + "|" + filter.cnes() + "|" + filter.ine();

        Long afterSeq = cursor == null
                ? null
                : EvidenceCursor.decode(cursor, id, EVIDENCE_ORDERING, scopeKey).seq();

        EvidencePage page = evidenceRepository.page(
                id, municipalityIbge, filter.cnes(), filter.ine(), afterSeq, limit);

        List<EvidenceEntryResponse> items = page.items().stream().map(this::toResponse).toList();
        String nextCursor = page.nextCursor() == null
                ? null
                : EvidenceCursor.of(id, EVIDENCE_ORDERING, scopeKey, page.nextCursor()).encode();
        authAuditWriter.record(clock.instant(), session.userId(), "EVIDENCE_READ", id, "SUCCESS",
                "{\"municipalityIbge\":\"" + municipalityIbge
                        + "\",\"itemCount\":" + items.size() + "}");
        return new EvidenceResponse(items, nextCursor);
    }

    private ResultResponse toResponse(PublishedResult result) {
        String unit = IndicatorPackCatalog.find(result.indicatorPack())
                .map(IndicatorPackCatalog.PackEntry::unit)
                .orElse(null);
        return new ResultResponse(
                result.resultId(), result.jobId(), result.runId(), result.extractionId(),
                result.adapterVersion(), result.calculationPolicyVersion(), result.inputFingerprint(),
                result.indicatorPack(), result.ruleVersion(),
                new ScopeResponse(result.municipalityIbge(), null, null),
                result.referencePeriod(), result.status(), result.valueText(), unit,
                result.numeratorText(), result.denominatorText(), result.denominatorKind(),
                result.classification(), result.dataCutoff(), parseLimitations(result.limitationsJson()),
                List.of(result.sourceId()), result.resultNature(), result.validationStatus(),
                result.completenessStatus(), result.consistencyLevel(), result.reproducibilityLevel(),
                result.canonicalSchemaVersion(), result.evidenceGrain(), result.appBuild(),
                result.publishedAt());
    }

    private List<String> parseLimitations(String limitationsJson) {
        if (limitationsJson == null || limitationsJson.isBlank()) {
            return List.of();
        }
        return List.of(mapper.readValue(limitationsJson, String[].class));
    }

    private EvidenceEntryResponse toResponse(EvidenceRecord record) {
        return new EvidenceEntryResponse(
                record.sourceEntityType(), record.sourceRecordId(), record.careDate(),
                record.modality(), record.cnes(), record.ine(), record.cbo(), record.decision(),
                record.criterionVersion());
    }
}
