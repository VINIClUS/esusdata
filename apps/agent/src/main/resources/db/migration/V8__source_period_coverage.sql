-- Last coverage check of each source (ADR 0027, POST /sources/{id}/coverage-check): which
-- competências of a window hold atendimentos of the source's municipality. V1-V7 are immutable
-- (§1.12.2) — this is additive.
--
-- One row per source, overwritten by every check, pinned to the configuration version it ran
-- against exactly like source_diagnostics (V4) and source_isolation_checks (V5). `periods_json`
-- is a JSON array of {referencePeriod, count} aggregates for the registered municipality only —
-- never a record — and is null unless the check completed (outcome CHECKED). SOURCE_BUSY is not
-- stored.
CREATE TABLE source_period_coverage (
    source_id                    TEXT PRIMARY KEY REFERENCES sources(id),
    source_configuration_version INTEGER NOT NULL,
    window_from                  TEXT NOT NULL, -- yyyy-MM, inclusive
    window_to_exclusive          TEXT NOT NULL, -- yyyy-MM, exclusive
    outcome                      TEXT NOT NULL CHECK (outcome IN (
                                     'DESTINATION_NOT_ALLOWED', 'SOURCE_AUTHENTICATION_FAILED',
                                     'SOURCE_PERMISSION_DENIED', 'CONNECTION_FAILED',
                                     'COMPATIBILITY_MISMATCH', 'SOURCE_BUDGET_EXCEEDED', 'CHECKED')),
    periods_json                 TEXT,
    checked_at                   TEXT NOT NULL, -- Instant, ISO-8601 UTC
    CHECK ((outcome = 'CHECKED') = (periods_json IS NOT NULL))
);
