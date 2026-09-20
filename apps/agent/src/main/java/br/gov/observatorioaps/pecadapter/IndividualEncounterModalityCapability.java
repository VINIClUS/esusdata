package br.gov.observatorioaps.pecadapter;

import br.gov.observatorioaps.sourceconnector.BudgetGuard;
import br.gov.observatorioaps.sourceconnector.PecConnectionProperties;
import br.gov.observatorioaps.sourceconnector.PecSourceIdentity;
import br.gov.observatorioaps.sourceconnector.PecSourceConnection;
import br.gov.observatorioaps.sourceconnector.SourceBudgetExceededException;
import java.io.IOException;
import java.io.Reader;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
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

    public static final String CAPABILITY = "individual_encounter_modality";
    public static final String ADAPTER_VERSION = "0.1.0";

    /** Frozen query text — its SHA-256 is recorded as {@code query_checksum} in the adapter matrix. */
    public static final String QUERY = """
            SELECT f.co_seq_fat_atd_ind, f.co_dim_tipo_atendimento, t.dt_registro,
                   u.nu_cnes, e.nu_ine, c.nu_cbo, f.nu_uuid_ficha, f.nu_atendimento
              FROM public.tb_fat_atendimento_individual f
              JOIN public.tb_dim_tempo      t ON t.co_seq_dim_tempo       = f.co_dim_tempo
              JOIN public.tb_dim_municipio  m ON m.co_seq_dim_municipio   = f.co_dim_municipio
              LEFT JOIN public.tb_dim_unidade_saude u ON u.co_seq_dim_unidade_saude = f.co_dim_unidade_saude_1
              LEFT JOIN public.tb_dim_equipe        e ON e.co_seq_dim_equipe        = f.co_dim_equipe_1
              LEFT JOIN public.tb_dim_cbo           c ON c.co_seq_dim_cbo           = f.co_dim_cbo_1
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
     * the {@link PecSourceConnection} — this method never closes it, and never opens one itself,
     * keeping source-connector the single place that manages PEC connections (§1.5).
     *
     * <p>Validates adapter compatibility against the frozen matrix entry before executing any query
     * — an unsupported source version or schema fingerprint blocks acquisition (ENG-43).
     */
    public static void stream(
            PecSourceConnection sourceConnection,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            BudgetGuard guard,
            Consumer<RawEncounterRecord> consumer
    ) throws SQLException {
        stream(sourceConnection, periodStart, periodEndExclusive, guard, consumer,
                new JdbcCompatibilityCatalog());
    }

    /**
     * Streams a capability after probing the connected PostgreSQL version and every object
     * fingerprint named by the exact matrix entry. The catalog is injectable only so synthetic
     * PostgreSQL fixtures can provide the same probes without pretending they are the production
     * PEC; validation itself is never bypassed.
     */
    public static void stream(
            PecSourceConnection sourceConnection,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            BudgetGuard guard,
            Consumer<RawEncounterRecord> consumer,
            CompatibilityCatalog catalog
    ) throws SQLException {
        if (sourceConnection == null) {
            throw new IllegalArgumentException("A source-bound PEC connection is required");
        }
        Connection connection = sourceConnection.jdbcConnection();
        PecConnectionProperties sourceProperties = sourceConnection.properties();
        PecSourceIdentity sourceIdentity = sourceConnection.sourceIdentity();
        String municipalityIbge = requireAuthorizedMunicipality(sourceProperties);
        beginReadOnlyRepeatableReadTransaction(connection);
        try {
            validateAdapterCompatibility(connection, sourceIdentity, catalog,
                    PecCompatibilityMatrix.fromClasspathResource(), guard);
            guard.checkDuration();

            try (PreparedStatement ps = connection.prepareStatement(
                    QUERY, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY)) {
                ps.setFetchSize(FETCH_SIZE);
                ps.setString(1, municipalityIbge);
                ps.setDate(2, Date.valueOf(periodStart));
                ps.setDate(3, Date.valueOf(periodEndExclusive));

                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        guard.onRow();
                        LocalDate careDate = rs.getDate(3).toLocalDate();
                        guard.onPayloadBytes(fixedPayloadBytes(careDate));
                        String cnes = readBoundedString(rs, 4, guard);
                        String ine = readBoundedString(rs, 5, guard);
                        String cbo = readBoundedString(rs, 6, guard);
                        String uuidFicha = readBoundedString(rs, 7, guard);
                        RawEncounterRecord record = new RawEncounterRecord(
                                rs.getLong(1),
                                rs.getInt(2),
                                careDate,
                                cnes,
                                ine,
                                cbo,
                                uuidFicha,
                                rs.getInt(8)
                        );
                        consumer.accept(record);
                    }
                    guard.checkDuration();
                }
            }
        } catch (SQLException failure) {
            if (isPostgresBudgetCancellation(failure)) {
                throw new SourceBudgetExceededException(
                        SourceBudgetExceededException.CODE + ": PostgreSQL read budget expired: "
                                + failure.getMessage(), failure);
            }
            throw failure;
        }
    }

    private static boolean isPostgresBudgetCancellation(SQLException failure) {
        for (SQLException current = failure; current != null; current = current.getNextException()) {
            if (hasSocketTimeoutCause(current)) {
                return true;
            }
            String state = current.getSQLState();
            String message = current.getMessage() == null
                    ? "" : current.getMessage().toLowerCase(Locale.ROOT);
            if ("57014".equals(state) && message.contains("statement timeout")) {
                return true;
            }
            if ("55P03".equals(state) && message.contains("lock timeout")) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasSocketTimeoutCause(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static long fixedPayloadBytes(LocalDate careDate) {
        return Long.BYTES + Integer.BYTES + Integer.BYTES + Integer.BYTES
                + utf8Length(careDate.toString());
    }

    private static String readBoundedString(
            ResultSet resultSet, int column, BudgetGuard guard) throws SQLException {
        try (Reader reader = resultSet.getCharacterStream(column)) {
            if (reader == null) {
                return null;
            }
            StringBuilder value = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                guard.onPayloadBytes(utf8Length(buffer, 0, read));
                value.append(buffer, 0, read);
            }
            return value.toString();
        } catch (IOException e) {
            throw new SQLException("Could not stream source text column " + column, e);
        }
    }

    private static long utf8Length(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static long utf8Length(char[] value, int offset, int length) {
        long bytes = 0;
        int end = offset + length;
        for (int i = offset; i < end; i++) {
            char c = value[i];
            if (c <= 0x7F) {
                bytes++;
            } else if (c <= 0x7FF) {
                bytes += 2;
            } else if (Character.isHighSurrogate(c) && i + 1 < end
                    && Character.isLowSurrogate(value[i + 1])) {
                bytes += 4;
                i++;
            } else {
                bytes += 3;
            }
        }
        return bytes;
    }

    private static void beginReadOnlyRepeatableReadTransaction(Connection connection) throws SQLException {
        if (connection == null) {
            throw new SQLException("A connected PostgreSQL session is required");
        }
        if (connection.getAutoCommit()) {
            connection.setAutoCommit(false);
        }
        if (!connection.isReadOnly()) {
            connection.setReadOnly(true);
        }
        if (connection.getTransactionIsolation() != Connection.TRANSACTION_REPEATABLE_READ) {
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
        }
    }

    private static String requireAuthorizedMunicipality(PecConnectionProperties sourceProperties) {
        if (sourceProperties == null) {
            throw new IllegalArgumentException(
                    "PecConnectionProperties is required to authorize the acquisition municipality");
        }
        return sourceProperties.municipalityIbge();
    }

    public static void validateAdapterCompatibility(
            Connection connection,
            PecSourceIdentity sourceIdentity,
            CompatibilityCatalog catalog
    ) throws SQLException {
        validateAdapterCompatibility(connection, sourceIdentity, catalog,
                PecCompatibilityMatrix.fromClasspathResource());
    }

    public static void validateAdapterCompatibility(
            Connection connection,
            PecSourceIdentity sourceIdentity,
            CompatibilityCatalog catalog,
            PecCompatibilityMatrix matrix
    ) throws SQLException {
        validateAdapterCompatibility(connection, sourceIdentity, catalog, matrix, null);
    }

    static void validateAdapterCompatibility(
            Connection connection,
            PecSourceIdentity sourceIdentity,
            CompatibilityCatalog catalog,
            PecCompatibilityMatrix matrix,
            BudgetGuard guard
    ) throws SQLException {
        if (sourceIdentity == null || !sourceIdentity.isComplete()) {
            throw new IllegalStateException(
                    "PecSourceIdentity is required before acquiring a PEC capability");
        }
        if (catalog == null) {
            throw new IllegalStateException("CompatibilityCatalog is required before acquiring a PEC capability");
        }
        if (matrix == null) {
            throw new IllegalStateException("PecCompatibilityMatrix is required before acquiring a PEC capability");
        }

        String postgresVersion = catalog.postgresVersion(connection);
        if (guard != null) {
            guard.checkDuration();
        }
        PecCompatibilityMatrix.Entry entry = matrix.findExact(
                CAPABILITY, ADAPTER_VERSION, sourceIdentity, postgresVersion);

        if (!QUERY_CHECKSUM.equals(entry.queryChecksum())) {
            throw new IllegalStateException(
                    "Query checksum mismatch: matrix has " + entry.queryChecksum()
                            + " but live query has " + QUERY_CHECKSUM
                            + " — adapter query was changed without updating the compatibility matrix (ENG-43).");
        }

        for (Map.Entry<String, String> expected : entry.objectFingerprints().entrySet()) {
            String object = expected.getKey();
            String actual = catalog.fingerprint(connection, object, entry.objectColumns().get(object));
            if (guard != null) {
                guard.checkDuration();
            }
            if (!expected.getValue().equals(actual)) {
                throw new IllegalStateException(
                        "Compatibility fingerprint mismatch for " + object
                                + ": expected " + expected.getValue() + " but connected source returned " + actual);
            }
        }
    }
}
