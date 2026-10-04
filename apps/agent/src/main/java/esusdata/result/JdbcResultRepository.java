package esusdata.result;

import esusdata.indicator.IndicatorRuleRegistry;
import esusdata.result.model.PublishedResult;
import esusdata.result.model.ResultRepository;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;

/**
 * Reads published results. Every method requires an explicit municipality scope — §1.12.1:
 * "consultas sempre recebem escopo autorizado." There is no unscoped read path in this class.
 */
public final class JdbcResultRepository implements ResultRepository {

    private static final RowMapper<PublishedResult> MAPPER = (rs, rowNum) -> new PublishedResult(
            rs.getString("result_id"),
            rs.getString("job_id"),
            rs.getString("run_id"),
            rs.getString("source_id"),
            rs.getString("indicator_pack"),
            rs.getString("rule_version"),
            rs.getString("municipality_ibge"),
            rs.getString("reference_period"),
            rs.getString("status"),
            rs.getString("value_text"),
            rs.getString("numerator_text"),
            rs.getString("denominator_text"),
            rs.getString("denominator_kind"),
            rs.getString("classification"),
            rs.getString("data_cutoff"),
            rs.getString("extraction_id"),
            rs.getString("adapter_version"),
            rs.getString("calculation_policy_version"),
            rs.getString("limitations_json"),
            rs.getString("input_fingerprint"),
            rs.getString("result_nature"),
            rs.getString("validation_status"),
            rs.getString("completeness_status"),
            rs.getString("consistency_level"),
            rs.getString("reproducibility_level"),
            rs.getString("canonical_schema_version"),
            rs.getString("evidence_grain"),
            rs.getString("app_build"),
            rs.getString("published_at"),
            rs.getString("value_kind"),
            rs.getString("value_exact_numerator"),
            rs.getString("value_exact_denominator"),
            rs.getString("components_json"),
            rs.getString("team_results_json"),
            rs.getInt("consolidation_eligible") == 1);

    /**
     * Newest first, in time order. {@code published_at} is {@code Instant.toString()}: UTC, ending in
     * {@code Z}, with a fraction of 0 to 9 digits. The text alone does not sort in time order
     * ({@code 12:00:00Z} sorts after {@code 12:00:00.5Z}) and {@code julianday} stops at the
     * millisecond, so this compares the whole seconds, then the fraction padded to nine digits.
     * {@code result_id} breaks a real tie, so the same query always yields the same rows.
     */
    private static final String NEWEST_PUBLISHED_FIRST = """
            substr(published_at, 1, 19) desc,
            substr(case when instr(published_at, '.') > 0
                        then substr(published_at, instr(published_at, '.') + 1,
                                    length(published_at) - instr(published_at, '.') - 1)
                        else '' end || '000000000', 1, 9) desc,
            result_id desc""";

    private final JdbcTemplate jdbc;

    public JdbcResultRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<PublishedResult> findPublished(String municipalityIbge, String indicatorPack, String referencePeriod) {
        requireScope(municipalityIbge);
        return jdbc.query("""
                select * from results
                 where municipality_ibge = ? and indicator_pack = ? and reference_period = ?
                 order by
                """ + NEWEST_PUBLISHED_FIRST, MAPPER, municipalityIbge, indicatorPack, referencePeriod);
    }

    @Override
    public List<PublishedResult> findLatestPublishedInRange(
            String municipalityIbge, String indicatorPack, String fromPeriod, String toPeriod) {
        requireScope(municipalityIbge);
        return jdbc.query(
                """
                select * from (
                    select r.*, row_number() over (
                               partition by indicator_pack, reference_period
                               order by
                """ + NEWEST_PUBLISHED_FIRST + """
                               ) as newest
                      from results r
                     where municipality_ibge = ?
                       and (? is null or indicator_pack = ?)
                       and reference_period between ? and ?)
                 where newest = 1
                 order by indicator_pack, reference_period
                """,
                MAPPER,
                municipalityIbge,
                indicatorPack,
                indicatorPack,
                fromPeriod,
                toPeriod);
    }

    @Override
    public List<String> findPublishedPeriods(String municipalityIbge) {
        requireScope(municipalityIbge);
        return jdbc.queryForList("""
                select distinct reference_period from results
                 where municipality_ibge = ?
                 order by reference_period desc
                """, String.class, municipalityIbge);
    }

    @Override
    public Map<String, Set<String>> findPublishedPeriodsByPack(String municipalityIbge) {
        requireScope(municipalityIbge);
        Map<String, Set<String>> byPack = new TreeMap<>();
        jdbc.query(
                "select distinct indicator_pack, rule_version, reference_period from results where municipality_ibge = ?",
                (RowCallbackHandler) rs -> {
                    String pack = rs.getString("indicator_pack");
                    if (isCurrentRule(pack, rs.getString("rule_version"))) {
                        byPack.computeIfAbsent(pack, p -> new TreeSet<>()).add(rs.getString("reference_period"));
                    }
                },
                municipalityIbge);
        Map<String, Set<String>> readOnly = new TreeMap<>();
        byPack.forEach((pack, periods) -> readOnly.put(pack, Collections.unmodifiableSet(periods)));
        return Collections.unmodifiableMap(readOnly);
    }

    private static boolean isCurrentRule(String pack, String ruleVersion) {
        return IndicatorRuleRegistry.find(pack)
                .map(rule -> rule.descriptor().ruleVersion().equals(ruleVersion))
                .orElse(false);
    }

    /**
     * Looks up a result by id, scoped to a municipality. An object that exists but is out of
     * scope returns empty — identical to "not found" from the caller's perspective (§1.10.1:
     * "objeto inexistente e objeto fora do escopo têm a mesma resposta externa 404").
     */
    @Override
    public Optional<PublishedResult> findByIdInScope(String resultId, String municipalityIbge) {
        requireScope(municipalityIbge);
        return jdbc
                .query(
                        "select * from results where result_id = ? and municipality_ibge = ?",
                        MAPPER,
                        resultId,
                        municipalityIbge)
                .stream()
                .findFirst();
    }

    /** Lets {@code GET /runs/{id}} surface where a SUCCEEDED job's result landed. */
    @Override
    public Optional<String> findResultIdByJobId(String jobId, String municipalityIbge) {
        requireScope(municipalityIbge);
        return jdbc
                .query(
                        "select result_id from results where job_id = ? and municipality_ibge = ?",
                        (rs, rowNum) -> rs.getString("result_id"),
                        jobId,
                        municipalityIbge)
                .stream()
                .findFirst();
    }

    private static void requireScope(String municipalityIbge) {
        if (municipalityIbge == null || !municipalityIbge.matches("\\d{7}")) {
            throw new IllegalArgumentException("a 7-digit municipality scope is required to read results");
        }
    }
}
