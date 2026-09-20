package br.gov.observatorioaps.resultstore;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Optional;

/**
 * Persists {@code sources} rows. {@code secret_ref} is a reference/state string only — the secret
 * value itself never passes through this class (§1.12.7).
 */
public final class SourceRepository {

    private static final RowMapper<SourceRecord> MAPPER = (rs, rowNum) -> new SourceRecord(
            rs.getString("id"),
            rs.getInt("source_configuration_version"),
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

    private final JdbcTemplate jdbc;

    public SourceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsert(SourceRecord source) {
        jdbc.update("""
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
                source.id(), source.sourceConfigurationVersion(), source.sourceFamily(),
                source.pecInstallationRole(), source.sourceLocationKind(), source.host(),
                source.port(), source.databaseName(), source.dbUser(), source.secretRef(),
                source.municipalityIbge(), source.pecVersion(), source.readModel(),
                source.createdAt());
    }

    public Optional<SourceRecord> findById(String id) {
        return jdbc.query("select * from sources where id = ?", MAPPER, id)
                .stream().findFirst();
    }
}
