use crate::aggregate::{self, Aggregate};
use crate::envelope::AggregateEnvelope;
use crate::hex_encode;
use serde_json::json;
use sha2::{Digest, Sha256};
use std::error::Error;
use std::io;

pub const CAPABILITY: &str = "municipal_isolation";

/// Byte-identical to the frozen query Java loads from the same file; its SHA-256 goes in the probe
/// handshake exactly like the acquisition query's.
pub const QUERY_TEXT: &str =
    include_str!("../../../contracts/compatibility/queries/municipal_isolation@0.1.0.sql");

const ISOLATION: Aggregate<(Option<String>, i64)> = Aggregate {
    capability: CAPABILITY,
    query_text: QUERY_TEXT,
    query_checksum,
    read_row: |row| (row.get(0), row.get(1)),
    message: isolation_message,
    rows_are: "municipality codes",
};

/// `POST /sources/{id}/isolation-check` (ADR 0023): one aggregate query that counts the
/// competência's atendimentos per `co_ibge`, answered with `isolation`.
pub fn check_isolation(
    envelope: AggregateEnvelope,
    lines: io::Lines<io::StdinLock<'static>>,
) -> Result<i32, Box<dyn Error>> {
    aggregate::run(&ISOLATION, envelope, lines)
}

/// The terminal `isolation` message: one count per distinct `co_ibge` of the period.
fn isolation_message(counts: &[(Option<String>, i64)]) -> serde_json::Value {
    let counts: Vec<serde_json::Value> = counts
        .iter()
        .map(|(ibge, count)| json!({ "ibge": ibge, "count": count }))
        .collect();
    json!({ "type": "isolation", "counts": counts })
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
    fn a_check_with_an_unusable_root_certificate_fails_before_connecting() {
        let envelope = envelope(
            "check_isolation",
            r#""tls_root_cert":"/nonexistent/execplane-root.pem","#,
        );
        assert_eq!(
            check_isolation(envelope, io::stdin().lock().lines()).unwrap(),
            1
        );
    }

    #[test]
    fn a_check_of_an_unreachable_server_fails() {
        assert_eq!(
            check_isolation(envelope("check_isolation", ""), io::stdin().lock().lines()).unwrap(),
            1
        );
    }

    #[test]
    fn a_malformed_period_fails_before_connecting() {
        let mut envelope = envelope("check_isolation", "");
        envelope.period_start = "not-a-date".to_string();
        assert!(check_isolation(envelope, io::stdin().lock().lines()).is_err());
    }

    #[test]
    fn counts_become_the_isolation_message_with_null_codes_kept() {
        let message = isolation_message(&[(Some("3541307".to_string()), 10_029), (None, 2)]);
        assert_eq!(
            message,
            json!({"type": "isolation", "counts": [
                {"ibge": "3541307", "count": 10_029},
                {"ibge": null, "count": 2},
            ]})
        );
    }

    #[test]
    fn the_query_binds_only_the_period() {
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
