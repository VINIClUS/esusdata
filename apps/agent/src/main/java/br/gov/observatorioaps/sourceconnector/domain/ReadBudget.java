package br.gov.observatorioaps.sourceconnector.domain;

import java.time.Duration;

/**
 * Read-load protection limits — Tech Spec §1.9.2. The numeric values in
 * {@link #initialEngineeringProposal()} are exactly the ones the spec itself labels "propostas de
 * configuração, não desempenho comprovado" (§1.9.2 closing note) — pending an approved load
 * profile (blocker P12). They are not invented here; they are transcribed, and this type exists
 * so the profile is one named, versioned thing rather than scattered constants.
 *
 * @param poolMaxSize                 max HikariCP pool size against the PEC — spec proposes 2,
 *                                     raisable to 4 only after benchmark + approval
 * @param connectionTimeout           budget for obtaining a physical connection
 * @param acquisitionTimeout          budget for acquiring a connection from the pool — kept
 *                                     distinct from connectionTimeout per §1.9.2's warning not to
 *                                     confuse "timeout do pool" with "timeout da consulta"
 * @param statementTimeoutMs          PostgreSQL {@code statement_timeout}
 * @param lockTimeoutMs               PostgreSQL {@code lock_timeout}
 * @param idleInTransactionTimeoutMs  PostgreSQL {@code idle_in_transaction_session_timeout}
 * @param maxRows                     row ceiling for a single acquisition; breach → SOURCE_BUDGET_EXCEEDED
 * @param maxDurationMs               wall-clock ceiling for a single acquisition
 * @param maxPayloadBytes             UTF-8 source-payload ceiling for a single acquisition
 * @param maxTempFileBytes            compressed temporary-extract ceiling for a single acquisition
 */
public record ReadBudget(
        int poolMaxSize,
        Duration connectionTimeout,
        Duration acquisitionTimeout,
        long statementTimeoutMs,
        long lockTimeoutMs,
        long idleInTransactionTimeoutMs,
        long maxRows,
        long maxDurationMs,
        long maxPayloadBytes,
        long maxTempFileBytes
) {
    public static final long DEFAULT_MAX_PAYLOAD_BYTES = 64L * 1024 * 1024;
    public static final long DEFAULT_MAX_TEMP_FILE_BYTES = 128L * 1024 * 1024;

    /** Backward-compatible constructor using the conservative byte ceilings. */
    public ReadBudget(
            int poolMaxSize,
            Duration connectionTimeout,
            Duration acquisitionTimeout,
            long statementTimeoutMs,
            long lockTimeoutMs,
            long idleInTransactionTimeoutMs,
            long maxRows,
            long maxDurationMs
    ) {
        this(poolMaxSize, connectionTimeout, acquisitionTimeout, statementTimeoutMs, lockTimeoutMs,
                idleInTransactionTimeoutMs, maxRows, maxDurationMs,
                DEFAULT_MAX_PAYLOAD_BYTES, DEFAULT_MAX_TEMP_FILE_BYTES);
    }

    public ReadBudget {
        if (poolMaxSize <= 0) throw new IllegalArgumentException("poolMaxSize must be positive");
        if (connectionTimeout == null || connectionTimeout.isNegative() || connectionTimeout.isZero()) {
            throw new IllegalArgumentException("connectionTimeout must be positive");
        }
        if (acquisitionTimeout == null || acquisitionTimeout.toMillis() < 250) {
            throw new IllegalArgumentException("acquisitionTimeout must be at least 250ms");
        }
        if (statementTimeoutMs <= 0 || lockTimeoutMs <= 0 || idleInTransactionTimeoutMs <= 0) {
            throw new IllegalArgumentException("PostgreSQL timeout budgets must be positive");
        }
        if (maxRows <= 0 || maxDurationMs <= 0 || maxPayloadBytes <= 0 || maxTempFileBytes <= 0) {
            throw new IllegalArgumentException(
                    "acquisition row, duration, payload, and temp-file ceilings must be positive");
        }
    }

    /**
     * The spec's own initial engineering proposal (§1.9.2), not a load-tested profile. A real
     * deployment must record which profile id was in effect on every run (plan: a versioned
     * {@code load-profile.yaml} under {@code deployment/}) — this factory is the seed for that,
     * not a substitute for the approval P12 requires.
     */
    public static ReadBudget initialEngineeringProposal() {
        return new ReadBudget(
                2,
                Duration.ofSeconds(10),
                Duration.ofSeconds(10),
                30_000,
                10_000,
                30_000,
                200_000,
                60_000,
                DEFAULT_MAX_PAYLOAD_BYTES,
                DEFAULT_MAX_TEMP_FILE_BYTES
        );
    }
}
