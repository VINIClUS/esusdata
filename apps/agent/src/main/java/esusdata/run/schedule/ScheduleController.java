package esusdata.run.schedule;

import esusdata.auth.ApiAuthorization;
import esusdata.auth.model.AuthenticatedSession;
import esusdata.auth.model.Permission;
import esusdata.indicator.model.IndicatorRule;
import esusdata.result.model.ResultRepository;
import esusdata.source.SourceIsolationService;
import esusdata.source.SourceRepository;
import esusdata.source.model.LastCoverage;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.UnsupportedSourceException;
import esusdata.web.ApiNotFoundException;
import java.time.Instant;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The Execução screen's source side (ADR 0028): what can be run — per pack since ADR 0030 — and the
 * competência scheduler.
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
    private final SourcePacks sourcePacks;

    public ScheduleController(
            SourceRepository sourceRepository,
            ResultRepository resultRepository,
            JdbcScheduleRepository scheduleRepository,
            CoverageScheduler scheduler,
            ApiAuthorization authorization,
            SourcePacks sourcePacks) {
        this.sourceRepository = sourceRepository;
        this.resultRepository = resultRepository;
        this.scheduleRepository = scheduleRepository;
        this.scheduler = scheduler;
        this.authorization = authorization;
        this.sourcePacks = sourcePacks;
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
        Map<String, List<String>> publishedPacks =
                packsByPeriod(resultRepository.findPublishedPeriodsByPack(municipalityIbge));
        return sourceRepository.findAll().stream()
                .filter(source -> municipalityIbge.equals(source.municipalityIbge()))
                .filter(SourceIsolationService::canCheck)
                .map(source -> toResponse(source, coverages.get(source.id()), publishedPacks))
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

    private RunSourceResponse toResponse(
            SourceRecord source, LastCoverage coverage, Map<String, List<String>> publishedPacks) {
        LastCoverage current = coverage != null && coverage.appliesTo(source) ? coverage : null;
        List<SourcePacks.Availability> packs = sourcePacks.of(source);
        List<String> computable = packs.stream()
                .filter(SourcePacks.Availability::available)
                .map(SourcePacks.Availability::indicatorPack)
                .toList();
        return new RunSourceResponse(
                source.id(),
                source.pecVersion(),
                current == null ? null : current.outcome(),
                current == null ? null : current.checkedAt(),
                current == null
                        ? List.of()
                        : selectable(current, packs).stream()
                                .map(period -> period(period, computable, publishedPacks))
                                .toList(),
                packs.stream().map(ScheduleController::pack).toList(),
                schedule(scheduleRepository.find(source.id())));
    }

    /**
     * The competências a person can run: the covered ones, and — when the source computes a pack
     * beyond atendimentos individuais (C2–C7) — every settled month from the oldest covered one on,
     * with zero atendimentos, as the scheduler sees them.
     */
    private List<LastCoverage.PeriodCount> selectable(LastCoverage coverage, List<SourcePacks.Availability> packs) {
        List<IndicatorRule> available = packs.stream()
                .filter(SourcePacks.Availability::available)
                .map(SourcePacks.Availability::rule)
                .toList();
        if (SourcePacks.attendanceScoped(available).size() == available.size()) {
            return coverage.periods();
        }
        List<YearMonth> covered = coverage.periods().stream()
                .map(p -> YearMonth.parse(p.referencePeriod()))
                .toList();
        return withGaps(coverage.periods(), scheduler.settledSinceOldest(covered));
    }

    /** {@code covered} plus each month of {@code settled} it lacks, with zero atendimentos, oldest first. */
    static List<LastCoverage.PeriodCount> withGaps(List<LastCoverage.PeriodCount> covered, List<YearMonth> settled) {
        Map<YearMonth, Long> counts = new TreeMap<>();
        covered.forEach(p -> counts.merge(YearMonth.parse(p.referencePeriod()), p.count(), Long::sum));
        settled.forEach(month -> counts.putIfAbsent(month, 0L));
        List<LastCoverage.PeriodCount> periods = new ArrayList<>(counts.size());
        counts.forEach((month, count) -> periods.add(new LastCoverage.PeriodCount(month.toString(), count)));
        return periods;
    }

    /** Published when every pack the source can compute is: never for a source that computes none. */
    private static RunSourceResponse.Period period(
            LastCoverage.PeriodCount period, List<String> computable, Map<String, List<String>> publishedPacks) {
        List<String> published = publishedPacks.getOrDefault(period.referencePeriod(), List.of());
        return new RunSourceResponse.Period(
                period.referencePeriod(),
                period.count(),
                !computable.isEmpty() && published.containsAll(computable),
                published);
    }

    private static RunSourceResponse.Pack pack(SourcePacks.Availability availability) {
        return new RunSourceResponse.Pack(
                availability.indicatorPack(),
                availability.rule().descriptor().ruleVersion(),
                availability.available() ? "AVAILABLE" : UnsupportedSourceException.CODE,
                availability.missingCapabilities());
    }

    /** {@code competência → packs published in it}, packs in id order (C1 first). */
    private static Map<String, List<String>> packsByPeriod(Map<String, Set<String>> periodsByPack) {
        Map<String, List<String>> byPeriod = new TreeMap<>();
        new TreeMap<>(periodsByPack)
                .forEach((pack, periods) -> periods.forEach(period ->
                        byPeriod.computeIfAbsent(period, p -> new ArrayList<>()).add(pack)));
        Map<String, List<String>> readOnly = new TreeMap<>();
        byPeriod.forEach((period, packs) -> readOnly.put(period, List.copyOf(packs)));
        return readOnly;
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
