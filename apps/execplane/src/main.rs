mod envelope;
mod matrix;
mod probe;

use envelope::AcquireEnvelope;
use postgres::{Config, IsolationLevel, NoTls};
use serde_json::{json, Map};
use sha2::{Digest, Sha256};
use std::error::Error;
use std::io::{self, BufRead, Write};
use std::time::Duration;
use zeroize::Zeroize;

const CAPABILITY: &str = "individual_encounter_modality";

/// Byte-identical to the frozen query Java loads from the same file (plan §2.3) — this binary
/// never runs it yet (that's the next slice), but its SHA-256 is part of the probe handshake.
const QUERY_TEXT: &str =
    include_str!("../../../contracts/compatibility/queries/individual_encounter_modality@0.1.0.sql");

fn main() {
    match run() {
        Ok(exit_code) => std::process::exit(exit_code),
        Err(err) => {
            eprintln!("observatorio-execplane: {err}");
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

    // Streaming the frozen query and writing "row" messages, inside this same txn, is the next
    // slice (plan §2.2/§2.6, superseded by ADR 0010 on writer ownership).
    eprintln!("observatorio-execplane: row streaming is not implemented yet");
    txn.rollback()?;
    Ok(3)
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
