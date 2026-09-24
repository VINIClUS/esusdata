package esusdata.result;

import esusdata.auth.ApiAuthorization;
import esusdata.auth.dto.ScopeResponse;
import esusdata.auth.model.AuthAuditWriter;
import esusdata.auth.model.AuthenticatedSession;
import esusdata.auth.model.Permission;
import esusdata.indicator.IndicatorPackCatalog;
import esusdata.result.dto.EvidenceCursor;
import esusdata.result.dto.EvidenceEntryResponse;
import esusdata.result.dto.EvidenceResponse;
import esusdata.result.dto.ResultResponse;
import esusdata.result.model.EvidencePage;
import esusdata.result.model.EvidenceRecord;
import esusdata.result.model.EvidenceRepository;
import esusdata.result.model.PublishedResult;
import esusdata.result.model.ResultRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

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
            ResultRepository resultRepository,
            EvidenceRepository evidenceRepository,
            ApiAuthorization authorization,
            AuthAuditWriter authAuditWriter,
            Clock clock) {
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

    /** Lets a client default to the latest competência instead of having one configured. */
    @GetMapping("/api/v1/results/periods")
    public List<String> periods(
            @AuthenticationPrincipal AuthenticatedSession session, @RequestParam String municipalityIbge) {
        authorization.requireObjectScope(session, Permission.READ_CLINICAL, municipalityIbge);
        return resultRepository.findPublishedPeriods(municipalityIbge);
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

        EvidencePage page = evidenceRepository.page(id, municipalityIbge, filter.cnes(), filter.ine(), afterSeq, limit);

        List<EvidenceEntryResponse> items =
                page.items().stream().map(this::toResponse).toList();
        String nextCursor = page.nextCursor() == null
                ? null
                : EvidenceCursor.of(id, EVIDENCE_ORDERING, scopeKey, page.nextCursor())
                        .encode();
        authAuditWriter.record(
                clock.instant(),
                session.userId(),
                "EVIDENCE_READ",
                id,
                "SUCCESS",
                "{\"municipalityIbge\":\"" + municipalityIbge + "\",\"itemCount\":" + items.size() + "}");
        return new EvidenceResponse(items, nextCursor);
    }

    private ResultResponse toResponse(PublishedResult result) {
        String unit = IndicatorPackCatalog.find(result.indicatorPack())
                .map(IndicatorPackCatalog.PackEntry::unit)
                .orElse(null);
        return new ResultResponse(
                result.resultId(),
                result.jobId(),
                result.runId(),
                result.extractionId(),
                result.adapterVersion(),
                result.calculationPolicyVersion(),
                result.inputFingerprint(),
                result.indicatorPack(),
                result.ruleVersion(),
                new ScopeResponse(result.municipalityIbge(), null, null),
                result.referencePeriod(),
                result.status(),
                result.valueText(),
                unit,
                result.numeratorText(),
                result.denominatorText(),
                result.denominatorKind(),
                result.classification(),
                result.dataCutoff(),
                parseLimitations(result.limitationsJson()),
                List.of(result.sourceId()),
                result.resultNature(),
                result.validationStatus(),
                result.completenessStatus(),
                result.consistencyLevel(),
                result.reproducibilityLevel(),
                result.canonicalSchemaVersion(),
                result.evidenceGrain(),
                result.appBuild(),
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
                record.sourceEntityType(),
                record.sourceRecordId(),
                record.careDate(),
                record.modality(),
                record.cnes(),
                record.ine(),
                record.cbo(),
                record.decision(),
                record.criterionVersion());
    }
}
