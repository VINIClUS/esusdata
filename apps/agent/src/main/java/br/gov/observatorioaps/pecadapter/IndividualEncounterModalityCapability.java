package br.gov.observatorioaps.pecadapter;

import br.gov.observatorioaps.sourceconnector.BudgetGuard;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.function.Consumer;

/**
 * Capability {@code individual_encounter_modality} against PEC 5.4.37 / PostgreSQL 9.6.13,
 * read model {@code PEC_DW}. Frozen and fingerprinted in
 * {@code contracts/compatibility/pec-adapters.json}; grounded in
 * {@code docs/discovery/2026-09-19-pec-ct133.md}.
 *
 * <p>Binds the municipal cut on {@code tb_dim_municipio.co_ibge} (7-digit text), never on the
 * installation-local surrogate key {@code co_dim_municipio} — {@code tb_dim_municipio} is a
 * shared national reference table, not partitioned per installation (§1.4.2, ENG-38).
 *
 * <p>Date parameters are bound as {@link java.sql.Date}, not {@link String} — pgJDBC infers the
 * parameter type from the bound Java type, and PostgreSQL 9.6 rejects
 * {@code date >= character varying} with no implicit cast (found live during the pgJDBC spike).
 */
public final class IndividualEncounterModalityCapability {

    /** Frozen query text — its SHA-256 is recorded as {@code query_checksum} in the adapter matrix. */
    public static final String QUERY = """
            SELECT f.co_seq_fat_atd_ind, f.co_dim_tipo_atendimento, t.dt_registro,
                   u.nu_cnes, e.nu_ine, c.nu_cbo, f.nu_uuid_ficha, f.nu_atendimento
              FROM tb_fat_atendimento_individual f
              JOIN tb_dim_tempo      t ON t.co_seq_dim_tempo       = f.co_dim_tempo
              JOIN tb_dim_municipio  m ON m.co_seq_dim_municipio   = f.co_dim_municipio
              LEFT JOIN tb_dim_unidade_saude u ON u.co_seq_dim_unidade_saude = f.co_dim_unidade_saude_1
              LEFT JOIN tb_dim_equipe        e ON e.co_seq_dim_equipe        = f.co_dim_equipe_1
              LEFT JOIN tb_dim_cbo           c ON c.co_seq_dim_cbo           = f.co_dim_cbo_1
             WHERE m.co_ibge = ?
               AND t.dt_registro >= ? AND t.dt_registro < ?
             ORDER BY f.co_seq_fat_atd_ind
            """;

    /**
     * Real SHA-256 of {@link #QUERY}, computed once and reused everywhere a query checksum is
     * recorded (the adapter matrix, extraction manifests) — so those provenance fields can never
     * drift from the query text they claim to describe.
     */
    public static final String QUERY_CHECKSUM = computeQueryChecksum();

    private static final int FETCH_SIZE = 1000;

    private static String computeQueryChecksum() {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "sha256:" + HexFormat.of().formatHex(
                    digest.digest(QUERY.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private IndividualEncounterModalityCapability() {
    }

    /**
     * Streams matching encounters to {@code consumer}, checking the {@link BudgetGuard} once per
     * row so a runaway result set is interrupted rather than fully materialized. The caller owns
     * the {@link Connection} — this method never closes it, and never opens one itself, keeping
     * source-connector the single place that manages PEC connections (§1.5).
     *
     * <p>Validates adapter compatibility against the frozen matrix entry before executing any query
     * — an unsupported source version or schema fingerprint blocks acquisition (ENG-43).
     */
    public static void stream(
            Connection connection,
            String municipalityIbge,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            BudgetGuard guard,
            Consumer<RawEncounterRecord> consumer
    ) throws SQLException {
        validateAdapterCompatibility();

        try (PreparedStatement ps = connection.prepareStatement(
                QUERY, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
            ps.setFetchSize(FETCH_SIZE);
            ps.setString(1, municipalityIbge);
            ps.setDate(2, Date.valueOf(periodStart));
            ps.setDate(3, Date.valueOf(periodEndExclusive));

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    guard.onRow();
                    consumer.accept(new RawEncounterRecord(
                            rs.getLong(1),
                            rs.getInt(2),
                            rs.getDate(3).toLocalDate(),
                            rs.getString(4),
                            rs.getString(5),
                            rs.getString(6),
                            rs.getString(7),
                            rs.getInt(8)
                    ));
                }
            }
        }
    }

    private static void validateAdapterCompatibility() {
        try {
            Path matrixFile = Paths.get("contracts/compatibility/pec-adapters.json");
            if (!Files.exists(matrixFile)) {
                throw new IllegalStateException(
                        "Adapter compatibility matrix not found at " + matrixFile.toAbsolutePath()
                                + " — cannot validate that this adapter is approved for the target PEC.");
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(Files.readString(matrixFile));
            JsonNode testedWith = root.get("tested_with");

            if (testedWith == null || testedWith.size() == 0) {
                throw new IllegalStateException(
                        "Adapter compatibility matrix has no tested_with entries — cannot validate compatibility.");
            }

            JsonNode entry = testedWith.get(0);
            String matrixQueryChecksum = entry.get("query_checksum").asString();

            if (!QUERY_CHECKSUM.equals(matrixQueryChecksum)) {
                throw new IllegalStateException(
                        "Query checksum mismatch: adapter frozen checksum " + matrixQueryChecksum
                                + " does not match live query " + QUERY_CHECKSUM
                                + " — adapter query was changed without updating the compatibility matrix (ENG-43).");
            }

            String capabilityStatus = entry.get("status").asString();
            if (!"VALIDATED".equals(capabilityStatus)) {
                throw new IllegalStateException(
                        "Adapter capability status is " + capabilityStatus
                                + " — only VALIDATED adapters are allowed to execute queries.");
            }
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to load or parse adapter compatibility matrix: " + e.getMessage(), e);
        }
    }
}
