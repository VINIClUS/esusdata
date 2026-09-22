use serde::Deserialize;

/// The `{"type":"acquire",...}` message — field names and shape are pinned by
/// `SubprocessAcquisitionAdapter.writeAcquireEnvelope`, not by plan prose. Rust field names are
/// already the wire's snake_case, so no `#[serde(rename)]` is needed anywhere but `type`.
#[derive(Deserialize)]
pub struct AcquireEnvelope {
    #[serde(rename = "type")]
    #[allow(dead_code)]
    pub message_type: String,
    #[allow(dead_code)]
    pub source_id: String,
    pub host: String,
    pub port: u16,
    pub database: String,
    pub user: String,
    pub password: String,
    #[allow(dead_code)]
    pub municipality_ibge: String,
    pub pec_version: String,
    pub read_model: String,
    pub installation_role: String,
    #[allow(dead_code)]
    pub extraction_id: String,
    #[allow(dead_code)]
    pub period_start: String,
    #[allow(dead_code)]
    pub period_end_exclusive: String,
    #[allow(dead_code)]
    pub source_zone_id: String,
    /// Where this process must create the extract's data file (fatia 3 / ADR 0011) — an
    /// absolute path Java already reserved via its own lock + reconcile before this process
    /// was even spawned. Never a directory: the full `<extractionId>.jsonl.gz.tmp` filename.
    pub extract_temp_path: String,
    #[allow(dead_code)]
    pub query_checksum: String,
    pub adapter_version: String,
    pub budget: Budget,
}

#[derive(Deserialize)]
pub struct Budget {
    pub connect_timeout_ms: i64,
    #[allow(dead_code)]
    pub acquisition_timeout_ms: i64,
    pub statement_timeout_ms: i64,
    pub lock_timeout_ms: i64,
    pub idle_in_transaction_timeout_ms: i64,
    #[allow(dead_code)]
    pub max_rows: i64,
    #[allow(dead_code)]
    pub max_duration_ms: i64,
    #[allow(dead_code)]
    pub max_payload_bytes: i64,
    pub max_temp_file_bytes: i64,
}
