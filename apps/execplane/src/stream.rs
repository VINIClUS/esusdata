use chrono::NaiveDate;
use postgres::fallible_iterator::FallibleIterator;
use postgres::{CancelToken, NoTls, Transaction};
use serde_json::json;
use std::error::Error;
use std::io::{self, BufRead, Write};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

const PROGRAMADO_IDS: [i64; 2] = [2, 3];
const ESPONTANEO_IDS: [i64; 3] = [5, 6, 7];
const PROGRESS_INTERVAL: i64 = 1000;

pub struct Budget {
    pub max_rows: i64,
    pub max_duration_ms: i64,
    pub max_payload_bytes: i64,
}

pub enum StreamOutcome {
    /// The extract's data file was written in full — `main.rs` still has to call
    /// `ExtractSink::finish` itself (this function only holds a `&mut` borrow, not ownership) and
    /// emit the terminal `complete` message before committing.
    Success,
    BudgetExceeded(String),
    Cancelled,
    Failed(String),
    /// A record failed `ExtractSink::write`'s port of `validateRecordForWrite` (out-of-scope
    /// `care_date`, blank required field, ...) — mirrors Java's `IllegalArgumentException` /
    /// `INVALID_EXTRACT_RECORD` (fatia 3 / ADR 0011).
    InvalidRecord(String),
}

/// Ports `IndividualEncounterModalityCapability.stream`'s row loop: runs the frozen query inside
/// the already-open, already-probed transaction, classifies each row via
/// `EncounterTypeMapping.classify` (hardcoded, same two id sets), and writes it through the
/// already-open `ExtractSink` — this process now owns the extract's data file in full (fatia 3 /
/// ADR 0011, superseding ADR 0010's "child never writes the extract" decision). A background
/// thread reads the rest of stdin for `{"type":"cancel"}` — or its own EOF, meaning the parent
/// died — and forwards it to PostgreSQL's own cancel signal, the equivalent of
/// `Statement.cancel()` (plan §2.7). A second background thread enforces max_duration_ms itself:
/// `tcp_user_timeout` only bounds unacknowledged *transmitted* data, so a connection that stays
/// fully acknowledged but never sends a response (server stalled, not blackholed) would otherwise
/// leave `rows.next()` blocked past the budget with nothing to interrupt it — this watchdog fires
/// the same cancel signal proactively once the deadline passes, whether or not the main thread is
/// currently blocked in a read. `max_rows`/`max_payload_bytes`/duration are this process's job
/// (plan §2.4/ADR 0010): it is the side actually reading rows off the wire.
#[allow(clippy::too_many_arguments)]
pub fn stream_query(
    txn: &mut Transaction,
    cancel_token: CancelToken,
    query_text: &str,
    source_id: &str,
    municipality_ibge: &str,
    period_start: NaiveDate,
    period_end_exclusive: NaiveDate,
    budget: &Budget,
    start: Instant,
    sink: &mut crate::extract::ExtractSink,
) -> Result<StreamOutcome, Box<dyn Error>> {
    let cancel_requested = Arc::new(AtomicBool::new(false));
    let duration_exceeded = Arc::new(AtomicBool::new(false));
    spawn_cancel_listener(cancel_token.clone(), Arc::clone(&cancel_requested));
    spawn_duration_watchdog(cancel_token, start, budget.max_duration_ms, Arc::clone(&duration_exceeded));

    let positional_query = to_positional_placeholders(query_text);
    let params: [&(dyn postgres::types::ToSql + Sync); 3] =
        [&municipality_ibge, &period_start, &period_end_exclusive];
    let mut rows = match txn.query_raw(positional_query.as_str(), params) {
        Ok(rows) => rows,
        Err(err) => return Ok(classify_failure(err, &cancel_requested, &duration_exceeded)),
    };

    let mut row_count: i64 = 0;
    let mut payload_bytes: i64 = 0;

    loop {
        let next = rows.next();
        let row = match next {
            Ok(Some(row)) => row,
            Ok(None) => {
                // Mirrors the JDBC path's own post-loop guard.checkDuration(): the fetch that
                // returns "no more rows" is itself a round trip and can be what pushes total
                // elapsed time past max_duration_ms, even though every row already seen was
                // within budget.
                if let Some(outcome) = check_duration(&start, budget) {
                    return Ok(outcome);
                }
                break;
            }
            Err(err) => {
                return Ok(classify_failure(err, &cancel_requested, &duration_exceeded));
            }
        };

        row_count += 1;
        if row_count > budget.max_rows {
            return Ok(StreamOutcome::BudgetExceeded(format!(
                "row ceiling exceeded: {row_count} > {}",
                budget.max_rows
            )));
        }
        if let Some(outcome) = check_duration(&start, budget) {
            return Ok(outcome);
        }

        let pk: i64 = row.get(0);
        let tipo_atendimento_id: i64 = row.get(1);
        let care_date: NaiveDate = row.get(2);
        let cnes: Option<String> = row.get(3);
        let ine: Option<String> = row.get(4);
        let cbo: Option<String> = row.get(5);
        let uuid_ficha: Option<String> = row.get(6);

        let care_date_str = care_date.format("%Y-%m-%d").to_string();
        payload_bytes += fixed_payload_bytes(&care_date_str);
        for field in [&cnes, &ine, &cbo, &uuid_ficha] {
            if let Some(value) = field {
                payload_bytes += value.len() as i64;
            }
        }
        if payload_bytes > budget.max_payload_bytes {
            return Ok(StreamOutcome::BudgetExceeded(format!(
                "payload byte ceiling exceeded: {payload_bytes} > {}",
                budget.max_payload_bytes
            )));
        }
        if let Some(outcome) = check_duration(&start, budget) {
            return Ok(outcome);
        }

        let modality = classify(tipo_atendimento_id);
        let encounter = crate::extract::Encounter {
            source_ref: crate::extract::SourceRef {
                source_id: source_id.to_string(),
                entity_type: "tb_fat_atendimento_individual".to_string(),
                record_id: pk.to_string(),
            },
            municipality_ibge: municipality_ibge.to_string(),
            care_date: care_date_str,
            modality,
            cnes,
            ine,
            cbo,
        };
        if let Err(err) = sink.write(&encounter) {
            return Ok(match err {
                crate::extract::ExtractError::InvalidRecord(detail) => StreamOutcome::InvalidRecord(detail),
                crate::extract::ExtractError::BudgetExceeded(detail) => StreamOutcome::BudgetExceeded(detail),
                crate::extract::ExtractError::Io(detail) => StreamOutcome::Failed(detail),
            });
        }

        if row_count % PROGRESS_INTERVAL == 0 {
            write_line(&json!({ "type": "progress" }))?;
        }
    }

    Ok(StreamOutcome::Success)
}

pub fn check_duration(start: &Instant, budget: &Budget) -> Option<StreamOutcome> {
    let elapsed_ms = start.elapsed().as_millis() as i64;
    if elapsed_ms > budget.max_duration_ms {
        return Some(StreamOutcome::BudgetExceeded(format!(
            "duration ceiling exceeded: {elapsed_ms}ms > {}ms",
            budget.max_duration_ms
        )));
    }
    None
}

/// PostgreSQL reports the same SQLSTATE (57014, QUERY_CANCELED) whether a client explicitly
/// cancels or `statement_timeout` simply expires — `cancel_requested` (set only by this
/// process's own cancel-listener thread) is what tells the two apart. An expired
/// `statement_timeout` is this process's own read budget being enforced server-side, not a
/// cooperative cancellation, so it must classify as `BudgetExceeded` — Java's `translate()` has
/// no mapping for `CANCELLED` outside of an actual `CancellationSignal`, and would otherwise
/// misfile a budget overrun as `UNCLASSIFIED_ERROR`.
fn classify_failure(
    err: postgres::Error,
    cancel_requested: &AtomicBool,
    duration_exceeded: &AtomicBool,
) -> StreamOutcome {
    // cancel_requested is checked ahead of duration_exceeded on purpose: if a user's explicit
    // cancel and the watchdog's deadline land at roughly the same moment, the caller's intent
    // wins the classification. The two aren't equivalent to Java's FailureClassifier — cancelled
    // and budget-exceeded carry different retry decisions — and the deadline will simply re-fire
    // if this particular cancel_query call is what actually lands.
    if let Some(db_error) = err.as_db_error() {
        if db_error.code() == &postgres::error::SqlState::QUERY_CANCELED {
            if cancel_requested.load(Ordering::SeqCst) {
                return StreamOutcome::Cancelled;
            }
            if duration_exceeded.load(Ordering::SeqCst) {
                return StreamOutcome::BudgetExceeded(
                    "duration ceiling exceeded (a stalled read was interrupted)".to_string(),
                );
            }
            return StreamOutcome::BudgetExceeded(
                "statement timeout exceeded on the source connection".to_string(),
            );
        }
        if db_error.code() == &postgres::error::SqlState::LOCK_NOT_AVAILABLE {
            return StreamOutcome::BudgetExceeded(
                "lock timeout exceeded on the source connection".to_string(),
            );
        }
    }
    // cancel_token.cancel_query() signals the backend over a separate connection — the main
    // connection can observe a transport-level failure (broken pipe, reset) instead of a clean
    // 57014 if that lands awkwardly. Either background thread's cancel is still classified as
    // its own trigger regardless of what shape the resulting error takes.
    if cancel_requested.load(Ordering::SeqCst) {
        return StreamOutcome::Cancelled;
    }
    if duration_exceeded.load(Ordering::SeqCst) {
        return StreamOutcome::BudgetExceeded(
            "duration ceiling exceeded (a stalled read was interrupted)".to_string(),
        );
    }
    StreamOutcome::Failed(err.to_string())
}

fn classify(tipo_atendimento_id: i64) -> &'static str {
    if PROGRAMADO_IDS.contains(&tipo_atendimento_id) {
        "PROGRAMADO"
    } else if ESPONTANEO_IDS.contains(&tipo_atendimento_id) {
        "ESPONTANEO"
    } else {
        "UNMAPPED"
    }
}

/// Mirrors `IndividualEncounterModalityCapability.fixedPayloadBytes`: the same fixed overhead
/// (pk long + tipoAtendimentoId int + nuAtendimento int, counted once per row regardless of
/// whether this process reports them individually) plus the care-date string's UTF-8 length.
fn fixed_payload_bytes(care_date_str: &str) -> i64 {
    8 + 4 + 4 + 4 + care_date_str.len() as i64
}

/// The frozen query text uses JDBC's `?` placeholder convention (pgJDBC rewrites it internally
/// before it ever reaches the wire); the native wire protocol this crate speaks needs `$1,$2,$3`.
/// This does not touch the bytes plan §2.3 checksums — that checksum is computed from
/// `QUERY_TEXT` directly, never from this converted copy, exactly mirroring what pgJDBC already
/// does invisibly to the same frozen text on the Java side.
fn to_positional_placeholders(query: &str) -> String {
    let mut result = String::with_capacity(query.len() + 8);
    let mut n = 0;
    for c in query.chars() {
        if c == '?' {
            n += 1;
            result.push('$');
            result.push_str(&n.to_string());
        } else {
            result.push(c);
        }
    }
    result
}

fn spawn_cancel_listener(cancel_token: CancelToken, cancel_requested: Arc<AtomicBool>) {
    std::thread::spawn(move || {
        let stdin = io::stdin();
        for line in stdin.lock().lines().map_while(Result::ok) {
            if line.contains("\"type\":\"cancel\"") {
                cancel_requested.store(true, Ordering::SeqCst);
                let _ = cancel_token.cancel_query(NoTls);
                return;
            }
        }
        // stdin closed without an explicit cancel ever arriving — the parent process died. An
        // orphaned child must not keep reading the PEC (or holding the extract temp file open)
        // until max_duration_ms elapses on its own; EOF is the standard, reliable "parent is
        // gone" signal (plan §2.7's table: "o filho detecta EOF no stdin... e sai sozinho"), so it
        // gets exactly the same treatment as an explicit cancel.
        cancel_requested.store(true, Ordering::SeqCst);
        let _ = cancel_token.cancel_query(NoTls);
    });
}

/// Fires once, proactively, when max_duration_ms elapses — independent of whether the main
/// thread is currently making progress. If the query already finished (or the process already
/// exited) before the deadline, this either finds nothing left to cancel or never gets the chance
/// to run at all (`std::process::exit` tears down lingering threads with it).
fn spawn_duration_watchdog(
    cancel_token: CancelToken,
    start: Instant,
    max_duration_ms: i64,
    duration_exceeded: Arc<AtomicBool>,
) {
    let deadline = Duration::from_millis(max_duration_ms.max(0) as u64);
    std::thread::spawn(move || {
        let elapsed = start.elapsed();
        if elapsed < deadline {
            std::thread::sleep(deadline - elapsed);
        }
        duration_exceeded.store(true, Ordering::SeqCst);
        let _ = cancel_token.cancel_query(NoTls);
    });
}

fn write_line(value: &serde_json::Value) -> Result<(), Box<dyn Error>> {
    let mut stdout = io::stdout().lock();
    stdout.write_all(serde_json::to_string(value)?.as_bytes())?;
    stdout.write_all(b"\n")?;
    stdout.flush()?;
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn converts_jdbc_placeholders_to_positional_ones() {
        assert_eq!(to_positional_placeholders("a = ? AND b = ?"), "a = $1 AND b = $2");
    }

    #[test]
    fn classifies_known_and_unknown_leaf_ids() {
        assert_eq!(classify(2), "PROGRAMADO");
        assert_eq!(classify(3), "PROGRAMADO");
        assert_eq!(classify(5), "ESPONTANEO");
        assert_eq!(classify(6), "ESPONTANEO");
        assert_eq!(classify(7), "ESPONTANEO");
        assert_eq!(classify(8), "UNMAPPED");
        assert_eq!(classify(99), "UNMAPPED");
    }

    #[test]
    fn fixed_payload_bytes_matches_java_formula() {
        assert_eq!(fixed_payload_bytes("2026-03-05"), 8 + 4 + 4 + 4 + 10);
    }
}
