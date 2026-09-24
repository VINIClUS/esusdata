use serde::Deserialize;

/// The `{"type":"acquire",...}` message — field names and shape are pinned by
/// `SubprocessAcquisitionAdapter.writeAcquireEnvelope`, not by plan prose. Rust field names are
/// already the wire's `snake_case`, so no `#[serde(rename)]` is needed anywhere but `type`.
#[derive(Deserialize)]
pub struct AcquireEnvelope {
    #[serde(rename = "type")]
    #[expect(
        dead_code,
        reason = "part of the acquire wire contract, not read by this process"
    )]
    pub message_type: String,
    pub source_id: String,
    pub host: String,
    pub port: u16,
    pub database: String,
    pub user: String,
    pub password: String,
    pub municipality_ibge: String,
    pub pec_version: String,
    pub read_model: String,
    pub installation_role: String,
    #[expect(
        dead_code,
        reason = "part of the acquire wire contract, not read by this process"
    )]
    pub extraction_id: String,
    pub period_start: String,
    pub period_end_exclusive: String,
    #[expect(
        dead_code,
        reason = "part of the acquire wire contract, not read by this process"
    )]
    pub source_zone_id: String,
    /// Where this process must create the extract's data file (fatia 3 / ADR 0011) — an
    /// absolute path Java already reserved via its own lock + reconcile before this process
    /// was even spawned. Never a directory: the full `<extractionId>.jsonl.gz.tmp` filename.
    pub extract_temp_path: String,
    #[expect(
        dead_code,
        reason = "part of the acquire wire contract, not read by this process"
    )]
    pub query_checksum: String,
    pub adapter_version: String,
    pub budget: Budget,
}

#[derive(Deserialize)]
pub struct Budget {
    pub connect_timeout_ms: i64,
    #[expect(
        dead_code,
        reason = "part of the acquire wire contract, not read by this process"
    )]
    pub acquisition_timeout_ms: i64,
    pub statement_timeout_ms: i64,
    pub lock_timeout_ms: i64,
    pub idle_in_transaction_timeout_ms: i64,
    pub max_rows: i64,
    pub max_duration_ms: i64,
    pub max_payload_bytes: i64,
    pub max_temp_file_bytes: i64,
}

impl Budget {
    pub fn session(&self) -> SessionBudget {
        SessionBudget {
            connect_timeout_ms: self.connect_timeout_ms,
            statement_timeout_ms: self.statement_timeout_ms,
            lock_timeout_ms: self.lock_timeout_ms,
            idle_in_transaction_timeout_ms: self.idle_in_transaction_timeout_ms,
            max_duration_ms: self.max_duration_ms,
        }
    }
}

/// The part of the budget that shapes the session itself — shared by `acquire` and `diagnose`, so
/// a passing diagnostic opens exactly the session an acquisition would.
#[derive(Deserialize)]
#[expect(
    clippy::struct_field_names,
    reason = "field names are the wire's, pinned by the Java envelope writers"
)]
pub struct SessionBudget {
    pub connect_timeout_ms: i64,
    pub statement_timeout_ms: i64,
    pub lock_timeout_ms: i64,
    pub idle_in_transaction_timeout_ms: i64,
    pub max_duration_ms: i64,
}

/// The `{"type":"diagnose",...}` message — pinned by `ExecPlaneConnectivityCheck.writeDiagnoseEnvelope`.
/// Connection fields only: a diagnostic never probes, streams or writes a file.
#[derive(Deserialize)]
pub struct DiagnoseEnvelope {
    #[serde(rename = "type")]
    #[expect(
        dead_code,
        reason = "part of the diagnose wire contract, not read by this process"
    )]
    pub message_type: String,
    #[expect(
        dead_code,
        reason = "part of the diagnose wire contract, not read by this process"
    )]
    pub source_id: String,
    pub host: String,
    pub port: u16,
    pub database: String,
    pub user: String,
    pub password: String,
    pub budget: SessionBudget,
}

#[cfg(test)]
mod tests {
    use super::*;

    /// Pinned against the exact keys `ExecPlaneConnectivityCheck` writes — an unknown field is
    /// ignored, a missing one fails the parse (and the process exits 1 with nothing on stdout).
    #[test]
    fn diagnose_envelope_parses_the_java_shape() {
        let envelope: DiagnoseEnvelope = serde_json::from_str(
            r#"{"type":"diagnose","source_id":"s","host":"127.0.0.1","port":5432,"database":"esus",
                "user":"u","password":"p","budget":{"connect_timeout_ms":5000,"statement_timeout_ms":1,
                "lock_timeout_ms":2,"idle_in_transaction_timeout_ms":3,"max_duration_ms":4}}"#,
        )
        .unwrap();
        assert_eq!(envelope.port, 5432);
        assert_eq!(envelope.budget.idle_in_transaction_timeout_ms, 3);
        assert_eq!(envelope.budget.max_duration_ms, 4);
    }

    #[test]
    fn diagnose_envelope_without_a_budget_is_rejected() {
        let parsed: Result<DiagnoseEnvelope, _> = serde_json::from_str(
            r#"{"type":"diagnose","source_id":"s","host":"h","port":1,"database":"d","user":"u","password":"p"}"#,
        );
        assert!(parsed.is_err());
    }
}
