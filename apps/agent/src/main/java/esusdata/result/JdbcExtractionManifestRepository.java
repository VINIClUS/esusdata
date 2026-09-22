package esusdata.result;

import esusdata.result.model.ExtractionManifestRepository;
import esusdata.run.extract.ExtractionManifest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.nio.file.Path;
import java.util.Optional;
import esusdata.result.model.StoredManifest;
/**
 * Persists {@link ExtractionManifest} rows once a manifest has been finalized on disk. Never
 * writes a manifest whose data file has not already been verified — callers finalize/verify the
 * extract first (§1.9.5: "finalizar o extrato antes do commit de referência").
 */
public final class JdbcExtractionManifestRepository implements ExtractionManifestRepository {

    private static final RowMapper<StoredManifest> MAPPER = (rs, rowNum) -> new StoredManifest(
            new ExtractionManifest(
                    rs.getString("extraction_id"),
                    rs.getString("source_id"),
                    rs.getString("municipality_ibge"),
                    rs.getString("period_start"),
                    rs.getString("period_end_exclusive"),
                    rs.getString("started_at"),
                    rs.getString("finished_at"),
                    rs.getString("canonical_schema_version"),
                    rs.getString("completeness_status"),
                    rs.getString("consistency_level"),
                    rs.getString("source_zone_id"),
                    rs.getLong("row_count"),
                    rs.getLong("exclusion_count"),
                    rs.getString("checksum"),
                    rs.getString("query_checksum"),
                    rs.getString("adapter_version")),
            Path.of(rs.getString("file_path")));

    private final JdbcTemplate jdbc;

    public JdbcExtractionManifestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existsById(String extractionId) {
        Integer count = jdbc.queryForObject(
                "select count(*) from extraction_manifests where extraction_id = ?",
                Integer.class, extractionId);
        return count != null && count > 0;
    }

    public void save(ExtractionManifest manifest, Path filePath) {
        jdbc.update("""
                INSERT INTO extraction_manifests (extraction_id, source_id, municipality_ibge,
                    period_start, period_end_exclusive, started_at, finished_at,
                    canonical_schema_version, completeness_status, consistency_level,
                    source_zone_id, row_count, exclusion_count, checksum, query_checksum,
                    file_path, adapter_version)
                VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                manifest.extractionId(), manifest.sourceId(), manifest.municipalityIbge(),
                manifest.periodStart(), manifest.periodEndExclusive(), manifest.startedAt(),
                manifest.finishedAt(), manifest.canonicalSchemaVersion(),
                manifest.completenessStatus(), manifest.consistencyLevel(), manifest.sourceZoneId(),
                manifest.rowCount(), manifest.exclusionCount(), manifest.checksum(),
                manifest.queryChecksum(), filePath.toString(), manifest.adapterVersion());
    }

    public Optional<StoredManifest> findById(String extractionId) {
        return jdbc.query("select * from extraction_manifests where extraction_id = ?",
                MAPPER, extractionId).stream().findFirst();
    }
}
