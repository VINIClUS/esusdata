use crate::aggregate::{self, Aggregate};
use crate::envelope::AggregateEnvelope;
use crate::hex_encode;
use serde_json::json;
use sha2::{Digest, Sha256};
use std::error::Error;
use std::io;

pub const CAPABILITY: &str = "period_coverage";

/// Byte-identical to the frozen query Java loads from the same file; its SHA-256 goes in the probe
/// handshake exactly like the acquisition query's.
pub const QUERY_TEXT: &str =
    include_str!("../../../contracts/compatibility/queries/period_coverage@0.1.0.sql");

const COVERAGE: Aggregate<(Option<String>, String, i64)> = Aggregate {
    capability: CAPABILITY,
    query_text: QUERY_TEXT,
    query_checksum,
    read_row: |row| (row.get(0), row.get(1), row.get(2)),
    message: coverage_message,
    rows_are: "(municipality code, competência) pairs",
};

/// `POST /sources/{id}/coverage-check` (ADR 0027): which competências of a window hold
/// atendimentos, counted per `co_ibge` and month, answered with `coverage`. Like the isolation
/// check it takes no municipality: Java keeps the registered one's months.
pub fn check_coverage(
    envelope: AggregateEnvelope,
    lines: io::Lines<io::StdinLock<'static>>,
) -> Result<i32, Box<dyn Error>> {
    aggregate::run(&COVERAGE, envelope, lines)
}

/// The terminal `coverage` message: one count per distinct (`co_ibge`, `yyyy-MM`) of the window.
fn coverage_message(counts: &[(Option<String>, String, i64)]) -> serde_json::Value {
    let counts: Vec<serde_json::Value> = counts
        .iter()
        .map(|(ibge, period, count)| json!({ "ibge": ibge, "period": period, "count": count }))
        .collect();
    json!({ "type": "coverage", "counts": counts })
}

pub fn query_checksum() -> String {
    format!(
        "sha256:{}",
        hex_encode(Sha256::digest(QUERY_TEXT.as_bytes()))
    )
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::aggregate::tests::envelope;
    use crate::matrix_checksum_tests::assert_matrix_checksum;
    use std::io::BufRead;

    #[test]
    fn a_check_of_an_unreachable_server_fails() {
        assert_eq!(
            check_coverage(envelope("check_coverage", ""), io::stdin().lock().lines()).unwrap(),
            1
        );
    }

    #[test]
    fn a_malformed_window_fails_before_connecting() {
        let mut envelope = envelope("check_coverage", "");
        envelope.period_end_exclusive = "2026-02-01".to_string();
        assert!(check_coverage(envelope, io::stdin().lock().lines()).is_err());
    }

    #[test]
    fn counts_become_the_coverage_message_with_null_codes_kept() {
        let message = coverage_message(&[
            (Some("3541307".to_string()), "2026-03".to_string(), 10_029),
            (None, "2026-03".to_string(), 2),
        ]);
        assert_eq!(
            message,
            json!({"type": "coverage", "counts": [
                {"ibge": "3541307", "period": "2026-03", "count": 10_029},
                {"ibge": null, "period": "2026-03", "count": 2},
            ]})
        );
    }

    #[test]
    fn the_query_binds_only_the_window() {
        assert_eq!(
            crate::stream::to_positional_placeholders(QUERY_TEXT)
                .matches('$')
                .count(),
            2
        );
        assert!(!QUERY_TEXT.contains("co_ibge ="));
    }

    #[test]
    fn embedded_query_matches_the_matrix_checksum() {
        assert_matrix_checksum(CAPABILITY, &query_checksum());
    }
}
