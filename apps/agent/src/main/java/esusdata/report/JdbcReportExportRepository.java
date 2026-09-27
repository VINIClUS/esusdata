package esusdata.report;

import esusdata.report.model.ReportExport;
import esusdata.report.model.ReportExportContent;
import esusdata.report.model.ReportExportRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** {@link ReportExportRepository} over the {@code report_exports} table (V6). */
public final class JdbcReportExportRepository implements ReportExportRepository {

    private static final String METADATA_COLUMNS = """
            export_id, municipality_ibge, indicator_pack, from_period, to_period, format, row_count,
            created_by, created_at, expires_at""";

    private static final RowMapper<ReportExport> MAPPER = (rs, rowNum) -> new ReportExport(
            rs.getString("export_id"),
            rs.getString("municipality_ibge"),
            rs.getString("indicator_pack"),
            rs.getString("from_period"),
            rs.getString("to_period"),
            rs.getString("format"),
            rs.getInt("row_count"),
            rs.getString("created_by"),
            rs.getString("created_at"),
            rs.getString("expires_at"));

    private final JdbcTemplate jdbc;

    public JdbcReportExportRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean insertWithinQuota(ReportExport export, byte[] content, Instant since, int quota) {
        int inserted = jdbc.update(
                "INSERT INTO report_exports (" + METADATA_COLUMNS + ", content)" + """

                        SELECT ?,?,?,?,?,?,?,?,?,?,?
                         WHERE (SELECT count(*) FROM report_exports
                                 WHERE created_by = ? AND created_at > ?) < ?
                        """,
                export.exportId(),
                export.municipalityIbge(),
                export.indicatorPack(),
                export.fromPeriod(),
                export.toPeriod(),
                export.format(),
                export.rowCount(),
                export.createdBy(),
                export.createdAt(),
                export.expiresAt(),
                content,
                export.createdBy(),
                iso(since),
                quota);
        return inserted == 1;
    }

    @Override
    public Optional<ReportExportContent> findInScope(String exportId, String municipalityIbge, Instant now) {
        return jdbc
                .query(
                        "SELECT " + METADATA_COLUMNS + ", content FROM report_exports"
                                + " WHERE export_id = ? AND municipality_ibge = ? AND expires_at > ?",
                        (rs, rowNum) -> new ReportExportContent(MAPPER.mapRow(rs, rowNum), rs.getBytes("content")),
                        exportId,
                        municipalityIbge,
                        iso(now))
                .stream()
                .findFirst();
    }

    @Override
    public List<ReportExport> listRecent(String municipalityIbge, Instant now, int limit) {
        return jdbc.query(
                "SELECT " + METADATA_COLUMNS + " FROM report_exports"
                        + " WHERE municipality_ibge = ? AND expires_at > ?"
                        + " ORDER BY created_at DESC, export_id DESC LIMIT ?",
                MAPPER,
                municipalityIbge,
                iso(now),
                limit);
    }

    @Override
    public int purgeExpired(Instant now) {
        return jdbc.update("DELETE FROM report_exports WHERE expires_at <= ?", iso(now));
    }

    /**
     * Instants are compared as ISO-8601 strings, which sort in time order only at one precision:
     * {@code 10:00:00Z} sorts after {@code 10:00:00.5Z}. Every stored and compared instant is
     * therefore whole seconds; {@link ReportExportService} writes them that way.
     */
    static String iso(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS).toString();
    }
}
