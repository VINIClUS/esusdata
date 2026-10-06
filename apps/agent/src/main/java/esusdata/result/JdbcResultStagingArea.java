package esusdata.result;

import esusdata.indicator.model.ExactRatio;
import esusdata.indicator.model.IndicatorResult;
import esusdata.result.model.EvidenceEntry;
import esusdata.result.model.ResultStagingArea;
import esusdata.result.model.StagingRequest;
import java.math.BigInteger;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/**
 * Staging area for one computed result before the short publication transaction (§1.9.5).
 * Nothing written here is visible to a reader of published results — {@link PublicationService}
 * is the only path from {@code result_staging} to {@code results}.
 *
 * <p>ADR 0030 (V10): the result's value kind, exact value, practices or subgroups, per-team results
 * and consolidation eligibility are staged with it ({@link ResultJson}); evidence rows carry their
 * subject, practice, reason code and points.
 */
public final class JdbcResultStagingArea implements ResultStagingArea {

    private static final int EVIDENCE_BATCH_SIZE = 500;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcResultStagingArea(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Opens a new staging row in state {@code OPEN}. */
    @Override
    public String open(StagingRequest request) {
        IndicatorResult result = request.result();
        ExactRatio exact = ResultJson.exactValue(result);
        jdbc.update(
                """
                INSERT INTO result_staging (staging_id, job_id, execution_generation,
                    process_instance_id, created_at, state, indicator_pack, rule_version,
                    municipality_ibge, reference_period, status, value_text, numerator_text,
                    denominator_text, denominator_kind, classification, data_cutoff,
                    extraction_id, adapter_version, calculation_policy_version, limitations_json,
                    input_fingerprint, evidence_grain, value_kind, value_exact_numerator,
                    value_exact_denominator, components_json, team_results_json,
                    consolidation_eligible, gate_snapshot_json)
                VALUES (?,?,?,?,?, 'OPEN', ?,?,?,?, ?,?,?,?,?,?, ?,?,?,?,?, ?,?, ?,?,?,?,?,?,?)
                """,
                request.stagingId(),
                request.jobId(),
                request.executionGeneration(),
                request.processInstanceId(),
                request.createdAt().toString(),
                request.indicatorPack(),
                result.ruleVersion(),
                result.municipalityIbge(),
                result.referencePeriod(),
                result.status().name(),
                result.valueText(),
                text(result.numerator()),
                text(result.denominator()),
                result.denominatorKind(),
                result.classification() == null ? null : result.classification().name(),
                result.dataCutoff(),
                request.extractionId(),
                request.adapterVersion(),
                result.calculationPolicyVersion(),
                writeLimitations(result.limitations()),
                request.inputFingerprint(),
                request.evidenceGrain(),
                result.valueKind().name(),
                exact == null ? null : exact.numerator().toString(),
                exact == null ? null : exact.denominator().toString(),
                ResultJson.writeComponents(result.components()),
                ResultJson.writeTeams(request.teams()),
                result.consolidationEligible() ? 1 : 0,
                request.gateSnapshotJson());
        return request.stagingId();
    }

    /**
     * Writes evidence rows in bounded batches (§1.9.5: "evidências são gravadas em lotes de
     * staging") — assigns a deterministic {@code seq} in list order, starting at 0, so pagination
     * over the eventual published result is reproducible.
     */
    @Override
    public void writeEvidence(String stagingId, List<EvidenceEntry> entries) {
        for (int offset = 0; offset < entries.size(); offset += EVIDENCE_BATCH_SIZE) {
            List<EvidenceEntry> batch = entries.subList(offset, Math.min(offset + EVIDENCE_BATCH_SIZE, entries.size()));
            int firstSeq = offset;
            jdbc.batchUpdate("""
                    INSERT INTO evidence (staging_id, seq, subject_kind, subject_key, source_entity_type,
                        source_record_id, care_date, modality, cnes, ine, cbo, component, decision,
                        reason_code, points_text, criterion_version)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    EvidenceEntry entry = batch.get(i);
                    ps.setString(1, stagingId);
                    ps.setInt(2, firstSeq + i);
                    ps.setString(3, entry.subjectKind());
                    ps.setString(4, entry.subjectKey());
                    ps.setString(5, entry.sourceEntityType());
                    ps.setString(6, entry.sourceRecordId());
                    ps.setString(7, entry.careDate());
                    ps.setString(8, entry.modality());
                    ps.setString(9, entry.cnes());
                    ps.setString(10, entry.ine());
                    ps.setString(11, entry.cbo());
                    ps.setString(12, entry.component());
                    ps.setString(13, entry.decision());
                    ps.setString(14, entry.reasonCode());
                    ps.setString(15, entry.points());
                    ps.setString(16, entry.criterionVersion());
                }

                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            });
        }
    }

    /** Seals a staging row — the last step before it can be published. */
    @Override
    public void seal(String stagingId) {
        int updated = jdbc.update(
                "update result_staging set state = 'SEALED' where staging_id = ? and state = 'OPEN'", stagingId);
        if (updated != 1) {
            throw new IllegalStateException("cannot seal staging " + stagingId + ": not found or not OPEN");
        }
    }

    /**
     * Neutralizes a staging row and its evidence — used on cancellation and on recovery of an
     * abandoned job (§1.9.4: "estágio parcial neutralizado"). A {@code PUBLISHED} row is never
     * neutralized; the CAS below only matches {@code OPEN}/{@code SEALED}.
     */
    @Override
    public void neutralize(String stagingId) {
        jdbc.update("delete from evidence where staging_id = ?", stagingId);
        jdbc.update(
                "update result_staging set state = 'NEUTRALIZED' where staging_id = ? and state in ('OPEN','SEALED')",
                stagingId);
    }

    private String writeLimitations(List<String> limitations) {
        return mapper.writeValueAsString(limitations);
    }

    private static String text(BigInteger value) {
        return value == null ? null : value.toString();
    }
}
