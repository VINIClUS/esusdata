package esusdata.result;

import esusdata.result.model.ResultRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;
import esusdata.result.model.PublishedResult;
/**
 * Reads published results. Every method requires an explicit municipality scope — §1.12.1:
 * "consultas sempre recebem escopo autorizado." There is no unscoped read path in this class.
 */
public final class JdbcResultRepository implements ResultRepository {

    private static final RowMapper<PublishedResult> MAPPER = (rs, rowNum) -> new PublishedResult(
            rs.getString("result_id"), rs.getString("job_id"), rs.getString("run_id"),
            rs.getString("source_id"), rs.getString("indicator_pack"), rs.getString("rule_version"),
            rs.getString("municipality_ibge"), rs.getString("reference_period"),
            rs.getString("status"), rs.getString("value_text"), rs.getString("numerator_text"),
            rs.getString("denominator_text"), rs.getString("denominator_kind"),
            rs.getString("classification"), rs.getString("data_cutoff"),
            rs.getString("extraction_id"), rs.getString("adapter_version"),
            rs.getString("calculation_policy_version"), rs.getString("limitations_json"),
            rs.getString("input_fingerprint"), rs.getString("result_nature"),
            rs.getString("validation_status"), rs.getString("completeness_status"),
            rs.getString("consistency_level"), rs.getString("reproducibility_level"),
            rs.getString("canonical_schema_version"), rs.getString("evidence_grain"),
            rs.getString("app_build"), rs.getString("published_at"));

    private final JdbcTemplate jdbc;

    public JdbcResultRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PublishedResult> findPublished(
            String municipalityIbge, String indicatorPack, String referencePeriod) {
        requireScope(municipalityIbge);
        return jdbc.query("""
                select * from results
                 where municipality_ibge = ? and indicator_pack = ? and reference_period = ?
                 order by published_at desc
                """, MAPPER, municipalityIbge, indicatorPack, referencePeriod);
    }

    public List<String> findPublishedPeriods(String municipalityIbge) {
        requireScope(municipalityIbge);
        return jdbc.queryForList("""
                select distinct reference_period from results
                 where municipality_ibge = ?
                 order by reference_period desc
                """, String.class, municipalityIbge);
    }

    /**
     * Looks up a result by id, scoped to a municipality. An object that exists but is out of
     * scope returns empty — identical to "not found" from the caller's perspective (§1.10.1:
     * "objeto inexistente e objeto fora do escopo têm a mesma resposta externa 404").
     */
    public Optional<PublishedResult> findByIdInScope(String resultId, String municipalityIbge) {
        requireScope(municipalityIbge);
        return jdbc.query("select * from results where result_id = ? and municipality_ibge = ?",
                MAPPER, resultId, municipalityIbge).stream().findFirst();
    }

    /** Lets {@code GET /runs/{id}} surface where a SUCCEEDED job's result landed. */
    public Optional<String> findResultIdByJobId(String jobId, String municipalityIbge) {
        requireScope(municipalityIbge);
        return jdbc.query("select result_id from results where job_id = ? and municipality_ibge = ?",
                (rs, rowNum) -> rs.getString("result_id"), jobId, municipalityIbge).stream().findFirst();
    }

    private void requireScope(String municipalityIbge) {
        if (municipalityIbge == null || !municipalityIbge.matches("\\d{7}")) {
            throw new IllegalArgumentException(
                    "a 7-digit municipality scope is required to read results");
        }
    }
}
