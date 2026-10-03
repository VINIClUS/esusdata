use serde::Deserialize;
use std::collections::BTreeMap;

/// The `{"type":"acquire",...}` message — field names and shape are pinned by
/// `SubprocessAcquisitionAdapter.writeAcquireEnvelope`, not by plan prose. Rust field names are
/// already the wire's `snake_case`, so no `#[serde(rename)]` is needed anywhere but `type`.
///
/// A canonical v2 acquisition (ADR 0030) adds `canonical_schema_version` and `parts`; both
/// default to empty, so C1's v1 envelope parses exactly as before and keeps its v1 path.
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
    /// PEM root(s) the source's certificate must chain to (ADR 0022); absent or `null` means a
    /// plaintext session, which Java only sends for loopback destinations.
    #[serde(default)]
    pub tls_root_cert: Option<String>,
    pub budget: Budget,
    /// `"2"` for a canonical v2 acquisition; absent in C1's v1 envelope.
    #[serde(default)]
    pub canonical_schema_version: Option<String>,
    /// The capabilities a canonical v2 acquisition reads, in part order, all in one transaction;
    /// empty in C1's v1 envelope.
    #[serde(default)]
    pub parts: Vec<PartRequest>,
}

/// One capability of a canonical v2 acquisition — the wire mirror of Java's `AcquisitionPart`
/// (ADR 0030), written by `ExecPlaneAcquisition`: which frozen query, the version, checksum and
/// record kind Java expects of it, its window and the values of its named binds.
#[derive(Debug, Deserialize)]
pub struct PartRequest {
    pub capability: String,
    pub adapter_version: String,
    pub query_checksum: String,
    pub record_kind: String,
    pub period_start: String,
    pub period_end_exclusive: String,
    /// `TEXT_ARRAY` binds (code lists), by bind name, values in bind order.
    #[serde(default)]
    pub array_params: BTreeMap<String, Vec<String>>,
    /// `DATE` binds, by bind name, as ISO `yyyy-MM-dd`.
    #[serde(default)]
    pub date_params: BTreeMap<String, String>,
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
    /// Same meaning as `AcquireEnvelope::tls_root_cert`.
    #[serde(default)]
    pub tls_root_cert: Option<String>,
    pub budget: SessionBudget,
}

/// The `{"type":"check_isolation",...}` and `{"type":"check_coverage",...}` messages — pinned by
/// `ExecPlaneAggregateRead.writeEnvelope`. The source's identity (to find what to probe) and the
/// period to count, never the municipality: comparing the counts with the registered IBGE is
/// Java's job, so this process only reports what the base holds.
#[derive(Deserialize)]
pub struct AggregateEnvelope {
    #[serde(rename = "type")]
    #[expect(
        dead_code,
        reason = "part of the check_isolation/check_coverage wire contract, not read by this process"
    )]
    pub message_type: String,
    #[expect(
        dead_code,
        reason = "part of the check_isolation/check_coverage wire contract, not read by this process"
    )]
    pub source_id: String,
    pub host: String,
    pub port: u16,
    pub database: String,
    pub user: String,
    pub password: String,
    pub pec_version: String,
    pub read_model: String,
    pub installation_role: String,
    pub adapter_version: String,
    pub period_start: String,
    pub period_end_exclusive: String,
    /// Same meaning as `AcquireEnvelope::tls_root_cert`.
    #[serde(default)]
    pub tls_root_cert: Option<String>,
    pub budget: AggregateBudget,
}

/// The session budget plus a ceiling on result rows — one per aggregate group of the period.
#[derive(Deserialize)]
pub struct AggregateBudget {
    #[serde(flatten)]
    pub session: SessionBudget,
    pub max_rows: i64,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn aggregate_envelope_parses_the_java_shape() {
        let envelope: AggregateEnvelope = serde_json::from_str(
            r#"{"type":"check_isolation","source_id":"s","host":"127.0.0.1","port":5432,
                "database":"esus","user":"u","password":"p","pec_version":"5.5.28",
                "read_model":"PEC_DW","installation_role":"PRONTUARIO","adapter_version":"0.1.0",
                "period_start":"2026-03-01","period_end_exclusive":"2026-04-01",
                "tls_root_cert":null,"budget":{"connect_timeout_ms":5000,"statement_timeout_ms":1,
                "lock_timeout_ms":2,"idle_in_transaction_timeout_ms":3,"max_duration_ms":4,
                "max_rows":5}}"#,
        )
        .unwrap();
        assert_eq!(envelope.period_start, "2026-03-01");
        assert_eq!(envelope.budget.session.max_duration_ms, 4);
        assert_eq!(envelope.budget.max_rows, 5);
    }

    #[test]
    fn aggregate_envelope_without_a_period_is_rejected() {
        let parsed: Result<AggregateEnvelope, _> = serde_json::from_str(
            r#"{"type":"check_isolation","source_id":"s","host":"h","port":1,"database":"d",
                "user":"u","password":"p","pec_version":"5.5.28","read_model":"PEC_DW",
                "installation_role":"PRONTUARIO","adapter_version":"0.1.0",
                "budget":{"connect_timeout_ms":1,"statement_timeout_ms":1,"lock_timeout_ms":1,
                "idle_in_transaction_timeout_ms":1,"max_duration_ms":1,"max_rows":1}}"#,
        );
        assert!(parsed.is_err());
    }

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
        assert_eq!(envelope.tls_root_cert, None);
    }

    #[test]
    fn diagnose_envelope_carries_the_tls_root_certificate_path() {
        let envelope: DiagnoseEnvelope = serde_json::from_str(
            r#"{"type":"diagnose","source_id":"s","host":"192.0.2.1","port":5433,"database":"esus",
                "user":"u","password":"p","tls_root_cert":"/etc/observatorio-aps/pec-ca.pem",
                "budget":{"connect_timeout_ms":5000,"statement_timeout_ms":1,
                "lock_timeout_ms":2,"idle_in_transaction_timeout_ms":3,"max_duration_ms":4}}"#,
        )
        .unwrap();
        assert_eq!(
            envelope.tls_root_cert.as_deref(),
            Some("/etc/observatorio-aps/pec-ca.pem")
        );
    }

    /// An acquire envelope as `ExecPlaneAcquisition` writes it, with `extra` fields appended.
    fn acquire_json(extra: &str) -> String {
        format!(
            r#"{{"type":"acquire","source_id":"s","host":"127.0.0.1","port":5432,"database":"esus",
                "user":"u","password":"p","municipality_ibge":"3541307","pec_version":"5.5.28",
                "read_model":"PEC_DW","installation_role":"PRONTUARIO","extraction_id":"e",
                "period_start":"2026-01-01","period_end_exclusive":"2026-04-01",
                "source_zone_id":"America/Sao_Paulo","extract_temp_path":"/tmp/e.jsonl.gz.tmp",
                "query_checksum":"sha256:c","adapter_version":"0.1.0","tls_root_cert":null,
                "budget":{{"connect_timeout_ms":1,"acquisition_timeout_ms":2,
                "statement_timeout_ms":3,"lock_timeout_ms":4,"idle_in_transaction_timeout_ms":5,
                "max_rows":6,"max_duration_ms":7,"max_payload_bytes":8,
                "max_temp_file_bytes":9}}{extra}}}"#
        )
    }

    /// C1's envelope carries neither field: it parses unchanged and keeps the v1 path.
    #[test]
    fn a_v1_acquire_envelope_has_no_parts() {
        let envelope: AcquireEnvelope = serde_json::from_str(&acquire_json("")).unwrap();
        assert!(envelope.parts.is_empty());
        assert_eq!(envelope.canonical_schema_version, None);
        assert_eq!(envelope.adapter_version, "0.1.0");
        assert_eq!(envelope.budget.max_temp_file_bytes, 9);
    }

    #[test]
    fn a_v2_acquire_envelope_carries_its_parts_in_order() {
        let envelope: AcquireEnvelope = serde_json::from_str(&acquire_json(
            r#","canonical_schema_version":"2","parts":[
                {"capability":"procedure_performed","adapter_version":"0.1.0",
                 "query_checksum":"sha256:a","record_kind":"procedure_event",
                 "period_start":"2026-01-01","period_end_exclusive":"2026-04-01",
                 "array_params":{"procedure_codes":["0301010080","ABEX001"]},
                 "date_params":{"birth_date_to":"2024-12-31","birth_date_from":"2024-01-01"}},
                {"capability":"citizen","adapter_version":"0.1.0","query_checksum":"sha256:b",
                 "record_kind":"person","period_start":"2026-03-01",
                 "period_end_exclusive":"2026-04-01"}]"#,
        ))
        .unwrap();
        assert_eq!(envelope.canonical_schema_version.as_deref(), Some("2"));
        assert_eq!(envelope.parts.len(), 2);
        let procedures = &envelope.parts[0];
        assert_eq!(procedures.capability, "procedure_performed");
        assert_eq!(procedures.record_kind, "procedure_event");
        assert_eq!(
            procedures.array_params["procedure_codes"],
            ["0301010080", "ABEX001"]
        );
        assert_eq!(
            procedures.date_params.keys().collect::<Vec<_>>(),
            ["birth_date_from", "birth_date_to"]
        );
        // Absent maps are empty, never a parse failure: the registry check names what is missing.
        assert!(envelope.parts[1].array_params.is_empty());
        assert!(envelope.parts[1].date_params.is_empty());
    }

    #[test]
    fn a_v2_part_without_its_window_is_rejected() {
        let parsed: Result<AcquireEnvelope, _> = serde_json::from_str(&acquire_json(
            r#","canonical_schema_version":"2","parts":[{"capability":"citizen",
                "adapter_version":"0.1.0","query_checksum":"sha256:b","record_kind":"person"}]"#,
        ));
        assert!(parsed.is_err());
    }

    #[test]
    fn diagnose_envelope_without_a_budget_is_rejected() {
        let parsed: Result<DiagnoseEnvelope, _> = serde_json::from_str(
            r#"{"type":"diagnose","source_id":"s","host":"h","port":1,"database":"d","user":"u","password":"p"}"#,
        );
        assert!(parsed.is_err());
    }
}
