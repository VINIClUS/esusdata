//! The canonical v2 capabilities this binary can read (ADR 0030). Each one is a frozen query and
//! the descriptor that types it — `contracts/compatibility/capabilities/<id>@<version>.json`,
//! schema `capabilities.schema.json` — compiled in with `include_str!` from the very files Java's
//! `CapabilityCatalog` packages, so both sides read one contract. Nothing here depends on what the
//! query says: its checksum is computed from its bytes, and its binds and columns come from the
//! descriptor ("a SQL é o esquema").

use crate::envelope::PartRequest;
use crate::hex_encode;
use serde::Deserialize;
use sha2::{Digest, Sha256};

/// Reported for a part that does not name a compiled-in capability exactly — Java maps it to
/// `INVALID_REQUEST`.
pub const UNKNOWN_CAPABILITY: &str = "UNKNOWN_CAPABILITY";

/// One capability as compiled into this binary: its descriptor and its frozen query, byte for byte.
pub struct Packaged {
    pub id: &'static str,
    pub descriptor_json: &'static str,
    pub query_text: &'static str,
}

/// `include_str!` needs a literal path; this keeps the ten entries below one line each.
macro_rules! packaged {
    ($id:literal) => {
        Packaged {
            id: $id,
            descriptor_json: include_str!(concat!(
                "../../../contracts/compatibility/capabilities/",
                $id,
                "@0.1.0.json"
            )),
            query_text: include_str!(concat!(
                "../../../contracts/compatibility/queries/",
                $id,
                "@0.1.0.sql"
            )),
        }
    };
}

/// The foundation's capabilities, as `contracts/compatibility/capabilities/index.json` lists them.
pub static REGISTRY: [Packaged; 10] = [
    packaged!("care_encounter"),
    packaged!("citizen"),
    packaged!("condition_list"),
    packaged!("dental_encounter"),
    packaged!("exam_request_evaluation"),
    packaged!("home_visit"),
    packaged!("immunization_history"),
    packaged!("individual_registration"),
    packaged!("measurement_record"),
    packaged!("procedure_performed"),
];

/// A capability descriptor (`capabilities.schema.json`); `description` is for people, not read.
#[derive(Debug, Deserialize)]
pub struct Descriptor {
    pub capability: String,
    pub adapter_version: String,
    pub record_kind: String,
    pub entity_type_column: String,
    pub record_id_column: String,
    pub municipality_column: String,
    pub scope_date_column: Option<String>,
    pub query: String,
    pub binds: Vec<Bind>,
    pub columns: Vec<Column>,
}

/// One positional bind, in the order the query's `?` placeholders consume them.
#[derive(Debug, Deserialize)]
pub struct Bind {
    pub name: String,
    #[serde(rename = "type")]
    pub kind: BindKind,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "SCREAMING_SNAKE_CASE")]
pub enum BindKind {
    /// The envelope's municipality.
    MunicipalityIbge,
    /// The part's first day, inclusive.
    PeriodStart,
    /// The part's first day not read.
    PeriodEndExclusive,
    /// `date_params[name]`.
    Date,
    /// `array_params[name]`.
    TextArray,
}

/// One output column: the record field of the same name.
#[derive(Debug, Deserialize)]
pub struct Column {
    pub name: String,
    #[serde(rename = "type")]
    pub kind: ColumnKind,
    pub required: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
pub enum ColumnKind {
    #[serde(rename = "text")]
    Text,
    #[serde(rename = "date")]
    Date,
    #[serde(rename = "integer")]
    Integer,
    /// A canonical decimal string (§1.7.1): the query casts `numeric` to text itself.
    #[serde(rename = "decimal")]
    Decimal,
    #[serde(rename = "bool")]
    Bool,
    #[serde(rename = "text[]")]
    TextArray,
}

impl Descriptor {
    pub fn column(&self, name: &str) -> Option<&Column> {
        self.columns.iter().find(|column| column.name == name)
    }

    /// What the schema cannot say on its own, and every read relies on: the descriptor names its
    /// own capability and query file, its columns and binds are unique, and the key columns exist
    /// and are required — the scope date (if any) being a date.
    fn check(&self, id: &str) -> Result<(), String> {
        if self.capability != id {
            return Err(format!("descriptor of {id} names {}", self.capability));
        }
        let query = format!("queries/{}@{}.sql", self.capability, self.adapter_version);
        if self.query != query {
            return Err(format!(
                "descriptor of {id} points at {}, not {query}",
                self.query
            ));
        }
        if let Some(name) = first_duplicate(self.columns.iter().map(|c| c.name.as_str())) {
            return Err(format!("descriptor of {id} lists column {name} twice"));
        }
        if let Some(name) = first_duplicate(self.binds.iter().map(|b| b.name.as_str())) {
            return Err(format!("descriptor of {id} lists bind {name} twice"));
        }
        for key in [
            &self.entity_type_column,
            &self.record_id_column,
            &self.municipality_column,
        ] {
            if !self.column(key).is_some_and(|column| column.required) {
                return Err(format!(
                    "descriptor of {id}: key column {key} is not a required column"
                ));
            }
        }
        if let Some(scope) = &self.scope_date_column {
            if !self
                .column(scope)
                .is_some_and(|column| column.kind == ColumnKind::Date)
            {
                return Err(format!(
                    "descriptor of {id}: scope date column {scope} is not a date column"
                ));
            }
        }
        Ok(())
    }
}

fn first_duplicate<'a>(names: impl Iterator<Item = &'a str>) -> Option<&'a str> {
    let mut seen = std::collections::HashSet::new();
    names.into_iter().find(|name| !seen.insert(*name))
}

/// A capability ready to be read: its descriptor, its frozen query and that query's checksum.
#[derive(Debug)]
pub struct Capability {
    pub descriptor: Descriptor,
    pub query_text: &'static str,
    pub query_checksum: String,
}

impl Packaged {
    /// Parses the descriptor with serde and checksums the query — the same `sha256:` + hex of the
    /// UTF-8 bytes Java's `FrozenQuery.checksum` computes for the matrix and the manifest.
    pub fn load(&self) -> Result<Capability, String> {
        let descriptor: Descriptor = serde_json::from_str(self.descriptor_json)
            .map_err(|e| format!("descriptor of {} is malformed: {e}", self.id))?;
        descriptor.check(self.id)?;
        Ok(Capability {
            descriptor,
            query_text: self.query_text,
            query_checksum: query_checksum(self.query_text),
        })
    }
}

pub fn query_checksum(query_text: &str) -> String {
    format!(
        "sha256:{}",
        hex_encode(Sha256::digest(query_text.as_bytes()))
    )
}

/// The compiled-in capability a part asks for — only if it is exactly the one Java expects: the
/// same id, adapter version, record kind and query checksum. Anything else is refused with
/// `UNKNOWN_CAPABILITY` before any connection exists; a mismatch here means the two sides were
/// built from different contracts, and reading anyway would put a different query's rows under the
/// part Java will publish.
pub fn resolve(part: &PartRequest) -> Result<Capability, String> {
    let packaged = REGISTRY
        .iter()
        .find(|packaged| packaged.id == part.capability)
        .ok_or_else(|| {
            format!(
                "capability {} is not compiled into this execution plane",
                part.capability
            )
        })?;
    let capability = packaged.load()?;
    let descriptor = &capability.descriptor;
    if descriptor.adapter_version != part.adapter_version {
        return Err(format!(
            "{}@{} was requested but this execution plane has {}@{}",
            part.capability,
            part.adapter_version,
            descriptor.capability,
            descriptor.adapter_version
        ));
    }
    if descriptor.record_kind != part.record_kind {
        return Err(format!(
            "{} writes {} records, not {}",
            part.capability, descriptor.record_kind, part.record_kind
        ));
    }
    if capability.query_checksum != part.query_checksum {
        return Err(format!(
            "{} query checksum is {}, not the requested {}",
            part.capability, capability.query_checksum, part.query_checksum
        ));
    }
    Ok(capability)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::collections::BTreeMap;

    fn request(capability: &str, adapter_version: &str, record_kind: &str) -> PartRequest {
        let checksum = REGISTRY.iter().find(|p| p.id == capability).map_or_else(
            || "sha256:none".to_string(),
            |p| query_checksum(p.query_text),
        );
        PartRequest {
            capability: capability.to_string(),
            adapter_version: adapter_version.to_string(),
            query_checksum: checksum,
            record_kind: record_kind.to_string(),
            period_start: "2026-01-01".to_string(),
            period_end_exclusive: "2026-02-01".to_string(),
            array_params: BTreeMap::new(),
            date_params: BTreeMap::new(),
        }
    }

    /// The registry is `capabilities/index.json`, entry for entry — a descriptor added there and
    /// not here (or the reverse) fails this test, not a run.
    #[test]
    fn the_registry_is_the_packaged_index() {
        let index: serde_json::Value = serde_json::from_str(include_str!(
            "../../../contracts/compatibility/capabilities/index.json"
        ))
        .unwrap();
        let listed: Vec<&str> = index["descriptors"]
            .as_array()
            .unwrap()
            .iter()
            .map(|file| file.as_str().unwrap())
            .collect();
        let compiled: Vec<String> = REGISTRY
            .iter()
            .map(|p| format!("{}@0.1.0.json", p.id))
            .collect();
        assert_eq!(listed, compiled);
    }

    #[test]
    fn every_descriptor_parses_and_names_itself() {
        for packaged in &REGISTRY {
            let capability = packaged.load().unwrap();
            assert_eq!(capability.descriptor.capability, packaged.id);
            assert_eq!(capability.descriptor.adapter_version, "0.1.0");
            assert!(capability.query_checksum.starts_with("sha256:"));
            assert_eq!(capability.query_checksum.len(), "sha256:".len() + 64);
        }
    }

    /// The `?`s of `sql` inside a comment or a quoted literal or identifier — which
    /// `to_positional_placeholders` would still turn into `$n`, shifting every later bind.
    fn misplaced_placeholders(sql: &str) -> usize {
        let chars: Vec<char> = sql.chars().collect();
        let mut misplaced = 0;
        let mut i = 0;
        while i < chars.len() {
            let end = match (chars[i], chars.get(i + 1)) {
                ('-', Some('-')) => (i..chars.len())
                    .find(|&j| chars[j] == '\n')
                    .unwrap_or(chars.len()),
                ('/', Some('*')) => (i + 2..chars.len())
                    .find(|&j| chars[j] == '*' && chars.get(j + 1) == Some(&'/'))
                    .map_or(chars.len(), |j| j + 1),
                (quote @ ('\'' | '"'), _) => (i + 1..chars.len())
                    .find(|&j| chars[j] == quote)
                    .unwrap_or(chars.len()),
                _ => {
                    i += 1;
                    continue;
                }
            };
            misplaced += chars[i..end.min(chars.len())]
                .iter()
                .filter(|&&c| c == '?')
                .count();
            i = end + 1;
        }
        misplaced
    }

    #[test]
    fn placeholders_in_comments_and_literals_are_found() {
        assert_eq!(misplaced_placeholders("SELECT ? -- why?\n AND ?"), 1);
        assert_eq!(
            misplaced_placeholders("/* a?b */ SELECT 'x?' AS \"y?\", ?"),
            3
        );
        assert_eq!(misplaced_placeholders("SELECT 'it''s' WHERE a = ?"), 0);
    }

    /// The query's `?` placeholders are its binds, one for one, and none hides in a comment or a
    /// literal: the conversion to `$n` counts every `?`, so one in prose would shift the binds.
    #[test]
    fn every_query_has_one_placeholder_per_bind_and_none_in_comments() {
        for packaged in &REGISTRY {
            let capability = packaged.load().unwrap();
            assert_eq!(
                misplaced_placeholders(packaged.query_text),
                0,
                "{}",
                packaged.id
            );
            assert_eq!(
                packaged.query_text.matches('?').count(),
                capability.descriptor.binds.len(),
                "{}",
                packaged.id
            );
        }
    }

    #[test]
    fn every_query_names_every_column_and_takes_the_municipality() {
        for packaged in &REGISTRY {
            let capability = packaged.load().unwrap();
            for column in &capability.descriptor.columns {
                assert!(
                    packaged.query_text.contains(&column.name),
                    "{}: {}",
                    packaged.id,
                    column.name
                );
            }
            assert!(capability
                .descriptor
                .binds
                .iter()
                .any(|bind| bind.kind == BindKind::MunicipalityIbge));
        }
    }

    #[test]
    fn a_requested_part_resolves_to_its_compiled_capability() {
        let capability = resolve(&request("condition_list", "0.1.0", "condition")).unwrap();
        let binds: Vec<(&str, BindKind)> = capability
            .descriptor
            .binds
            .iter()
            .map(|bind| (bind.name.as_str(), bind.kind))
            .collect();
        assert_eq!(
            binds,
            [
                ("municipality_ibge", BindKind::MunicipalityIbge),
                ("period_start", BindKind::PeriodStart),
                ("period_end_exclusive", BindKind::PeriodEndExclusive),
                ("birth_date_from", BindKind::Date),
                ("birth_date_to", BindKind::Date),
                ("ciap_codes", BindKind::TextArray),
                ("cid_codes", BindKind::TextArray),
            ]
        );
        assert_eq!(
            capability.descriptor.scope_date_column.as_deref(),
            Some("recorded_date")
        );
    }

    #[test]
    fn an_unknown_capability_is_refused() {
        let refused = resolve(&request(
            "individual_encounter_modality",
            "0.1.0",
            "care_event",
        ));
        assert!(refused.unwrap_err().contains("not compiled"));
    }

    #[test]
    fn another_adapter_version_is_refused() {
        let refused = resolve(&request("citizen", "0.2.0", "person"));
        assert!(refused.unwrap_err().contains("citizen@0.2.0"));
    }

    #[test]
    fn another_record_kind_is_refused() {
        let refused = resolve(&request("dental_encounter", "0.1.0", "procedure_event"));
        assert!(refused.unwrap_err().contains("writes care_event records"));
    }

    #[test]
    fn another_query_checksum_is_refused() {
        let mut part = request("home_visit", "0.1.0", "home_visit");
        part.query_checksum = format!("sha256:{}", "0".repeat(64));
        assert!(resolve(&part).unwrap_err().contains("query checksum"));
    }

    fn load(descriptor: &'static str) -> Result<Capability, String> {
        Packaged {
            id: "probe_test",
            descriptor_json: descriptor,
            query_text: "SELECT 1",
        }
        .load()
    }

    #[test]
    fn a_descriptor_must_name_its_own_query_and_keys() {
        let base = r#"{"capability":"probe_test","adapter_version":"0.1.0","description":"t",
            "record_kind":"person","entity_type_column":"e","record_id_column":"r",
            "municipality_column":"m","scope_date_column":SCOPE,"query":QUERY,
            "binds":[{"name":"m","type":"MUNICIPALITY_IBGE"}],
            "columns":[{"name":"e","type":"text","required":true},
                       {"name":"r","type":"text","required":true},
                       {"name":"m","type":"text","required":MREQ},
                       {"name":"d","type":"date","required":true}]}"#;
        let variant = |scope: &str, query: &str, municipality_required: &str| -> &'static str {
            base.replace("SCOPE", scope)
                .replace("QUERY", query)
                .replace("MREQ", municipality_required)
                .leak()
        };
        let good_query = r#""queries/probe_test@0.1.0.sql""#;
        assert!(load(variant(r#""d""#, good_query, "true")).is_ok());
        assert!(load(variant("null", good_query, "true")).is_ok());
        assert!(
            load(variant(r#""d""#, r#""queries/other@0.1.0.sql""#, "true"))
                .unwrap_err()
                .contains("points at")
        );
        assert!(load(variant(r#""d""#, good_query, "false"))
            .unwrap_err()
            .contains("key column m"));
        assert!(load(variant(r#""e""#, good_query, "true"))
            .unwrap_err()
            .contains("scope date column e"));
        assert!(load(
            variant(r#""d""#, good_query, "true")
                .replace("probe_test", "other")
                .leak()
        )
        .unwrap_err()
        .contains("names other"));
        assert!(load("{").unwrap_err().contains("malformed"));
    }

    #[test]
    fn a_descriptor_with_a_repeated_column_or_bind_is_refused() {
        let repeated_column = r#"{"capability":"probe_test","adapter_version":"0.1.0",
            "record_kind":"person","entity_type_column":"e","record_id_column":"e",
            "municipality_column":"e","scope_date_column":null,
            "query":"queries/probe_test@0.1.0.sql",
            "binds":[{"name":"m","type":"MUNICIPALITY_IBGE"}],
            "columns":[{"name":"e","type":"text","required":true},
                       {"name":"e","type":"text","required":true}]}"#;
        assert!(load(repeated_column)
            .unwrap_err()
            .contains("column e twice"));
        let repeated_bind = r#"{"capability":"probe_test","adapter_version":"0.1.0",
            "record_kind":"person","entity_type_column":"e","record_id_column":"e",
            "municipality_column":"e","scope_date_column":null,
            "query":"queries/probe_test@0.1.0.sql",
            "binds":[{"name":"m","type":"MUNICIPALITY_IBGE"},{"name":"m","type":"DATE"}],
            "columns":[{"name":"e","type":"text","required":true}]}"#;
        assert!(load(repeated_bind).unwrap_err().contains("bind m twice"));
    }
}
