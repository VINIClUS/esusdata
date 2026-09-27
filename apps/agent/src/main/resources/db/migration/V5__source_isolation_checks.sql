-- Last municipal isolation check of each source (ADR 0023, POST /sources/{id}/isolation-check,
-- issue #22). V1-V4 are immutable (§1.12.2) — this is additive.
--
-- One row per source, overwritten by every check, pinned to the configuration version it ran
-- against exactly like source_diagnostics (V4). `reference_period` is the competência counted
-- (yyyy-MM). The counts are aggregates per municipality code — never a record — and are null
-- unless the check completed (outcome CHECKED). SOURCE_BUSY is not stored.
CREATE TABLE source_isolation_checks (
    source_id                    TEXT PRIMARY KEY REFERENCES sources(id),
    source_configuration_version INTEGER NOT NULL,
    reference_period             TEXT NOT NULL,
    outcome                      TEXT NOT NULL CHECK (outcome IN (
                                     'DESTINATION_NOT_ALLOWED', 'SOURCE_AUTHENTICATION_FAILED',
                                     'SOURCE_PERMISSION_DENIED', 'CONNECTION_FAILED',
                                     'COMPATIBILITY_MISMATCH', 'SOURCE_BUDGET_EXCEEDED', 'CHECKED')),
    registered_count             INTEGER,
    other_municipality_count     INTEGER,
    other_municipality_codes     INTEGER,
    unidentified_count           INTEGER,
    checked_at                   TEXT NOT NULL, -- Instant, ISO-8601 UTC
    CHECK ((outcome = 'CHECKED') = (registered_count IS NOT NULL
                                    AND other_municipality_count IS NOT NULL
                                    AND other_municipality_codes IS NOT NULL
                                    AND unidentified_count IS NOT NULL))
);
