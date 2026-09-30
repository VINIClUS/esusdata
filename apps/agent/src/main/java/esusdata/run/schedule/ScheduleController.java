package esusdata.run.schedule;

import esusdata.auth.ApiAuthorization;
import esusdata.auth.model.AuthenticatedSession;
import esusdata.auth.model.Permission;
import esusdata.result.model.ResultRepository;
import esusdata.source.SourceIsolationService;
import esusdata.source.SourceRepository;
import esusdata.source.model.LastCoverage;
import esusdata.source.model.SourceRecord;
import esusdata.web.ApiNotFoundException;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Execução screen's source side (ADR 0028): what can be run and the competência scheduler.
 * Every route takes {@code RUN_INDICATOR} or {@code MANAGE_SOURCE} in the source's municipality —
 * the manager runs indicators, the admin manages the source, and neither gains the other's
 * permission here: the responses are aggregates and settings, never records nor secrets. The two
 * mutations need a recent reauth; "Verificar agora" also opens a PEC session.
 */
@RestController
public class ScheduleController {

    private final SourceRepository sourceRepository;
    private final ResultRepository resultRepository;
    private final JdbcScheduleRepository scheduleRepository;
    private final CoverageScheduler scheduler;
    private final ApiAuthorization authorization;

    public ScheduleController(
            SourceRepository sourceRepository,
            ResultRepository resultRepository,
            JdbcScheduleRepository scheduleRepository,
            CoverageScheduler scheduler,
            ApiAuthorization authorization) {
        this.sourceRepository = sourceRepository;
        this.resultRepository = resultRepository;
        this.scheduleRepository = scheduleRepository;
        this.scheduler = scheduler;
        this.authorization = authorization;
    }

    /** Toggles one source's scheduler. */
    public record ScheduleSwitch(Boolean enabled) {}

    /** The municipality's PEC sources, each with its competências and scheduler. */
    @GetMapping("/api/v1/run-sources")
    public List<RunSourceResponse> runSources(
            @AuthenticationPrincipal AuthenticatedSession session,
            @RequestParam("municipalityIbge") String municipalityIbge) {
        authorization.requireObjectScopeForEither(
                session, municipalityIbge, Permission.RUN_INDICATOR, Permission.MANAGE_SOURCE);
        Map<String, LastCoverage> coverages = sourceRepository.findLastCoverages();
        Set<String> published = new HashSet<>(resultRepository.findPublishedPeriods(municipalityIbge));
        return sourceRepository.findAll().stream()
                .filter(source -> municipalityIbge.equals(source.municipalityIbge()))
                .filter(SourceIsolationService::canCheck)
                .map(source -> toResponse(source, coverages.get(source.id()), published))
                .toList();
    }

    @PutMapping("/api/v1/sources/{id}/schedule")
    public RunSourceResponse.Schedule switchSchedule(
            @AuthenticationPrincipal AuthenticatedSession session,
            @PathVariable("id") String id,
            @RequestBody ScheduleSwitch request) {
        SourceRecord source = authorizedSource(session, id);
        if (request == null || request.enabled() == null) {
            throw new IllegalArgumentException("enabled is required");
        }
        authorization.requireRecentReauth(session);
        scheduleRepository.setEnabled(source.id(), request.enabled());
        return schedule(scheduleRepository.find(source.id()));
    }

    /** "Verificar agora": one tick for this source, now — coverage refresh and at most one job. */
    @PostMapping("/api/v1/sources/{id}/schedule/run-now")
    public RunSourceResponse.Schedule runNow(
            @AuthenticationPrincipal AuthenticatedSession session, @PathVariable("id") String id) {
        SourceRecord source = authorizedSource(session, id);
        authorization.requireRecentReauth(session);
        return schedule(scheduler.tick(source));
    }

    /** Unknown, not a PEC source, or another municipality's: the same opaque 404. */
    private SourceRecord authorizedSource(AuthenticatedSession session, String id) {
        SourceRecord source =
                sourceRepository.findById(id).orElseThrow(() -> new ApiNotFoundException("unknown source: " + id));
        authorization.requireObjectScopeForEither(
                session, source.municipalityIbge(), Permission.RUN_INDICATOR, Permission.MANAGE_SOURCE);
        if (!SourceIsolationService.canCheck(source)) {
            throw new ApiNotFoundException("not a schedulable source: " + id);
        }
        return source;
    }

    private RunSourceResponse toResponse(SourceRecord source, LastCoverage coverage, Set<String> published) {
        LastCoverage current = coverage != null && coverage.appliesTo(source) ? coverage : null;
        return new RunSourceResponse(
                source.id(),
                source.pecVersion(),
                current == null ? null : current.outcome(),
                current == null ? null : current.checkedAt(),
                current == null
                        ? List.of()
                        : current.periods().stream()
                                .map(period -> new RunSourceResponse.Period(
                                        period.referencePeriod(),
                                        period.count(),
                                        published.contains(period.referencePeriod())))
                                .toList(),
                schedule(scheduleRepository.find(source.id())));
    }

    private RunSourceResponse.Schedule schedule(ScheduleState state) {
        CoverageScheduler.Settings settings = scheduler.settings();
        return new RunSourceResponse.Schedule(
                settings.enabled(),
                state.enabled(),
                settings.interval().toHours(),
                settings.settleDays(),
                scheduler.nextTickAt().map(Instant::toString).orElse(null),
                state.lastTickAt(),
                state.lastOutcome(),
                state.lastDetail(),
                state.lastJobId(),
                state.lastPeriod());
    }
}
