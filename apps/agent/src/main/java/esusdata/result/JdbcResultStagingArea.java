package esusdata.result;

import esusdata.result.model.ResultStagingArea;
import esusdata.indicator.model.IndicatorResult;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import esusdata.result.model.EvidenceEntry;
import esusdata.result.model.StagingRequest;
/**
 * Staging area for one computed result before the short publication transaction (§1.9.5).
 * Nothing written here is visible to a reader of published results — {@link PublicationService}
 * is the only path from {@code result_staging} to {@code results}.
 */
public final class JdbcResultStagingArea implements ResultStagingArea {

    private static final int EVIDENCE_BATCH_SIZE = 500;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public JdbcResultStagingArea(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Opens a new staging row in state {@code OPEN}. */
    public String open(StagingRequest request) {
        IndicatorResult result = request.result();
        jdbc.update("""
                INSERT INTO result_staging (staging_id, job_id, execution_generation,
                    process_instance_id, created_at, state, indicator_pack, rule_version,
                    municipality_ibge, reference_period, status, value_text, numerator_text,
                    denominator_text, denominator_kind, classification, data_cutoff,
                    extraction_id, adapter_version, calculation_policy_version, limitations_json,
                    input_fingerprint, evidence_grain)
                VALUES (?,?,?,?,?, 'OPEN', ?,?,?,?, ?,?,?,?,?,?, ?,?,?,?,?, ?,?)
                """,
                request.stagingId(), request.jobId(), request.executionGeneration(),
                request.processInstanceId(), request.createdAt().toString(),
                request.indicatorPack(), result.ruleVersion(), result.municipalityIbge(),
                result.referencePeriod(), result.status().name(), result.valueText(),
                result.numerator().toString(), result.denominator().toString(),
                result.denominatorKind(),
                result.classification() == null ? null : result.classification().name(),
                result.dataCutoff(), request.extractionId(), request.adapterVersion(),
                result.calculationPolicyVersion(), writeLimitations(result.limitations()),
                request.inputFingerprint(), request.evidenceGrain());
        return request.stagingId();
    }

    /**
     * Writes evidence rows in bounded batches (§1.9.5: "evidências são gravadas em lotes de
     * staging") — assigns a deterministic {@code seq} in list order, starting at 0, so pagination
     * over the eventual published result is reproducible.
     */
    public void writeEvidence(String stagingId, List<EvidenceEntry> entries) {
        for (int offset = 0; offset < entries.size(); offset += EVIDENCE_BATCH_SIZE) {
            List<EvidenceEntry> batch = entries.subList(
                    offset, Math.min(offset + EVIDENCE_BATCH_SIZE, entries.size()));
            int firstSeq = offset;
            jdbc.batchUpdate("""
                    INSERT INTO evidence (staging_id, seq, source_entity_type, source_record_id,
                        care_date, modality, cnes, ine, cbo, decision, criterion_version)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?)
                    """, new BatchPreparedStatementSetter() {
                @Override
                public void setValues(PreparedStatement ps, int i) throws SQLException {
                    EvidenceEntry entry = batch.get(i);
                    ps.setString(1, stagingId);
                    ps.setInt(2, firstSeq + i);
                    ps.setString(3, entry.sourceEntityType());
                    ps.setString(4, entry.sourceRecordId());
                    ps.setString(5, entry.careDate());
                    ps.setString(6, entry.modality());
                    ps.setString(7, entry.cnes());
                    ps.setString(8, entry.ine());
                    ps.setString(9, entry.cbo());
                    ps.setString(10, entry.decision());
                    ps.setString(11, entry.criterionVersion());
                }

                @Override
                public int getBatchSize() {
                    return batch.size();
                }
            });
        }
    }

    /** Seals a staging row — the last step before it can be published. */
    public void seal(String stagingId) {
        int updated = jdbc.update(
                "update result_staging set state = 'SEALED' where staging_id = ? and state = 'OPEN'",
                stagingId);
        if (updated != 1) {
            throw new IllegalStateException(
                    "cannot seal staging " + stagingId + ": not found or not OPEN");
        }
    }

    /**
     * Neutralizes a staging row and its evidence — used on cancellation and on recovery of an
     * abandoned job (§1.9.4: "estágio parcial neutralizado"). A {@code PUBLISHED} row is never
     * neutralized; the CAS below only matches {@code OPEN}/{@code SEALED}.
     */
    public void neutralize(String stagingId) {
        jdbc.update("delete from evidence where staging_id = ?", stagingId);
        jdbc.update(
                "update result_staging set state = 'NEUTRALIZED' where staging_id = ? and state in ('OPEN','SEALED')",
                stagingId);
    }

    private String writeLimitations(List<String> limitations) {
        return mapper.writeValueAsString(limitations);
    }
}
