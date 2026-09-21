mod envelope;
mod matrix;
mod probe;
mod stream;

use envelope::AcquireEnvelope;
use postgres::{Config, IsolationLevel, NoTls};
use serde_json::{json, Map};
use sha2::{Digest, Sha256};
use std::error::Error;
use std::io::{self, BufRead, Write};
use std::str::FromStr;
use std::time::Duration;
use zeroize::Zeroize;

const CAPABILITY: &str = "individual_encounter_modality";

/// Byte-identical to the frozen query Java loads from the same file (plan §2.3). Its SHA-256 is
/// reported in the probe handshake; `stream::stream_query` converts it to `$1,$2,$3` placeholders
/// before executing it (the native wire protocol doesn't understand JDBC's `?`), but the checksum
/// below is always computed from these unconverted bytes.
const QUERY_TEXT: &str =
    include_str!("../../../contracts/compatibility/queries/individual_encounter_modality@0.1.0.sql");

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

    let acquire_line = lines
        .next()
        .ok_or("stdin closed before an acquire envelope was received")??;
    let mut envelope: AcquireEnvelope = serde_json::from_str(&acquire_line)?;

    let mut config = Config::new();
    config
        .host(&envelope.host)
        .port(envelope.port)
        .dbname(&envelope.database)
        .user(&envelope.user)
        .password(envelope.password.as_bytes())
        .connect_timeout(Duration::from_millis(envelope.budget.connect_timeout_ms.max(0) as u64));
    let connect_result = config.connect(NoTls);
    // Zeroed regardless of outcome — mirrors PecDataSourceFactory/writeAcquireEnvelope's own
    // finally-block zeroing on both sides of this same secret.
    envelope.password.zeroize();
    let mut client = connect_result?;
    // Captured before any &mut borrow of client (e.g. build_transaction()) makes that
    // impossible — CancelToken is independent of the connection it was derived from and stays
    // usable from another thread for as long as the process runs (plan §2.7's cancellation path).
    let cancel_token = client.cancel_token();

    // Same session GUCs as PecDataSourceFactory's connectionInitSql, byte-identical
    // application_name so the child is findable in pg_stat_activity under the name operators are
    // told to look for.
    client.batch_execute(&format!(
        "SET application_name = 'observatorio-aps'; \
         SET default_transaction_read_only = on; \
         SET statement_timeout = {}; \
         SET lock_timeout = {}; \
         SET idle_in_transaction_session_timeout = {};",
        envelope.budget.statement_timeout_ms,
        envelope.budget.lock_timeout_ms,
        envelope.budget.idle_in_transaction_timeout_ms,
    ))?;

    // ENG-43: the probe and (in the next slice) the frozen query itself run inside one
    // read-only repeatable-read transaction — the same snapshot, never reopened. This
    // transaction is deliberately kept open past the probe: it only ends via explicit
    // rollback below (abort/not-yet-implemented) or, in the next slice, after streaming.
    let mut txn = client
        .build_transaction()
        .read_only(true)
        .isolation_level(IsolationLevel::RepeatableRead)
        .start()?;

    let postgres_version: String = txn
        .query_one("SELECT current_setting('server_version')", &[])?
        .get::<_, String>(0)
        .trim()
        .to_string();

    let objects = matrix::objects_to_probe(
        CAPABILITY,
        &envelope.adapter_version,
        &envelope.pec_version,
        &envelope.read_model,
        &envelope.installation_role,
    );

    let mut objects_json = Map::new();
    for object in &objects {
        let probed = probe::probe_object(&mut txn, &object.object, &object.columns_used)?;
        objects_json.insert(object.object.clone(), probed);
    }

    let query_checksum = format!("sha256:{}", hex_encode(Sha256::digest(QUERY_TEXT.as_bytes())));
    write_line(&json!({
        "type": "probe",
        "postgres_version": postgres_version,
        "query_checksum": query_checksum,
        "objects": objects_json,
    }))?;

    let decision = lines.next().ok_or("stdin closed before a decision was received")??;
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
    let budget = stream::Budget {
        max_rows: envelope.budget.max_rows,
        max_duration_ms: envelope.budget.max_duration_ms,
        max_payload_bytes: envelope.budget.max_payload_bytes,
    };

    let outcome = stream::stream_query(
        &mut txn,
        cancel_token,
        QUERY_TEXT,
        &envelope.source_id,
        &envelope.municipality_ibge,
        period_start,
        period_end_exclusive,
        &budget,
    )?;

    let exit_code = match outcome {
        stream::StreamOutcome::Success => {
            end_transaction(txn.commit());
            0
        }
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
        stream::StreamOutcome::Failed(detail) => {
            write_line(&json!({
                "type": "error", "code": "UNCLASSIFIED_ERROR", "detail": detail, "uncertain": true,
            }))?;
            end_transaction(txn.rollback());
            1
        }
    };
    Ok(exit_code)
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
    bytes.as_ref().iter().map(|b| format!("{b:02x}")).collect()
}
