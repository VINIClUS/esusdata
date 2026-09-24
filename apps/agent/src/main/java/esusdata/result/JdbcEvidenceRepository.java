package esusdata.result;

import esusdata.result.model.EvidenceNotFoundException;
import esusdata.result.model.EvidencePage;
import esusdata.result.model.EvidenceRecord;
import esusdata.result.model.EvidenceRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/**
 * Deterministic, scope-checked pagination over evidence for one published result (§1.10.1:
 * "cursor opaco vinculado ao resultado publicado/filtros/ordenação e autorização a cada página").
 * The cursor here is a monotonic {@code seq} scoped to one immutable {@code staging_id} — it
 * grants no access by itself; every call still re-resolves and re-checks the municipality scope.
 */
public final class JdbcEvidenceRepository implements EvidenceRepository {

    private static final RowMapper<EvidenceRecord> MAPPER = (rs, rowNum) -> new EvidenceRecord(
            rs.getLong("seq"),
            rs.getString("source_entity_type"),
            rs.getString("source_record_id"),
            rs.getString("care_date"),
            rs.getString("modality"),
            rs.getString("cnes"),
            rs.getString("ine"),
            rs.getString("cbo"),
            rs.getString("decision"),
            rs.getString("criterion_version"));

    private final JdbcTemplate jdbc;

    public JdbcEvidenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public EvidencePage page(String resultId, String municipalityIbge, Long afterSeq, int limit) {
        return page(resultId, municipalityIbge, null, null, afterSeq, limit);
    }

    /**
     * §1.12 L427 CNES/INE narrowing: a non-null {@code cnes}/{@code ine} filters the underlying
     * rows in SQL, before pagination — filtering the page's Java list afterward would corrupt
     * {@code hasMore}/{@code nextCursor} (a page could come back short of {@code limit} while more
     * matching rows exist further down the {@code seq} order).
     */
    @Override
    public EvidencePage page(
            String resultId, String municipalityIbge, String cnes, String ine, Long afterSeq, int limit) {
        if (municipalityIbge == null || !municipalityIbge.matches("\\d{7}")) {
            throw new IllegalArgumentException("a 7-digit municipality scope is required to read evidence");
        }
        int effectiveLimit = Math.min(Math.max(limit, 1), MAX_PAGE_SIZE);

        String stagingId = jdbc
                .query(
                        "select staging_id from results where result_id = ? and municipality_ibge = ?",
                        (rs, i) -> rs.getString(1),
                        resultId,
                        municipalityIbge)
                .stream()
                .findFirst()
                .orElseThrow(() -> new EvidenceNotFoundException("result not found in scope: " + resultId));

        long cursor = afterSeq == null ? -1L : afterSeq;
        StringBuilder sql = new StringBuilder("select * from evidence where staging_id = ? and seq > ?");
        List<Object> params = new ArrayList<>(List.of(stagingId, cursor));
        if (cnes != null) {
            sql.append(" and cnes = ?");
            params.add(cnes);
        }
        if (ine != null) {
            sql.append(" and ine = ?");
            params.add(ine);
        }
        sql.append(" order by seq asc limit ?");
        params.add(effectiveLimit + 1);

        List<EvidenceRecord> rows = jdbc.query(sql.toString(), MAPPER, params.toArray());

        boolean hasMore = rows.size() > effectiveLimit;
        List<EvidenceRecord> page = hasMore ? rows.subList(0, effectiveLimit) : rows;
        Long nextCursor = hasMore ? page.get(page.size() - 1).seq() : null;
        return new EvidencePage(page, nextCursor);
    }
}
