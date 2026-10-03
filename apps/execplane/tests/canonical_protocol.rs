//! The compiled binary's canonical v2 handshake (ADR 0030), end to end over its stdin/stdout: an
//! `acquire` with `parts` takes the v2 path, a request this build cannot honour is refused before
//! any connection, and against a real PostgreSQL the `probe` reports every part before Java's
//! decision. No part's query runs here — what each capability reads is `canonical.rs`'s concern,
//! and the packaged queries are free to change without touching this test.

#[path = "support/postgres.rs"]
mod postgres_container;

/// Test code only — the clippy gate allows `unwrap` in tests, and these helpers serve nothing else.
#[cfg(test)]
mod protocol {
    use super::postgres_container::{Postgres, DATABASE, PASSWORD, USER};
    use serde_json::{json, Value};
    use sha2::{Digest, Sha256};
    use std::fmt::Write as _;
    use std::io::{BufRead, BufReader, Write};
    use std::path::{Path, PathBuf};
    use std::process::{Child, ChildStdout, Command, Stdio};
    use std::time::{Duration, Instant};

    const CITIZEN_SQL: &str =
        include_str!("../../../contracts/compatibility/queries/citizen@0.1.0.sql");
    const CONDITION_LIST_SQL: &str =
        include_str!("../../../contracts/compatibility/queries/condition_list@0.1.0.sql");

    /// A port nothing listens on: a request refused before connecting never notices.
    const UNREACHABLE_PORT: u16 = 1;

    fn checksum(sql: &str) -> String {
        Sha256::digest(sql.as_bytes())
            .iter()
            .fold("sha256:".to_string(), |mut hex, byte| {
                let _ = write!(hex, "{byte:02x}");
                hex
            })
    }

    fn citizen() -> Value {
        json!({
            "capability": "citizen", "adapter_version": "0.1.0",
            "query_checksum": checksum(CITIZEN_SQL), "record_kind": "person",
            "period_start": "2026-03-01", "period_end_exclusive": "2026-04-01",
            "array_params": {},
            "date_params": {"birth_date_from": "2024-01-01", "birth_date_to": "2024-12-31"},
        })
    }

    fn condition_list() -> Value {
        json!({
            "capability": "condition_list", "adapter_version": "0.1.0",
            "query_checksum": checksum(CONDITION_LIST_SQL), "record_kind": "condition",
            "period_start": "2025-04-01", "period_end_exclusive": "2026-04-01",
            "array_params": {"ciap_codes": ["T90"], "cid_codes": ["E11"]},
            "date_params": {"birth_date_from": "1900-01-01", "birth_date_to": "2008-03-31"},
        })
    }

    /// A path the binary would create only after `proceed`.
    fn extract_temp_path(name: &str) -> PathBuf {
        std::env::temp_dir().join(format!(
            "execplane-protocol-{}-{name}.jsonl.gz.tmp",
            std::process::id()
        ))
    }

    /// `acquire` as `ExecPlaneAcquisition` writes it; `parts` absent means C1's v1 envelope. The PEC
    /// version is one no matrix entry lists, so the probe measures nothing whatever the matrix holds.
    fn envelope(port: u16, parts: Option<Value>, temp_path: &Path) -> Value {
        let mut envelope = json!({
            "type": "acquire", "source_id": "src-protocol", "host": "127.0.0.1", "port": port,
            "database": DATABASE, "user": USER, "password": PASSWORD,
            "municipality_ibge": "3541307", "pec_version": "0.0.0-unlisted", "read_model": "PEC_DW",
            "installation_role": "PRONTUARIO", "extraction_id": "protocol",
            "period_start": "2025-04-01", "period_end_exclusive": "2026-04-01",
            "source_zone_id": "America/Sao_Paulo", "extract_temp_path": temp_path,
            "query_checksum": "sha256:plan", "adapter_version": "0.1.0", "tls_root_cert": null,
            "budget": {"connect_timeout_ms": 5000, "acquisition_timeout_ms": 60_000,
                "statement_timeout_ms": 30_000, "lock_timeout_ms": 5000,
                "idle_in_transaction_timeout_ms": 30_000, "max_rows": 1000, "max_duration_ms": 60_000,
                "max_payload_bytes": 1_000_000, "max_temp_file_bytes": 10_000_000},
        });
        if let Some(parts) = parts {
            envelope["adapter_version"] = json!("canonical-v2");
            envelope["canonical_schema_version"] = json!("2");
            envelope["parts"] = parts;
        }
        envelope
    }

    struct ExecPlane {
        child: Child,
        stdout: BufReader<ChildStdout>,
    }

    impl ExecPlane {
        fn start(envelope: &Value) -> Self {
            let mut child = Command::new(env!("CARGO_BIN_EXE_observatorio-execplane"))
                .stdin(Stdio::piped())
                .stdout(Stdio::piped())
                .stderr(Stdio::null())
                .spawn()
                .unwrap();
            let stdout = BufReader::new(child.stdout.take().unwrap());
            let mut plane = Self { child, stdout };
            plane.send(envelope);
            plane
        }

        fn send(&mut self, message: &Value) {
            let stdin = self.child.stdin.as_mut().unwrap();
            writeln!(stdin, "{message}").unwrap();
            stdin.flush().unwrap();
        }

        /// The next message, or `None` once the binary closed its stdout.
        fn receive(&mut self) -> Option<Value> {
            let mut line = String::new();
            let read = self.stdout.read_line(&mut line).unwrap();
            (read > 0).then(|| serde_json::from_str(&line).unwrap())
        }

        fn exit_code(mut self) -> Option<i32> {
            drop(self.child.stdin.take());
            self.child.wait().unwrap().code()
        }
    }

    /// The error a refused request reports, and the exit code that follows it.
    fn refusal(parts: Value) -> (Value, Option<i32>) {
        let temp_path = extract_temp_path("refused");
        let mut plane = ExecPlane::start(&envelope(UNREACHABLE_PORT, Some(parts), &temp_path));
        let message = plane.receive().unwrap();
        assert_eq!(plane.receive(), None);
        assert!(!temp_path.exists());
        (message, plane.exit_code())
    }

    #[test]
    fn a_capability_this_build_does_not_have_is_refused_before_connecting() {
        let mut stranger = citizen();
        stranger["capability"] = json!("pregnancy_outcome");
        let (message, exit_code) = refusal(json!([citizen(), stranger]));
        assert_eq!(message["type"], "error");
        assert_eq!(message["code"], "UNKNOWN_CAPABILITY");
        assert_eq!(message["uncertain"], false);
        assert!(message["detail"]
            .as_str()
            .unwrap()
            .starts_with("part 1: capability pregnancy_outcome"));
        assert_eq!(exit_code, Some(1));
    }

    #[test]
    fn a_query_other_than_the_compiled_one_is_refused_before_connecting() {
        let mut tampered = citizen();
        tampered["query_checksum"] = json!(format!("sha256:{}", "0".repeat(64)));
        let (message, exit_code) = refusal(json!([tampered]));
        assert_eq!(message["code"], "UNKNOWN_CAPABILITY");
        assert_eq!(exit_code, Some(1));
    }

    #[test]
    fn a_bind_without_a_value_is_an_invalid_request_before_connecting() {
        let mut incomplete = condition_list();
        incomplete["array_params"] = json!({"ciap_codes": ["T90"]});
        let (message, exit_code) = refusal(json!([incomplete]));
        assert_eq!(message["code"], "INVALID_REQUEST");
        assert_eq!(message["uncertain"], false);
        assert!(message["detail"]
            .as_str()
            .unwrap()
            .contains("code-list bind cid_codes has no value"));
        assert_eq!(exit_code, Some(1));
    }

    #[test]
    fn the_probe_reports_every_part_and_the_v1_envelope_keeps_its_own() {
        let Some(postgres) = Postgres::start() else {
            return;
        };

        let temp_path = extract_temp_path("v2");
        let mut plane = ExecPlane::start(&envelope(
            postgres.port,
            Some(json!([citizen(), condition_list()])),
            &temp_path,
        ));
        let probe = plane.receive().unwrap();
        assert_eq!(probe["type"], "probe");
        assert_eq!(probe["postgres_version"], "9.6.13");
        assert_eq!(
            probe["parts"],
            json!([
                {"index": 0, "capability": "citizen", "adapter_version": "0.1.0",
                 "query_checksum": checksum(CITIZEN_SQL), "objects": {}},
                {"index": 1, "capability": "condition_list", "adapter_version": "0.1.0",
                 "query_checksum": checksum(CONDITION_LIST_SQL), "objects": {}},
            ])
        );
        assert!(probe.get("objects").is_none());
        plane.send(&json!({"type": "abort", "code": "COMPATIBILITY_MISMATCH", "detail": "test"}));
        assert_eq!(plane.receive(), None);
        assert_eq!(plane.exit_code(), Some(1));
        // The extract file is opened only after `proceed`.
        assert!(!temp_path.exists());

        // Anything but a decision ends the conversation as a protocol error.
        let mut plane = ExecPlane::start(&envelope(
            postgres.port,
            Some(json!([citizen()])),
            &temp_path,
        ));
        assert_eq!(plane.receive().unwrap()["type"], "probe");
        plane.send(&json!({"type": "cancel"}));
        assert_eq!(plane.exit_code(), Some(3));

        // C1's envelope, without parts, still gets the v1 probe: one query checksum, one object map.
        let mut plane = ExecPlane::start(&envelope(postgres.port, None, &temp_path));
        let probe = plane.receive().unwrap();
        assert_eq!(probe["type"], "probe");
        assert!(probe.get("parts").is_none());
        assert!(probe["query_checksum"]
            .as_str()
            .unwrap()
            .starts_with("sha256:"));
        assert!(probe["objects"].is_object());
        plane.send(&json!({"type": "abort", "code": "COMPATIBILITY_MISMATCH", "detail": "test"}));
        assert_eq!(plane.exit_code(), Some(1));

        assert_no_session_left(&postgres);
    }

    /// Every conversation above ended its read-only session with the binary's exit.
    fn assert_no_session_left(postgres: &Postgres) {
        let mut client = postgres.connect().unwrap();
        let deadline = Instant::now() + Duration::from_secs(10);
        loop {
            let sessions: i64 = client
                .query_one(
                    "SELECT count(*) FROM pg_stat_activity WHERE application_name = 'observatorio-aps'",
                    &[],
                )
                .unwrap()
                .get(0);
            if sessions == 0 {
                return;
            }
            assert!(Instant::now() < deadline, "{sessions} sessions left open");
            std::thread::sleep(Duration::from_millis(100));
        }
    }
}
