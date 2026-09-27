-- Last diagnostic of each source (§1.10 POST /sources/{id}/test, issue #22). V1-V3 are immutable
-- (§1.12.2) — this is additive.
--
-- One row per source, overwritten by every test. `source_configuration_version` pins the result to
-- the configuration it was run against: re-registering a source bumps that version and the stored
-- row stops applying, so a stale CONNECTED is never shown for a changed host or user. SOURCE_BUSY
-- is not stored — it says the source was being acquired from, nothing about the source itself.
-- `detail` is the same connection-class message the API returns, never the secret (§1.12.7).
CREATE TABLE source_diagnostics (
    source_id                    TEXT PRIMARY KEY REFERENCES sources(id),
    source_configuration_version INTEGER NOT NULL,
    outcome                      TEXT NOT NULL CHECK (outcome IN (
                                     'DESTINATION_NOT_ALLOWED', 'SOURCE_AUTHENTICATION_FAILED',
                                     'SOURCE_PERMISSION_DENIED', 'CONNECTION_FAILED', 'CONNECTED')),
    detail                       TEXT,
    tested_at                    TEXT NOT NULL -- Instant, ISO-8601 UTC
);
