use crate::envelope::AggregateEnvelope;
use crate::{
    connect_session, end_transaction, matrix, report_pre_probe_failure, run_probes,
    session_tls_or_report, stream, write_line,
};
use chrono::NaiveDate;
use postgres::fallible_iterator::FallibleIterator;
use postgres::{IsolationLevel, Row};
use serde_json::json;
use std::error::Error;
use std::io;
use std::str::FromStr;
use std::time::Instant;

/// One aggregate read of the PEC outside an acquisition (ADR 0023, ADR 0027): which capability it
/// is, its frozen query, how a result row is read and which terminal message carries the rows.
pub struct Aggregate<T> {
    pub capability: &'static str,
    pub query_text: &'static str,
    pub query_checksum: fn() -> String,
    pub read_row: fn(&Row) -> T,
    pub message: fn(&[T]) -> serde_json::Value,
    /// What one row is, for the row-ceiling error ("municipality codes").
    pub rows_are: &'static str,
}

/// The acquisition's session and handshake — the same read-only repeatable-read transaction, the
/// same probes of the capability's matrix objects, Java's `proceed`/`abort` — then the one
/// aggregate query over `[period_start, period_end_exclusive)`. Success is the capability's
/// terminal message **and** exit 0. Nothing but aggregates ever leaves this process.
pub fn run<T>(
    aggregate: &Aggregate<T>,
    mut envelope: AggregateEnvelope,
    mut lines: io::Lines<io::StdinLock<'static>>,
) -> Result<i32, Box<dyn Error>> {
    // A malformed period fails before any session exists.
    let (period_start, period_end_exclusive) =
        period(&envelope.period_start, &envelope.period_end_exclusive)?;
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
        &envelope.budget.session,
    )?
    else {
        return Ok(1);
    };
    let mut txn = match client
        .build_transaction()
        .read_only(true)
        .isolation_level(IsolationLevel::RepeatableRead)
        .start()
    {
        Ok(txn) => txn,
        Err(err) => return report_pre_probe_failure(&err),
    };

    let start = Instant::now();
    let budget = stream::Budget {
        max_rows: envelope.budget.max_rows,
        max_duration_ms: envelope.budget.session.max_duration_ms,
        max_payload_bytes: i64::MAX,
    };
    let objects = matrix::objects_to_probe(
        aggregate.capability,
        &envelope.adapter_version,
        &envelope.pec_version,
        &envelope.read_model,
        &envelope.installation_role,
    );
    let (postgres_version, objects_json) = match run_probes(&mut txn, &objects, &start, &budget) {
        Ok(report) => report,
        Err(err) => {
            end_transaction(txn.rollback());
            return report_pre_probe_failure(err.as_ref());
        }
    };
    write_line(&json!({
        "type": "probe",
        "postgres_version": postgres_version,
        "query_checksum": (aggregate.query_checksum)(),
        "objects": objects_json,
    }))?;

    let decision = lines
        .next()
        .ok_or("stdin closed before a decision was received")??;
    match Decision::of(&decision) {
        Decision::Proceed => {}
        Decision::Abort => {
            end_transaction(txn.rollback());
            return Ok(1);
        }
        Decision::Unexpected => {
            eprintln!("observatorio-execplane: expected 'proceed' or 'abort', got: {decision}");
            end_transaction(txn.rollback());
            return Ok(3);
        }
    }

    // Streamed and cut at max_rows + 1, like the acquisition: the row ceiling bounds what is read
    // off the wire, not only what is reported.
    let params: [&(dyn postgres::types::ToSql + Sync); 2] = [&period_start, &period_end_exclusive];
    let fetched = txn
        .query_raw(
            stream::to_positional_placeholders(aggregate.query_text).as_str(),
            params,
        )
        .and_then(|rows| {
            take_bounded(
                rows.iterator()
                    .map(|row| row.map(|row| (aggregate.read_row)(&row))),
                budget.max_rows,
            )
        });
    end_transaction(txn.rollback());
    let rows = match fetched {
        Ok(Bounded::Within(rows)) => rows,
        Ok(Bounded::Exceeded) => {
            return report_budget_exceeded(&format!(
                "row ceiling exceeded: more than {} {}",
                budget.max_rows, aggregate.rows_are
            ))
        }
        Err(err) => return report_pre_probe_failure(&err),
    };
    if let Some(stream::StreamOutcome::BudgetExceeded(detail)) =
        stream::check_duration(&start, &budget)
    {
        return report_budget_exceeded(&detail);
    }
    write_line(&(aggregate.message)(&rows))?;
    Ok(0)
}

#[derive(Debug, PartialEq, Eq)]
enum Bounded<T> {
    Within(Vec<T>),
    Exceeded,
}

/// Reads at most `max_rows + 1` items: one past the ceiling is enough to know it was exceeded.
fn take_bounded<T, E>(
    rows: impl Iterator<Item = Result<T, E>>,
    max_rows: i64,
) -> Result<Bounded<T>, E> {
    let mut taken = Vec::new();
    for row in rows {
        if i64::try_from(taken.len()).unwrap_or(i64::MAX) >= max_rows {
            return Ok(Bounded::Exceeded);
        }
        taken.push(row?);
    }
    Ok(Bounded::Within(taken))
}

fn period(start: &str, end_exclusive: &str) -> Result<(NaiveDate, NaiveDate), Box<dyn Error>> {
    let start = NaiveDate::from_str(start)?;
    let end_exclusive = NaiveDate::from_str(end_exclusive)?;
    if end_exclusive <= start {
        return Err(format!("empty period: {start} to {end_exclusive}").into());
    }
    Ok((start, end_exclusive))
}

/// Java's answer to the `probe` message.
#[derive(Debug, PartialEq, Eq)]
enum Decision {
    Proceed,
    Abort,
    Unexpected,
}

impl Decision {
    fn of(line: &str) -> Self {
        if line.contains("\"type\":\"abort\"") {
            Self::Abort
        } else if line.contains("\"type\":\"proceed\"") {
            Self::Proceed
        } else {
            Self::Unexpected
        }
    }
}

fn report_budget_exceeded(detail: &str) -> Result<i32, Box<dyn Error>> {
    write_line(&json!({
        "type": "error", "code": "SOURCE_BUDGET_EXCEEDED", "detail": detail, "uncertain": true,
    }))?;
    Ok(1)
}

#[cfg(test)]
pub mod tests {
    use super::*;

    /// A check envelope of either kind, as Java writes it.
    pub fn envelope(message_type: &str, tls_root_cert: &str) -> AggregateEnvelope {
        serde_json::from_str(&format!(
            r#"{{"type":"{message_type}","source_id":"s","host":"127.0.0.1","port":1,
                "database":"d","user":"u","password":"p","pec_version":"5.5.28",
                "read_model":"PEC_DW","installation_role":"PRONTUARIO","adapter_version":"0.1.0",
                "period_start":"2026-03-01","period_end_exclusive":"2026-04-01",{tls_root_cert}
                "budget":{{"connect_timeout_ms":2000,"statement_timeout_ms":1,"lock_timeout_ms":2,
                "idle_in_transaction_timeout_ms":3,"max_duration_ms":4,"max_rows":5}}}}"#
        ))
        .unwrap()
    }

    #[test]
    fn the_period_is_parsed_and_must_not_be_empty() {
        assert_eq!(
            period("2026-03-01", "2026-04-01").unwrap(),
            (
                NaiveDate::from_ymd_opt(2026, 3, 1).unwrap(),
                NaiveDate::from_ymd_opt(2026, 4, 1).unwrap()
            )
        );
        assert!(period("2026-03-01", "2026-03-01").is_err());
        assert!(period("03/2026", "2026-04-01").is_err());
    }

    #[test]
    fn only_proceed_lets_the_query_run() {
        assert_eq!(Decision::of(r#"{"type":"proceed"}"#), Decision::Proceed);
        assert_eq!(
            Decision::of(r#"{"type":"abort","code":"COMPATIBILITY_MISMATCH"}"#),
            Decision::Abort
        );
        assert_eq!(Decision::of(r#"{"type":"cancel"}"#), Decision::Unexpected);
    }

    #[test]
    fn reading_stops_one_row_past_the_ceiling() {
        let mut pulled = 0;
        let rows = (0..1_000_000).map(|n| {
            pulled += 1;
            Ok::<i64, ()>(n)
        });
        assert_eq!(take_bounded(rows, 2), Ok(Bounded::Exceeded));
        assert_eq!(pulled, 3);
    }

    #[test]
    fn rows_within_the_ceiling_are_all_kept() {
        let rows = vec![Ok::<i64, ()>(1), Ok(2)].into_iter();
        assert_eq!(take_bounded(rows, 2), Ok(Bounded::Within(vec![1, 2])));
    }

    #[test]
    fn a_failed_row_is_the_error() {
        let rows = vec![Ok(1), Err("broken")].into_iter();
        assert_eq!(take_bounded(rows, 5), Err("broken"));
    }
}
