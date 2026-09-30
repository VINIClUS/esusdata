package esusdata.source;

import esusdata.source.model.LastCoverage;
import esusdata.source.model.LastDiagnostic;
import esusdata.source.model.LastIsolationCheck;
import esusdata.source.model.SourceRecord;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Persists {@code sources} rows. {@code secret_ref} is a reference/state string only — the secret
 * value itself never passes through this class (§1.12.7).
 */
public final class JdbcSourceRepository implements SourceRepository {

    private static final String CONFIGURATION_VERSION = "source_configuration_version";

    private static final RowMapper<SourceRecord> MAPPER = (rs, rowNum) -> new SourceRecord(
            rs.getString("id"),
            rs.getInt(CONFIGURATION_VERSION),
            rs.getString("source_family"),
            rs.getString("pec_installation_role"),
            rs.getString("source_location_kind"),
            rs.getString("host"),
            rs.getInt("port"),
            rs.getString("database_name"),
            rs.getString("db_user"),
            rs.getString("secret_ref"),
            rs.getString("municipality_ibge"),
            rs.getString("pec_version"),
            rs.getString("read_model"),
            rs.getString("created_at"));

    private static final RowMapper<LastDiagnostic> DIAGNOSTIC_MAPPER = (rs, rowNum) -> new LastDiagnostic(
            rs.getInt(CONFIGURATION_VERSION),
            rs.getString("outcome"),
            rs.getString("detail"),
            rs.getString("tested_at"));

    private static final String CHECKED = "CHECKED";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<LastCoverage.PeriodCount>> PERIODS = new TypeReference<>() {};

    private static final RowMapper<LastCoverage> COVERAGE_MAPPER = (rs, rowNum) -> new LastCoverage(
            rs.getInt(CONFIGURATION_VERSION),
            rs.getString("window_from"),
            rs.getString("window_to_exclusive"),
            rs.getString("outcome"),
            periods(rs.getString("periods_json")),
            rs.getString("checked_at"));

    private static final RowMapper<LastIsolationCheck> ISOLATION_MAPPER = (rs, rowNum) -> new LastIsolationCheck(
            rs.getInt(CONFIGURATION_VERSION),
            rs.getString("reference_period"),
            rs.getString("outcome"),
            nullableLong(rs, "registered_count"),
            nullableLong(rs, "other_municipality_count"),
            nullableInt(rs, "other_municipality_codes"),
            nullableLong(rs, "unidentified_count"),
            rs.getString("checked_at"));

    private final JdbcTemplate jdbc;

    public JdbcSourceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // The SQLite driver refuses getObject(column, Long.class) on NULL.
    private static Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    @Override
    public void upsert(SourceRecord source) {
        jdbc.update(
                """
                INSERT INTO sources (id, source_configuration_version, source_family,
                    pec_installation_role, source_location_kind, host, port, database_name,
                    db_user, secret_ref, municipality_ibge, pec_version, read_model, created_at)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                ON CONFLICT(id) DO UPDATE SET
                    source_configuration_version = excluded.source_configuration_version,
                    source_family = excluded.source_family,
                    pec_installation_role = excluded.pec_installation_role,
                    source_location_kind = excluded.source_location_kind,
                    host = excluded.host, port = excluded.port,
                    database_name = excluded.database_name, db_user = excluded.db_user,
                    secret_ref = excluded.secret_ref, municipality_ibge = excluded.municipality_ibge,
                    pec_version = excluded.pec_version, read_model = excluded.read_model
                """,
                source.id(),
                source.sourceConfigurationVersion(),
                source.sourceFamily(),
                source.pecInstallationRole(),
                source.sourceLocationKind(),
                source.host(),
                source.port(),
                source.databaseName(),
                source.dbUser(),
                source.secretRef(),
                source.municipalityIbge(),
                source.pecVersion(),
                source.readModel(),
                source.createdAt());
    }

    @Override
    public Optional<SourceRecord> findById(String id) {
        return jdbc.query("select * from sources where id = ?", MAPPER, id).stream()
                .findFirst();
    }

    @Override
    public List<SourceRecord> findAll() {
        return jdbc.query("select * from sources order by municipality_ibge, id", MAPPER);
    }

    @Override
    public void recordDiagnostic(
            String sourceId, int sourceConfigurationVersion, String outcome, String detail, String testedAt) {
        jdbc.update("""
                INSERT INTO source_diagnostics (source_id, source_configuration_version, outcome, detail, tested_at)
                SELECT id, source_configuration_version, ?, ?, ?
                FROM sources WHERE id = ? AND source_configuration_version = ?
                ON CONFLICT(source_id) DO UPDATE SET
                    source_configuration_version = excluded.source_configuration_version,
                    outcome = excluded.outcome, detail = excluded.detail, tested_at = excluded.tested_at
                """, outcome, detail, testedAt, sourceId, sourceConfigurationVersion);
    }

    @Override
    public Optional<LastDiagnostic> findLastDiagnostic(String sourceId) {
        return jdbc.query("select * from source_diagnostics where source_id = ?", DIAGNOSTIC_MAPPER, sourceId).stream()
                .findFirst();
    }

    @Override
    public Map<String, LastDiagnostic> findLastDiagnostics() {
        return jdbc
                .query(
                        "select * from source_diagnostics",
                        (rs, rowNum) -> Map.entry(rs.getString("source_id"), DIAGNOSTIC_MAPPER.mapRow(rs, rowNum)))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void recordIsolationCheck(String sourceId, LastIsolationCheck check) {
        jdbc.update(
                """
                INSERT INTO source_isolation_checks (source_id, source_configuration_version, reference_period,
                    outcome, registered_count, other_municipality_count, other_municipality_codes,
                    unidentified_count, checked_at)
                SELECT id, source_configuration_version, ?, ?, ?, ?, ?, ?, ?
                FROM sources WHERE id = ? AND source_configuration_version = ?
                ON CONFLICT(source_id) DO UPDATE SET
                    source_configuration_version = excluded.source_configuration_version,
                    reference_period = excluded.reference_period, outcome = excluded.outcome,
                    registered_count = excluded.registered_count,
                    other_municipality_count = excluded.other_municipality_count,
                    other_municipality_codes = excluded.other_municipality_codes,
                    unidentified_count = excluded.unidentified_count, checked_at = excluded.checked_at
                """,
                check.referencePeriod(),
                check.outcome(),
                check.registeredCount(),
                check.otherMunicipalityCount(),
                check.otherMunicipalityCodes(),
                check.unidentifiedCount(),
                check.checkedAt(),
                sourceId,
                check.sourceConfigurationVersion());
    }

    @Override
    public Map<String, LastIsolationCheck> findLastIsolationChecks() {
        return jdbc
                .query(
                        "select * from source_isolation_checks",
                        (rs, rowNum) -> Map.entry(rs.getString("source_id"), ISOLATION_MAPPER.mapRow(rs, rowNum)))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void recordCoverage(String sourceId, LastCoverage coverage) {
        jdbc.update(
                """
                INSERT INTO source_period_coverage (source_id, source_configuration_version, window_from,
                    window_to_exclusive, outcome, periods_json, checked_at)
                SELECT id, source_configuration_version, ?, ?, ?, ?, ?
                FROM sources WHERE id = ? AND source_configuration_version = ?
                ON CONFLICT(source_id) DO UPDATE SET
                    source_configuration_version = excluded.source_configuration_version,
                    window_from = excluded.window_from, window_to_exclusive = excluded.window_to_exclusive,
                    outcome = excluded.outcome, periods_json = excluded.periods_json,
                    checked_at = excluded.checked_at
                """,
                coverage.windowFrom(),
                coverage.windowToExclusive(),
                coverage.outcome(),
                CHECKED.equals(coverage.outcome()) ? JSON.writeValueAsString(coverage.periods()) : null,
                coverage.checkedAt(),
                sourceId,
                coverage.sourceConfigurationVersion());
    }

    @Override
    public Map<String, LastCoverage> findLastCoverages() {
        return jdbc
                .query(
                        "select * from source_period_coverage",
                        (rs, rowNum) -> Map.entry(rs.getString("source_id"), COVERAGE_MAPPER.mapRow(rs, rowNum)))
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private static List<LastCoverage.PeriodCount> periods(String json) {
        return json == null ? List.of() : JSON.readValue(json, PERIODS);
    }
}
