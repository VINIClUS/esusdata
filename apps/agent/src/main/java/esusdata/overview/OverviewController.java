package esusdata.overview;

import esusdata.auth.ApiAuthorization;
import esusdata.auth.model.AuthenticatedSession;
import esusdata.auth.model.Permission;
import esusdata.indicator.IndicatorPackCatalog;
import esusdata.overview.OverviewResponse.Check;
import esusdata.overview.OverviewResponse.HistoryPoint;
import esusdata.overview.OverviewResponse.Indicator;
import esusdata.overview.OverviewResponse.PendingPeriod;
import esusdata.overview.OverviewResponse.Quality;
import esusdata.overview.OverviewResponse.RecentRun;
import esusdata.result.model.PublishedResult;
import esusdata.result.model.ResultRepository;
import esusdata.run.job.Job;
import esusdata.run.job.JobRepository;
import esusdata.run.schedule.CoverageScheduler;
import esusdata.run.schedule.JdbcScheduleRepository;
import esusdata.run.schedule.SchedulePlanner;
import esusdata.run.schedule.SourcePacks;
import esusdata.source.SourceCoverageService;
import esusdata.source.SourceIsolationService;
import esusdata.source.SourceRepository;
import esusdata.source.model.LastCoverage;
import esusdata.source.model.SourceRecord;
import esusdata.source.pec.UnsupportedSourceException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

/**
 * {@code GET /api/v1/overview} (ADR 0029). The same READ_CLINICAL municipality-wide scope as
 * {@code GET /results}; recent jobs only for who may also run indicators there.
 */
@RestController
public class OverviewController {

    static final int HISTORY_MONTHS = 12;
    static final int RECENT_RUNS = 5;

    private static final String AVAILABLE = "AVAILABLE";
    private static final String NO_SOURCE = "NO_SOURCE";

    private final ResultRepository resultRepository;
    private final SourceRepository sourceRepository;
    private final JdbcScheduleRepository scheduleRepository;
    private final CoverageScheduler scheduler;
    private final JobRepository jobRepository;
    private final ApiAuthorization authorization;
    private final Clock clock;
    private final SourcePacks sourcePacks;
    private final ObjectMapper mapper = new ObjectMapper();

    public OverviewController(
            ResultRepository resultRepository,
            SourceRepository sourceRepository,
            JdbcScheduleRepository scheduleRepository,
            CoverageScheduler scheduler,
            JobRepository jobRepository,
            ApiAuthorization authorization,
            Clock clock,
            SourcePacks sourcePacks) {
        this.resultRepository = resultRepository;
        this.sourceRepository = sourceRepository;
        this.scheduleRepository = scheduleRepository;
        this.scheduler = scheduler;
        this.jobRepository = jobRepository;
        this.authorization = authorization;
        this.clock = clock;
        this.sourcePacks = sourcePacks;
    }

    @GetMapping("/api/v1/overview")
    public OverviewResponse overview(
            @AuthenticationPrincipal AuthenticatedSession session,
            @RequestParam String municipalityIbge,
            @RequestParam(required = false) String referencePeriod) {
        authorization.requireObjectScope(session, Permission.READ_CLINICAL, municipalityIbge);
        List<String> publishedPeriods = resultRepository.findPublishedPeriods(municipalityIbge);
        String period = referencePeriod != null
                ? parse(referencePeriod).toString()
                : publishedPeriods.isEmpty() ? null : publishedPeriods.getFirst();
        LocalDate today = LocalDate.now(clock.withZone(SourceCoverageService.ZONE));
        YearMonth end = period == null ? YearMonth.from(today) : YearMonth.parse(period);

        List<PublishedResult> window = resultRepository.findLatestPublishedInRange(
                municipalityIbge, null, end.minusMonths(HISTORY_MONTHS - 1L).toString(), end.toString());
        Map<String, PublishedResult> current = window.stream()
                .filter(result -> result.referencePeriod().equals(period))
                .collect(Collectors.toMap(PublishedResult::indicatorPack, Function.identity(), (a, b) -> b));
        List<SourceRecord> sources = sourceRepository.findAll().stream()
                .filter(source -> municipalityIbge.equals(source.municipalityIbge()))
                .filter(SourceIsolationService::canCheck)
                .toList();
        List<Indicator> indicators = IndicatorPackCatalog.all().stream()
                .map(pack -> indicator(pack, current.get(pack.id()), sources))
                .toList();

        List<Check> checks = new ArrayList<>();
        List<PendingPeriod> pending = new ArrayList<>();
        collectSourceChecks(
                sources, resultRepository.findPublishedPeriodsByPack(municipalityIbge), today, checks, pending);
        checks.add(OverviewChecks.resultsPublished(period, !current.isEmpty()));

        List<RecentRun> recentRuns = authorization
                        .permittedMunicipalities(session, Permission.RUN_INDICATOR)
                        .test(municipalityIbge)
                ? jobRepository.findRecent(municipalityIbge, RECENT_RUNS).stream()
                        .map(OverviewController::recentRun)
                        .toList()
                : null;

        return new OverviewResponse(
                municipalityIbge,
                period,
                lastUpdate(municipalityIbge, publishedPeriods),
                indicators,
                window.stream()
                        .map(result -> new HistoryPoint(
                                result.referencePeriod(),
                                result.indicatorPack(),
                                result.status(),
                                "COMPUTED".equals(result.status()) ? result.valueText() : null))
                        .toList(),
                quality(current.values()),
                List.copyOf(checks),
                OverviewAlerts.of(period, indicators, checks, pending, recentRuns),
                List.copyOf(pending),
                recentRuns);
    }

    /**
     * One pass over every stored check, not a query per source. Pending competências are the
     * scheduler's own (ADR 0028), per pack the source can compute (ADR 0030), grouped by competência.
     */
    private void collectSourceChecks(
            List<SourceRecord> sources,
            Map<String, Set<String>> publishedByPack,
            LocalDate today,
            List<Check> checks,
            List<PendingPeriod> pending) {
        var diagnostics = sourceRepository.findLastDiagnostics();
        var isolations = sourceRepository.findLastIsolationChecks();
        Map<String, LastCoverage> coverages = sourceRepository.findLastCoverages();
        Map<String, Set<YearMonth>> published = new TreeMap<>();
        publishedByPack.forEach((pack, periods) ->
                published.put(pack, periods.stream().map(YearMonth::parse).collect(Collectors.toSet())));
        CoverageScheduler.Settings settings = scheduler.settings();
        for (SourceRecord source : sources) {
            LastCoverage coverage = coverages.get(source.id());
            checks.addAll(OverviewChecks.of(
                    source,
                    diagnostics.get(source.id()),
                    isolations.get(source.id()),
                    coverage,
                    scheduleRepository.find(source.id()),
                    settings.enabled()));
            if (coverage == null || !coverage.appliesTo(source)) {
                continue;
            }
            Map<YearMonth, Long> counts = coverage.periods().stream()
                    .collect(Collectors.toMap(
                            p -> YearMonth.parse(p.referencePeriod()), LastCoverage.PeriodCount::count, Long::sum));
            List<String> packs = sourcePacks.eligible(source).stream()
                    .map(rule -> rule.descriptor().id())
                    .toList();
            Map<YearMonth, List<String>> byMonth = new TreeMap<>();
            SchedulePlanner.pending(counts.keySet(), packs, published, today, settings.settleDays())
                    .forEach(candidate -> byMonth.computeIfAbsent(candidate.period(), month -> new ArrayList<>())
                            .add(candidate.indicatorPack()));
            byMonth.forEach((month, monthPacks) -> pending.add(
                    new PendingPeriod(source.id(), month.toString(), counts.get(month), List.copyOf(monthPacks))));
        }
    }

    /**
     * Whether some PEC source of the municipality can compute {@code pack} (ADR 0030): every
     * capability it reads — for the Nota Final, every capability of the packs it consolidates —
     * {@code VALIDATED} for the source. Without an available source, the capabilities the closest
     * source lacks.
     */
    private Availability availability(IndicatorPackCatalog.PackEntry pack, List<SourceRecord> sources) {
        if (sources.isEmpty()) {
            return new Availability(NO_SOURCE, List.of());
        }
        List<String> missing = null;
        for (SourceRecord source : sources) {
            List<String> lacking = sourcePacks.missing(readCapabilities(pack), source);
            if (lacking.isEmpty()) {
                return new Availability(AVAILABLE, List.of());
            }
            if (missing == null || lacking.size() < missing.size()) {
                missing = lacking;
            }
        }
        return new Availability(UnsupportedSourceException.CODE, missing);
    }

    private static List<String> readCapabilities(IndicatorPackCatalog.PackEntry pack) {
        if (pack.runnable() || pack.dependsOn().isEmpty()) {
            return pack.requiredCapabilities();
        }
        Set<String> capabilities = new LinkedHashSet<>();
        for (String dependency : pack.dependsOn()) {
            IndicatorPackCatalog.find(dependency).ifPresent(entry -> capabilities.addAll(entry.requiredCapabilities()));
        }
        return List.copyOf(capabilities);
    }

    private record Availability(String status, List<String> missingCapabilities) {}

    private String lastUpdate(String municipalityIbge, List<String> publishedPeriods) {
        if (publishedPeriods.isEmpty()) {
            return null;
        }
        String newest = publishedPeriods.getFirst();
        return resultRepository.findLatestPublishedInRange(municipalityIbge, null, newest, newest).stream()
                .map(PublishedResult::publishedAt)
                .filter(Objects::nonNull)
                .map(Instant::parse)
                .max(Instant::compareTo)
                .map(Instant::toString)
                .orElse(null);
    }

    private Indicator indicator(
            IndicatorPackCatalog.PackEntry pack, PublishedResult result, List<SourceRecord> sources) {
        Availability availability = availability(pack, sources);
        return new Indicator(
                pack.id(),
                pack.ruleVersion(),
                pack.family(),
                pack.unit(),
                pack.code(),
                pack.title(),
                pack.valueKind().name(),
                pack.runnable(),
                availability.status(),
                availability.missingCapabilities(),
                pack.executionEnabled(),
                pack.blockedGates(),
                result == null ? null : result.resultId(),
                result == null ? null : result.status(),
                result == null ? null : result.valueText(),
                result == null ? List.of() : limitations(result.limitationsJson()),
                result == null ? null : result.publishedAt());
    }

    private List<String> limitations(String json) {
        return json == null || json.isBlank() ? List.of() : List.of(mapper.readValue(json, String[].class));
    }

    private static Quality quality(Collection<PublishedResult> results) {
        int completeSnapshot = (int) results.stream()
                .filter(r -> "COMPLETE".equals(r.completenessStatus()) && "SNAPSHOT".equals(r.consistencyLevel()))
                .count();
        return new Quality(results.size(), completeSnapshot);
    }

    private static RecentRun recentRun(Job job) {
        return new RecentRun(
                job.jobId(),
                job.indicatorPack(),
                job.referencePeriod(),
                job.state().name(),
                text(job.createdAt()),
                text(job.finishedAt()),
                job.failureCode());
    }

    private static String text(Instant instant) {
        return instant == null ? null : instant.toString();
    }

    private static YearMonth parse(String referencePeriod) {
        try {
            return YearMonth.parse(referencePeriod);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("referencePeriod must be yyyy-MM: " + referencePeriod, e);
        }
    }
}
