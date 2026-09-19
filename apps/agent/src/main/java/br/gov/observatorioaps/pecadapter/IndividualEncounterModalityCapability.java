package br.gov.observatorioaps.pecadapter;

import br.gov.observatorioaps.sourceconnector.BudgetGuard;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
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

    private static final int FETCH_SIZE = 1000;

    private IndividualEncounterModalityCapability() {
    }

    /**
     * Streams matching encounters to {@code consumer}, checking the {@link BudgetGuard} once per
     * row so a runaway result set is interrupted rather than fully materialized. The caller owns
     * the {@link Connection} — this method never closes it, and never opens one itself, keeping
     * source-connector the single place that manages PEC connections (§1.5).
     */
    public static void stream(
            Connection connection,
            String municipalityIbge,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            BudgetGuard guard,
            Consumer<RawEncounterRecord> consumer
    ) throws SQLException {
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
}
