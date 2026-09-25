mod envelope;
mod extract;
mod matrix;
mod probe;
mod stream;
mod tls;

use envelope::{AcquireEnvelope, DiagnoseEnvelope, SessionBudget};
use postgres::{Client, Config, IsolationLevel};
use serde_json::{json, Map};
use sha2::{Digest, Sha256};
use std::error::Error;
use std::io::{self, BufRead, Write};
use std::str::FromStr;
use std::time::{Duration, Instant};
use zeroize::Zeroize;

const CAPABILITY: &str = "individual_encounter_modality";

/// Byte-identical to the frozen query Java loads from the same file (plan §2.3). Its SHA-256 is
/// reported in the probe handshake; `stream::stream_query` converts it to `$1,$2,$3` placeholders
/// before executing it (the native wire protocol doesn't understand JDBC's `?`), but the checksum
/// below is always computed from these unconverted bytes.
const QUERY_TEXT: &str = include_str!(
    "../../../contracts/compatibility/queries/individual_encounter_modality@0.1.0.sql"
);

fn main() {
    match run() {
        Ok(exit_code) => std::process::exit(exit_code),
        Err(err) => {
            eprintln!("observatorio-execplane: {err:?}");
            std::process::exit(1);
        }
    }
}

fn run() -> Result<i32, Box<dyn Error>> {
    let stdin = io::stdin();
    let mut lines = stdin.lock().lines();

    let first_line = lines
        .next()
        .ok_or("stdin closed before an envelope was received")??;
    let message: serde_json::Value = serde_json::from_str(&first_line)?;
    match message.get("type").and_then(serde_json::Value::as_str) {
        Some("acquire") => acquire(serde_json::from_value(message)?, lines),
        Some("diagnose") => diagnose(serde_json::from_value(message)?),
        other => {
            eprintln!("observatorio-execplane: expected 'acquire' or 'diagnose', got: {other:?}");
            Ok(3)
        }
    }
}

/// Opens the one kind of session this process ever uses, for both `acquire` and `diagnose`.
/// `Ok(None)` means the failure was already reported on stdout and the process should exit 1.
fn connect_session(
    host: &str,
    port: u16,
    database: &str,
    user: &str,
    password: &mut String,
    session_tls: &tls::SessionTls,
    budget: &SessionBudget,
) -> Result<Option<Client>, Box<dyn Error>> {
    let mut config = Config::new();
    config
        .host(host)
        .port(port)
        .dbname(database)
        .user(user)
        .password(password.as_bytes())
        .connect_timeout(Duration::from_millis(budget.connect_timeout_ms.max(0).unsigned_abs()))
        // Bounds a blackholed TCP connection (packets sent, never acknowledged) the same way
        // pgJDBC's `socketTimeout` property did on the retired JDBC path — without this, a dead
        // connection leaves a blocking socket read waiting indefinitely, and neither
        // max_duration_ms nor cooperative cancellation can free the sole acquisition worker.
        .tcp_user_timeout(Duration::from_millis(budget.max_duration_ms.max(0).unsigned_abs()));
    let connect_result = session_tls.connect(&mut config);
    // Zeroed regardless of outcome — mirrors the Java side's own finally-block zeroing of this
    // same secret right after writing the envelope.
    password.zeroize();
    let mut client = match connect_result {
        Ok(client) => client,
        Err(err) => {
            // No session ever existed, so nothing can still be executing on the source — not
            // "uncertain" in the ENG-51 sense, mirroring JdbcAcquisitionAdapter's outer catch for a
            // connection that never became live. The SQLSTATE lets Java's FailureClassifier tell a
            // rejected credential (28P01 → SOURCE_AUTHENTICATION_FAILED) from an unreachable
            // server; a failure with no server-side code gets pgJDBC's own `08001` for the same
            // case (PSQLState.CONNECTION_UNABLE_TO_CONNECT).
            let sqlstate = err.code().map_or("08001", |code| code.code());
            write_line(&json!({
                "type": "error", "code": "SQL_ERROR", "sqlstate": sqlstate,
                "detail": err.to_string(), "uncertain": false,
            }))?;
            return Ok(None);
        }
    };

    // Same session GUCs the JDBC pool's connectionInitSql set, byte-identical application_name
    // so the child is findable in pg_stat_activity under the name operators are told to look for.
    let session_setup = client.batch_execute(&format!(
        "SET application_name = 'observatorio-aps'; \
         SET default_transaction_read_only = on; \
         SET statement_timeout = {}; \
         SET lock_timeout = {}; \
         SET idle_in_transaction_session_timeout = {};",
        budget.statement_timeout_ms, budget.lock_timeout_ms, budget.idle_in_transaction_timeout_ms,
    ));
    if let Err(err) = session_setup {
        report_pre_probe_failure(&err)?;
        return Ok(None);
    }
    Ok(Some(client))
}

/// Builds the session's TLS mode before any connection attempt. A bad `tls_root_cert` is reported
/// like a connection that never opened (`08001`, not uncertain): no session existed, and falling
/// back to plaintext would drop the protection the deployment asked for.
fn session_tls_or_report(
    root_cert: Option<&str>,
    password: &mut String,
) -> Result<Option<tls::SessionTls>, Box<dyn Error>> {
    match tls::SessionTls::from_root_cert(root_cert) {
        Ok(session_tls) => Ok(Some(session_tls)),
        Err(err) => {
            password.zeroize();
            write_line(&json!({
                "type": "error", "code": "SQL_ERROR", "sqlstate": "08001",
                "detail": format!("invalid tls_root_cert: {err}"), "uncertain": false,
            }))?;
            Ok(None)
        }
    }
}

/// `POST /sources/{id}/test` (ADR 0017): the same session an acquisition would open, then
/// `SELECT 1` in a read-only transaction. Success is `diagnosed` **and** exit 0; failures reuse the
/// pre-probe `error` shapes, so the SQLSTATE reaches Java the same way it does for an acquisition.
fn diagnose(mut envelope: DiagnoseEnvelope) -> Result<i32, Box<dyn Error>> {
    let Some(session_tls) =
        session_tls_or_report(envelope.tls_root_cert.as_deref(), &mut envelope.password)?
    else {
        return Ok(1);
    };
    let Some(mut client) = connect_session(
        &envelope.host,
        envelope.port,
        &envelope.database,
        &envelope.user,
        &mut envelope.password,
        &session_tls,
        &envelope.budget,
    )?
    else {
        return Ok(1);
    };
    let checked = client
        .build_transaction()
        .read_only(true)
        .start()
        .and_then(|mut txn| {
            txn.query_one("SELECT 1", &[])?;
            txn.rollback()
        });
    if let Err(err) = checked {
        return report_pre_probe_failure(&err);
    }
    write_line(&json!({ "type": "diagnosed" }))?;
    Ok(0)
}

#[expect(
    clippy::too_many_lines,
    reason = "one linear acquire protocol: handshake, decision, stream, outcome"
)]
fn acquire(
    mut envelope: AcquireEnvelope,
    mut lines: io::Lines<io::StdinLock<'static>>,
) -> Result<i32, Box<dyn Error>> {
    let session_budget = envelope.budget.session();
    let Some(session_tls) =
        session_tls_or_report(envelope.tls_root_cert.as_deref(), &mut envelope.password)?
    else {
        return Ok(1);
    };
    let Some(mut client) = connect_session(
        &envelope.host,
        envelope.port,
        &envelope.database,
        &envelope.user,
        &mut envelope.password,
        &session_tls,
        &session_budget,
    )?
    else {
        return Ok(1);
    };
    // Captured before any &mut borrow of client (e.g. build_transaction()) makes that
    // impossible — CancelToken is independent of the connection it was derived from and stays
    // usable from another thread for as long as the process runs (plan §2.7's cancellation path).
    let canceller = tls::Canceller::new(client.cancel_token(), session_tls);

    // ENG-43: the probe and (in the next slice) the frozen query itself run inside one
    // read-only repeatable-read transaction — the same snapshot, never reopened. This
    // transaction is deliberately kept open past the probe: it only ends via explicit
    // rollback below (abort/not-yet-implemented) or, in the next slice, after streaming.
    let mut txn = match client
        .build_transaction()
        .read_only(true)
        .isolation_level(IsolationLevel::RepeatableRead)
        .start()
    {
        Ok(txn) => txn,
        Err(err) => return report_pre_probe_failure(&err),
    };

    // Started here, not at the first row of the frozen query: mirrors BudgetGuard's clock on the
    // JDBC path, which starts at PecSourceConnection.acquire() — before compatibility probing,
    // not after. A large source's probes (each running its own query) can otherwise burn most or
    // all of a small max_duration_ms budget before a single row is ever read.
    let start = Instant::now();
    let budget = stream::Budget {
        max_rows: envelope.budget.max_rows,
        max_duration_ms: envelope.budget.max_duration_ms,
        max_payload_bytes: envelope.budget.max_payload_bytes,
    };

    let (postgres_version, objects_json) = match run_probes(&mut txn, &envelope, &start, &budget) {
        Ok(report) => report,
        Err(err) => {
            end_transaction(txn.rollback());
            return report_pre_probe_failure(err.as_ref());
        }
    };

    let query_checksum = format!(
        "sha256:{}",
        hex_encode(Sha256::digest(QUERY_TEXT.as_bytes()))
    );
    write_line(&json!({
        "type": "probe",
        "postgres_version": postgres_version,
        "query_checksum": query_checksum,
        "objects": objects_json,
    }))?;

    let decision = lines
        .next()
        .ok_or("stdin closed before a decision was received")??;
    if decision.contains("\"type\":\"abort\"") {
        txn.rollback()?;
        return Ok(1);
    }
    if !decision.contains("\"type\":\"proceed\"") {
        eprintln!("observatorio-execplane: expected 'proceed' or 'abort', got: {decision}");
        txn.rollback()?;
        return Ok(3);
    }

    // The cancel-listener thread (spawned inside stream_query) needs its own lock on stdin;
    // this one must be released first or the two would contend for the same underlying lock.
    drop(lines);

    let period_start = chrono::NaiveDate::from_str(&envelope.period_start)?;
    let period_end_exclusive = chrono::NaiveDate::from_str(&envelope.period_end_exclusive)?;

    // Opened only now, after the compatibility handshake decided to proceed — mirrors the
    // pre-fatia-3 timing of opening ExtractWriter right before "proceed" was sent, so a local
    // sink failure (disk full, path unwritable) still aborts before any row is read. Java has
    // already taken the `.extract.lock` and reconciled this extraction id before ever spawning
    // this process (DelegatedExtractPublication, fatia 3 / ADR 0011) — this process only ever
    // creates the one `.tmp` path it was handed.
    let scope = extract::Scope {
        source_id: envelope.source_id.clone(),
        municipality_ibge: envelope.municipality_ibge.clone(),
        period_start,
        period_end_exclusive,
    };
    let mut sink = match extract::ExtractSink::open(
        std::path::Path::new(&envelope.extract_temp_path),
        envelope.budget.max_temp_file_bytes,
        scope,
    ) {
        Ok(sink) => sink,
        Err(err) => {
            // A live connection already exists (the probe ran) — this is uncertain territory,
            // same reasoning as every other post-probe failure.
            let (code, detail) = extract_error_code_and_detail(err);
            write_line(
                &json!({ "type": "error", "code": code, "detail": detail, "uncertain": true }),
            )?;
            end_transaction(txn.rollback());
            return Ok(1);
        }
    };

    let outcome = stream::stream_query(
        &mut txn,
        canceller,
        QUERY_TEXT,
        &envelope.source_id,
        &envelope.municipality_ibge,
        period_start,
        period_end_exclusive,
        &budget,
        start,
        &mut sink,
    )?;

    let exit_code = match outcome {
        stream::StreamOutcome::Success => match sink.finish() {
            Ok(completion) => {
                write_line(&json!({
                    "type": "complete",
                    "row_count": completion.row_count,
                    "exclusion_count": completion.exclusion_count,
                    "checksum": completion.checksum,
                    "compressed_bytes": completion.compressed_bytes,
                }))?;
                end_transaction(txn.commit());
                0
            }
            Err(err) => {
                let (code, detail) = extract_error_code_and_detail(err);
                write_line(
                    &json!({ "type": "error", "code": code, "detail": detail, "uncertain": true }),
                )?;
                end_transaction(txn.rollback());
                1
            }
        },
        stream::StreamOutcome::BudgetExceeded(detail) => {
            write_line(&json!({
                "type": "error", "code": "SOURCE_BUDGET_EXCEEDED", "detail": detail, "uncertain": true,
            }))?;
            end_transaction(txn.rollback());
            1
        }
        stream::StreamOutcome::Cancelled => {
            write_line(&json!({
                "type": "error", "code": "CANCELLED", "detail": "cancelled cooperatively", "uncertain": true,
            }))?;
            end_transaction(txn.rollback());
            2
        }
        stream::StreamOutcome::Failed { detail, sqlstate } => {
            let mut error = json!({
                "type": "error", "code": "UNCLASSIFIED_ERROR", "detail": detail, "uncertain": true,
            });
            if let Some(sqlstate) = sqlstate {
                error["code"] = json!("SQL_ERROR");
                error["sqlstate"] = json!(sqlstate);
            }
            write_line(&error)?;
            end_transaction(txn.rollback());
            1
        }
        stream::StreamOutcome::InvalidRecord(detail) => {
            write_line(&json!({
                "type": "error", "code": "INVALID_EXTRACT_RECORD", "detail": detail, "uncertain": true,
            }))?;
            end_transaction(txn.rollback());
            1
        }
    };
    Ok(exit_code)
}

/// `ExtractError` has no `SOURCE_BUDGET_EXCEEDED` vs `UNCLASSIFIED_ERROR` distinction baked into
/// its own type name — this is the one place that maps it to the wire's error codes, mirroring
/// how `stream::StreamOutcome`'s variants are mapped just above.
fn extract_error_code_and_detail(err: extract::ExtractError) -> (&'static str, String) {
    match err {
        extract::ExtractError::BudgetExceeded(detail) => ("SOURCE_BUDGET_EXCEEDED", detail),
        extract::ExtractError::InvalidRecord(detail) => ("INVALID_EXTRACT_RECORD", detail),
        extract::ExtractError::Io(detail) => ("UNCLASSIFIED_ERROR", detail),
    }
}

/// A cancel packet that arrives after the row loop already reached a decided outcome (all rows
/// read, or a budget/error outcome already written) can land on this commit/rollback instead of
/// the query that was actually running — the read-only transaction has nothing left to lose from
/// that, and the outcome above was already decided from data actually seen, so this failing must
/// not override it or turn into an ambiguous process exit.
fn end_transaction(result: Result<(), postgres::Error>) {
    if let Err(err) = result {
        eprintln!("observatorio-execplane: commit/rollback after decided outcome failed: {err}");
    }
}

fn write_line(value: &serde_json::Value) -> Result<(), Box<dyn Error>> {
    let mut stdout = io::stdout().lock();
    stdout.write_all(serde_json::to_string(value)?.as_bytes())?;
    stdout.write_all(b"\n")?;
    stdout.flush()?;
    Ok(())
}

fn hex_encode(bytes: impl AsRef<[u8]>) -> String {
    use std::fmt::Write as _;
    bytes.as_ref().iter().fold(String::new(), |mut hex, b| {
        let _ = write!(hex, "{b:02x}");
        hex
    })
}

/// Everything the `probe` message reports, measured inside the already-open read-only
/// transaction. Split out of `run` only so that every failure in here reaches
/// `report_pre_probe_failure` through one `Err`, instead of a bare `?` closing stdout silently.
fn run_probes(
    txn: &mut postgres::Transaction,
    envelope: &AcquireEnvelope,
    start: &Instant,
    budget: &stream::Budget,
) -> Result<(String, Map<String, serde_json::Value>), Box<dyn Error>> {
    let postgres_version: String = txn
        .query_one("SELECT current_setting('server_version')", &[])?
        .get::<_, String>(0)
        .trim()
        .to_string();
    check_probe_duration(start, budget)?;

    let objects = matrix::objects_to_probe(
        CAPABILITY,
        &envelope.adapter_version,
        &envelope.pec_version,
        &envelope.read_model,
        &envelope.installation_role,
    );

    let mut objects_json = Map::new();
    for object in &objects {
        let probed = probe::probe_object(txn, &object.object, &object.columns_used)?;
        objects_json.insert(object.object.clone(), probed);
        check_probe_duration(start, budget)?;
    }
    Ok((postgres_version, objects_json))
}

/// A live session already exists by this point, so every pre-probe failure is "uncertain" in the
/// ENG-51 sense — same as the JDBC path, where compatibility probing runs inside the catch that
/// calls `onUncertainOutcome`. The SQLSTATE, when there is one, still reaches Java so it
/// classifies the failure itself (e.g. `42501` → `SQL_ERROR`, a broken connection → transient).
fn report_pre_probe_failure(err: &(dyn Error + 'static)) -> Result<i32, Box<dyn Error>> {
    let mut error = json!({
        "type": "error", "code": "UNCLASSIFIED_ERROR", "detail": err.to_string(), "uncertain": true,
    });
    let postgres_error = err.downcast_ref::<postgres::Error>();
    if err.is::<ProbeBudgetExceeded>() || postgres_error.is_some_and(is_timeout_budget) {
        error["code"] = json!("SOURCE_BUDGET_EXCEEDED");
    } else if let Some(sqlstate) = postgres_error.and_then(stream::sqlstate_of) {
        error["code"] = json!("SQL_ERROR");
        error["sqlstate"] = json!(sqlstate);
    }
    write_line(&error)?;
    Ok(1)
}

/// `statement_timeout`/`lock_timeout` are read-budget limits set by this process itself. Same test
/// as `IndividualEncounterModalityCapability.isPostgresBudgetCancellation` on the JDBC path: the
/// SQLSTATE alone isn't enough, since an external `pg_cancel_backend` also reports `57014` and
/// must stay a plain `SQL_ERROR`.
fn is_timeout_budget(err: &postgres::Error) -> bool {
    let Some(db_error) = err.as_db_error() else {
        return false;
    };
    let message = db_error.message().to_lowercase();
    (db_error.code() == &postgres::error::SqlState::QUERY_CANCELED
        && message.contains("statement timeout"))
        || (db_error.code() == &postgres::error::SqlState::LOCK_NOT_AVAILABLE
            && message.contains("lock timeout"))
}

/// Mirrors `BudgetGuard.checkDuration` on the JDBC path, whose clock also starts before
/// compatibility probing: a probe-phase overrun is a real `SOURCE_BUDGET_EXCEEDED`, reported as
/// such by `report_pre_probe_failure` rather than as a generic failure.
#[derive(Debug)]
struct ProbeBudgetExceeded(String);

impl std::fmt::Display for ProbeBudgetExceeded {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        f.write_str(&self.0)
    }
}

impl Error for ProbeBudgetExceeded {}

fn check_probe_duration(start: &Instant, budget: &stream::Budget) -> Result<(), Box<dyn Error>> {
    match stream::check_duration(start, budget) {
        Some(stream::StreamOutcome::BudgetExceeded(detail)) => {
            Err(Box::new(ProbeBudgetExceeded(detail)))
        }
        _ => Ok(()),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const BUDGET: &str = r#""connect_timeout_ms":2000,"statement_timeout_ms":1,"lock_timeout_ms":2,
        "idle_in_transaction_timeout_ms":3,"max_duration_ms":4"#;

    fn diagnose_envelope(tls_root_cert: &str) -> DiagnoseEnvelope {
        serde_json::from_str(&format!(
            r#"{{"type":"diagnose","source_id":"s","host":"127.0.0.1","port":1,"database":"d",
                "user":"u","password":"p",{tls_root_cert}"budget":{{{BUDGET}}}}}"#
        ))
        .unwrap()
    }

    #[test]
    fn no_root_certificate_keeps_the_session_plaintext_and_the_password() {
        let mut password = String::from("secret");
        let session_tls = session_tls_or_report(None, &mut password).unwrap();
        assert!(matches!(session_tls, Some(tls::SessionTls::Plain)));
        assert_eq!(password, "secret");
    }

    #[test]
    fn an_unusable_root_certificate_is_reported_and_zeroes_the_password() {
        let mut password = String::from("secret");
        let session_tls =
            session_tls_or_report(Some("/nonexistent/execplane-root.pem"), &mut password).unwrap();
        assert!(session_tls.is_none());
        assert!(password.is_empty());
    }

    #[test]
    fn a_diagnostic_with_an_unusable_root_certificate_fails_before_connecting() {
        let envelope = diagnose_envelope(r#""tls_root_cert":"/nonexistent/execplane-root.pem","#);
        assert_eq!(diagnose(envelope).unwrap(), 1);
    }

    #[test]
    fn a_plaintext_diagnostic_of_an_unreachable_server_fails() {
        assert_eq!(diagnose(diagnose_envelope("")).unwrap(), 1);
    }

    #[test]
    fn an_acquisition_with_an_unusable_root_certificate_fails_before_connecting() {
        let envelope: AcquireEnvelope = serde_json::from_str(&format!(
            r#"{{"type":"acquire","source_id":"s","host":"127.0.0.1","port":1,"database":"d",
                "user":"u","password":"p","municipality_ibge":"1100015","pec_version":"5.5.28",
                "read_model":"r","installation_role":"i","extraction_id":"e",
                "period_start":"2026-01-01","period_end_exclusive":"2026-02-01",
                "source_zone_id":"z","extract_temp_path":"/nonexistent/extract",
                "query_checksum":"c","adapter_version":"a",
                "tls_root_cert":"/nonexistent/execplane-root.pem",
                "budget":{{{BUDGET},"acquisition_timeout_ms":5,"max_rows":6,
                "max_payload_bytes":7,"max_temp_file_bytes":8}}}}"#
        ))
        .unwrap();
        assert_eq!(acquire(envelope, io::stdin().lock().lines()).unwrap(), 1);
    }

    /// The handshake's query checksum must equal the matrix's on every build host. A CRLF
    /// checkout (Git on Windows before .gitattributes pinned contracts/ to LF) changed these bytes
    /// and made every live acquisition fail closed with "query checksum mismatch" (ADR 0014).
    #[test]
    fn embedded_query_matches_the_matrix_checksum() {
        let matrix: serde_json::Value = serde_json::from_str(include_str!(
            "../../../contracts/compatibility/pec-adapters.json"
        ))
        .unwrap();
        let checksum = format!(
            "sha256:{}",
            hex_encode(Sha256::digest(QUERY_TEXT.as_bytes()))
        );
        for entry in matrix["tested_with"].as_array().unwrap() {
            assert_eq!(entry["query_checksum"], checksum.as_str());
        }
    }
}
