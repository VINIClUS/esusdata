package esusdata.report;

import esusdata.indicator.IndicatorPackCatalog;
import esusdata.report.model.ExportQuotaExceededException;
import esusdata.report.model.ReportExport;
import esusdata.report.model.ReportExportContent;
import esusdata.report.model.ReportExportRepository;
import esusdata.result.model.PublishedResult;
import esusdata.result.model.ResultRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Generates and reads back aggregate exports (ADR 0024). Generation is synchronous and reads only
 * the published results in SQLite, never the PEC (§1.13 L521), so an export has no states: it
 * either exists, complete, or the request failed. The caller has already checked the scope.
 */
public final class ReportExportService {

    public static final String FORMAT = "CSV";

    /** At most two years of competências per export. */
    static final int MAX_PERIODS = 24;

    /** Exports one user may create per {@link #QUOTA_WINDOW}. */
    static final int QUOTA = 10;

    static final Duration QUOTA_WINDOW = Duration.ofHours(1);

    /** How long a generated file stays downloadable (§1.12 L475 retention). */
    static final Duration RETENTION = Duration.ofDays(7);

    static final int RECENT_LIMIT = 20;

    private final ReportExportRepository exportRepository;
    private final ResultRepository resultRepository;
    private final Clock clock;

    public ReportExportService(
            ReportExportRepository exportRepository, ResultRepository resultRepository, Clock clock) {
        this.exportRepository = exportRepository;
        this.resultRepository = resultRepository;
        this.clock = clock;
    }

    /**
     * Generates and stores one export of the newest published results in the range.
     *
     * @throws IllegalArgumentException for a malformed or too long range, or an unknown pack
     * @throws ExportQuotaExceededException when the user already reached {@link #QUOTA}
     */
    public ReportExport create(
            String userId, String municipalityIbge, String indicatorPack, String fromPeriod, String toPeriod) {
        YearMonth from = parsePeriod("fromPeriod", fromPeriod);
        YearMonth to = parsePeriod("toPeriod", toPeriod);
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("fromPeriod must not be after toPeriod");
        }
        if (ChronoUnit.MONTHS.between(from, to) >= MAX_PERIODS) {
            throw new IllegalArgumentException("an export covers at most " + MAX_PERIODS + " competências");
        }
        String pack = indicatorPack == null || indicatorPack.isBlank() ? null : indicatorPack;
        if (pack != null && IndicatorPackCatalog.find(pack).isEmpty()) {
            throw new IllegalArgumentException("unknown indicator pack: " + pack);
        }

        Instant now = now();
        exportRepository.purgeExpired(now);
        List<PublishedResult> results =
                resultRepository.findLatestPublishedInRange(municipalityIbge, pack, from.toString(), to.toString());
        ReportExport export = new ReportExport(
                "exp-" + UUID.randomUUID(),
                municipalityIbge,
                pack,
                from.toString(),
                to.toString(),
                FORMAT,
                results.size(),
                userId,
                now.toString(),
                now.plus(RETENTION).toString());
        if (!exportRepository.insertWithinQuota(export, ReportCsv.render(results), now.minus(QUOTA_WINDOW), QUOTA)) {
            throw new ExportQuotaExceededException(
                    "at most " + QUOTA + " exports per user every " + QUOTA_WINDOW.toMinutes() + " minutes");
        }
        return export;
    }

    public List<ReportExport> recent(String municipalityIbge) {
        Instant now = now();
        exportRepository.purgeExpired(now);
        return exportRepository.listRecent(municipalityIbge, now, RECENT_LIMIT);
    }

    public Optional<ReportExportContent> content(String exportId, String municipalityIbge) {
        return exportRepository.findInScope(exportId, municipalityIbge, now());
    }

    /** Deletes expired exports; also run at boot so files left by a stopped instance go too (L437). */
    public int purgeExpired() {
        return exportRepository.purgeExpired(now());
    }

    /** A server-chosen name: the download never takes a name or path from the client (L409). */
    public static String fileName(ReportExport export) {
        return "esusdata-" + export.municipalityIbge() + "-"
                + (export.indicatorPack() == null ? "todos" : export.indicatorPack()) + "-"
                + export.fromPeriod() + "_" + export.toPeriod() + ".csv";
    }

    // Whole seconds: see JdbcReportExportRepository.iso.
    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    private static YearMonth parsePeriod(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        try {
            return YearMonth.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(name + " must be an ISO YearMonth (yyyy-MM): " + value, e);
        }
    }
}
