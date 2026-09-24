use postgres::Transaction;
use serde_json::{json, Map, Value};
use std::error::Error;

/// Ports `JdbcCompatibilityCatalog`'s raw-data measurement, marker by marker, over this crate's
/// own connection. This never computes a fingerprint (that algorithm lives exactly once, in
/// Java's `CompatibilityFingerprint` — plan §1.3/§2.2/ADR 0010): it only reports what it measured,
/// in the shape `SubprocessAcquisitionAdapter.buildProbeResult` expects to read back.
pub fn probe_object(
    txn: &mut Transaction,
    object: &str,
    columns_used: &[String],
) -> Result<Value, Box<dyn Error>> {
    require_safe_identifier(object)?;
    let columns = fetch_columns(txn, object)?;

    let mut fields = Map::new();
    fields.insert("columns".to_string(), Value::Array(columns));

    for marker in columns_used {
        if let Some(csv) = marker.strip_prefix("UNIQUE_KEY=") {
            let (matched_type, violation) = probe_unique_key(txn, object, csv)?;
            fields.insert(
                "unique_key".to_string(),
                json!({
                    "matched_constraint_type": matched_type,
                    "uniqueness_violation_found": violation,
                }),
            );
        } else if marker.starts_with("REQUIRED_DIMENSIONS=") {
            let violating = probe_required_dimensions(txn)?;
            fields.insert(
                "required_dimensions".to_string(),
                json!({ "violating_fact_event_id": violating }),
            );
        } else if let Some(csv) = marker.strip_prefix("LEAF_SEMANTICS=") {
            let rows = probe_leaf_semantics(txn, csv)?;
            fields.insert("leaf_semantics".to_string(), json!({ "rows": rows }));
        } else if let Some(csv) = marker.strip_prefix("LEAF_IDS=") {
            let found = probe_leaf_ids(txn, csv)?;
            fields.insert("leaf_ids".to_string(), json!({ "found_ids": found }));
        }
        // A plain column name needs nothing beyond "columns" — CompatibilityFingerprint looks it
        // up there by name (plan finding: the map is looked up by key, never iterated in order).
    }

    Ok(Value::Object(fields))
}

const COLUMNS_QUERY: &str =
    "SELECT column_name, data_type, udt_name, is_nullable, ordinal_position \
     FROM information_schema.columns \
    WHERE table_schema = 'public' AND table_name = $1 \
    ORDER BY ordinal_position";

fn fetch_columns(txn: &mut Transaction, object: &str) -> Result<Vec<Value>, Box<dyn Error>> {
    let rows = txn.query(COLUMNS_QUERY, &[&object])?;
    Ok(rows
        .iter()
        .map(|row| {
            let name: String = row.get(0);
            let data_type: String = row.get(1);
            let udt_name: String = row.get(2);
            let is_nullable: String = row.get(3);
            let ordinal_position: i32 = row.get(4);
            json!({
                "name": name,
                "data_type": data_type,
                "udt_name": udt_name,
                "is_nullable": is_nullable,
                "ordinal_position": ordinal_position,
            })
        })
        .collect())
}

const CONSTRAINTS_QUERY: &str =
    "SELECT tc.constraint_name, tc.constraint_type, kcu.column_name, kcu.ordinal_position \
      FROM information_schema.table_constraints tc \
      JOIN information_schema.key_column_usage kcu \
        ON kcu.constraint_schema = tc.constraint_schema \
       AND kcu.constraint_name = tc.constraint_name \
       AND kcu.table_schema = tc.table_schema \
       AND kcu.table_name = tc.table_name \
     WHERE tc.table_schema = 'public' \
       AND tc.table_name = $1 \
       AND tc.constraint_type IN ('PRIMARY KEY', 'UNIQUE') \
     ORDER BY tc.constraint_name, kcu.ordinal_position";

/// Mirrors `JdbcCompatibilityCatalog.probeUniqueKey`: first looks for an existing PK/UNIQUE
/// constraint whose column list matches exactly (no data query needed); only runs the
/// GROUP BY/HAVING uniqueness probe when no such constraint exists.
fn probe_unique_key(
    txn: &mut Transaction,
    object: &str,
    expected_csv: &str,
) -> Result<(Option<String>, bool), Box<dyn Error>> {
    let expected: Vec<&str> = expected_csv.split(',').collect();
    let rows = txn.query(CONSTRAINTS_QUERY, &[&object])?;

    let mut constraints: Vec<(String, String, Vec<String>)> = Vec::new();
    for row in &rows {
        let name: String = row.get(0);
        let constraint_type: String = row.get(1);
        let column: String = row.get(2);
        match constraints.iter_mut().find(|(n, _, _)| n == &name) {
            Some(entry) => entry.2.push(column),
            None => constraints.push((name, constraint_type, vec![column])),
        }
    }

    for (_, constraint_type, columns) in &constraints {
        if columns
            .iter()
            .map(String::as_str)
            .eq(expected.iter().copied())
        {
            return Ok((Some(constraint_type.clone()), false));
        }
    }

    for column in &expected {
        require_safe_identifier(column)?;
    }
    let key_expression = expected.join(",");
    let null_checks = expected
        .iter()
        .map(|column| format!("{column} IS NULL"))
        .collect::<Vec<_>>()
        .join(" OR ");
    let uniqueness_query = format!(
        "SELECT {key_expression} FROM public.{object} GROUP BY {key_expression} \
         HAVING COUNT(*) > 1 OR {null_checks} LIMIT 1"
    );
    let violation = !txn.query(&uniqueness_query, &[])?.is_empty();
    Ok((None, violation))
}

/// Mirrors `JdbcCompatibilityCatalog.probeRequiredDimensions`: hardcoded to
/// `tb_fat_atendimento_individual`'s two required dimensions, exactly like the Java side — the
/// marker's argument is not parsed, on purpose (deriving the query from marker text would be a
/// behavior change the fingerprint doesn't authorize).
fn probe_required_dimensions(txn: &mut Transaction) -> Result<Option<i64>, Box<dyn Error>> {
    let rows = txn.query(
        "SELECT f.co_seq_fat_atd_ind \
           FROM public.tb_fat_atendimento_individual f \
           LEFT JOIN public.tb_dim_tempo t ON t.co_seq_dim_tempo = f.co_dim_tempo \
           LEFT JOIN public.tb_dim_municipio m ON m.co_seq_dim_municipio = f.co_dim_municipio \
          WHERE t.co_seq_dim_tempo IS NULL OR m.co_seq_dim_municipio IS NULL \
          LIMIT 1",
        &[],
    )?;
    Ok(rows.first().map(|row| row.get::<_, i64>(0)))
}

fn parse_ids(csv: &str) -> Result<Vec<i64>, Box<dyn Error>> {
    csv.split(',')
        .map(|value| {
            value
                .parse::<i64>()
                .map_err(|e| Box::<dyn Error>::from(e.to_string()))
        })
        .collect()
}

/// Mirrors `JdbcCompatibilityCatalog.probeLeafSemantics`.
fn probe_leaf_semantics(
    txn: &mut Transaction,
    ids_csv: &str,
) -> Result<Vec<Value>, Box<dyn Error>> {
    let ids = parse_ids(ids_csv)?;
    let rows = txn.query(
        "SELECT co_seq_dim_tipo_atendimento, ds_tipo_atendimento, co_dim_tipo_atendimento_pai \
           FROM public.tb_dim_tipo_atendimento \
          WHERE co_seq_dim_tipo_atendimento = ANY($1) \
          ORDER BY co_seq_dim_tipo_atendimento",
        &[&ids],
    )?;
    Ok(rows
        .iter()
        .map(|row| {
            let id: i64 = row.get(0);
            let description: String = row.get(1);
            let parent_id: Option<i64> = row.get(2);
            json!({ "id": id, "description": description, "parent_id": parent_id })
        })
        .collect())
}

/// Mirrors `JdbcCompatibilityCatalog.probeLeafIds`.
fn probe_leaf_ids(txn: &mut Transaction, ids_csv: &str) -> Result<Vec<i64>, Box<dyn Error>> {
    let ids = parse_ids(ids_csv)?;
    let rows = txn.query(
        "SELECT co_seq_dim_tipo_atendimento FROM public.tb_dim_tipo_atendimento \
          WHERE co_seq_dim_tipo_atendimento = ANY($1)",
        &[&ids],
    )?;
    Ok(rows.iter().map(|row| row.get::<_, i64>(0)).collect())
}

/// Mirrors the Java probe's own defense-in-depth check (`JdbcCompatibilityCatalog.fingerprint`
/// and `probeUniqueKey`) before any identifier is interpolated into SQL text — both sides read
/// these names from the same packaged, trusted `pec-adapters.json`, not from the wire, but the
/// check costs nothing and keeps the two implementations symmetric.
fn require_safe_identifier(identifier: &str) -> Result<(), Box<dyn Error>> {
    if !identifier
        .chars()
        .all(|c| c.is_ascii_alphanumeric() || c == '_')
        || identifier.is_empty()
    {
        return Err(format!("unsafe identifier in compatibility contract: {identifier}").into());
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn safe_identifiers_are_accepted() {
        assert!(require_safe_identifier("tb_fat_atendimento_individual").is_ok());
        assert!(require_safe_identifier("co_seq_fat_atd_ind").is_ok());
    }

    #[test]
    fn unsafe_identifiers_are_rejected() {
        assert!(require_safe_identifier("").is_err());
        assert!(require_safe_identifier("id; DROP TABLE x --").is_err());
        assert!(require_safe_identifier("id,other").is_err());
    }

    #[test]
    fn parse_ids_reads_a_csv_list() {
        assert_eq!(parse_ids("2,3,5,6,7").unwrap(), vec![2, 3, 5, 6, 7]);
    }

    #[test]
    fn parse_ids_rejects_non_numeric_input() {
        assert!(parse_ids("2,x,7").is_err());
    }
}
