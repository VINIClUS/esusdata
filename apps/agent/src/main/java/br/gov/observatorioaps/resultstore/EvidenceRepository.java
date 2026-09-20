package br.gov.observatorioaps.resultstore;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;

/**
 * Deterministic, scope-checked pagination over evidence for one published result (§1.10.1:
 * "cursor opaco vinculado ao resultado publicado/filtros/ordenação e autorização a cada página").
 * The cursor here is a monotonic {@code seq} scoped to one immutable {@code staging_id} — it
 * grants no access by itself; every call still re-resolves and re-checks the municipality scope.
 */
public final class EvidenceRepository {

    public static final int DEFAULT_PAGE_SIZE = 100;
    public static final int MAX_PAGE_SIZE = 500;

    private static final RowMapper<EvidenceRecord> MAPPER = (rs, rowNum) -> new EvidenceRecord(
            rs.getLong("seq"), rs.getString("source_entity_type"), rs.getString("source_record_id"),
            rs.getString("care_date"), rs.getString("modality"), rs.getString("cnes"),
            rs.getString("ine"), rs.getString("cbo"), rs.getString("decision"),
            rs.getString("criterion_version"));

    private final JdbcTemplate jdbc;

    public EvidenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public EvidencePage page(String resultId, String municipalityIbge, Long afterSeq, int limit) {
        if (municipalityIbge == null || !municipalityIbge.matches("\\d{7}")) {
            throw new IllegalArgumentException(
                    "a 7-digit municipality scope is required to read evidence");
        }
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);

        String stagingId = jdbc.query(
                        "select staging_id from results where result_id = ? and municipality_ibge = ?",
                        (rs, i) -> rs.getString(1), resultId, municipalityIbge)
                .stream().findFirst()
                .orElseThrow(() -> new EvidenceNotFoundException(
                        "result not found in scope: " + resultId));

        long cursor = afterSeq == null ? -1L : afterSeq;
        List<EvidenceRecord> rows = jdbc.query("""
                select * from evidence where staging_id = ? and seq > ?
                 order by seq asc limit ?
                """, MAPPER, stagingId, cursor, effectiveLimit + 1);

        boolean hasMore = rows.size() > effectiveLimit;
        List<EvidenceRecord> page = hasMore ? rows.subList(0, effectiveLimit) : rows;
        Long nextCursor = hasMore ? page.get(page.size() - 1).seq() : null;
        return new EvidencePage(page, nextCursor);
    }
}
