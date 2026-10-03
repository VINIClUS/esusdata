//! The canonical extract v2 (ADR 0030): every capability an acquisition asks for, read part after
//! part inside the one read-only repeatable-read transaction its probe already ran in. Each row is
//! converted column by column, by its PostgreSQL type, into the `snake_case` record its descriptor
//! declares ("a SQL é o esquema"), checked against the part's scope, and written as
//! `{"part":i,"kind":"…","record":{…}}` through the same `ExtractSink` C1's v1 extract uses —
//! gzip, SHA-256, the byte ceilings. The row, payload and duration budgets are the acquisition's,
//! cumulative across parts. C1's v1 path (`main.rs` `acquire`, `stream::stream_query`) keeps its
//! own code; it shares the session, the handshake shape and the cancellation signals with this one.

use crate::aggregate::Decision;
use crate::capabilities::{self, BindKind, Capability, ColumnKind};
use crate::envelope::{AcquireEnvelope, PartRequest};
use crate::extract::{self, ExtractSink};
use crate::stream::{self, Budget, Interrupts, StreamOutcome};
use crate::{
    check_probe_duration, connect_session, end_transaction, extract_error_code_and_detail, matrix,
    probe, report_pre_probe_failure, session_tls_or_report, tls, write_line,
};
use chrono::NaiveDate;
use postgres::fallible_iterator::FallibleIterator;
use postgres::types::{ToSql, Type};
use postgres::{IsolationLevel, Row, Statement, Transaction};
use serde::ser::{SerializeMap, Serializer};
use serde::Serialize;
use serde_json::{json, Map, Value};
use std::collections::HashSet;
use std::error::Error;
use std::io;
use std::ops::ControlFlow;
use std::time::Instant;
use zeroize::Zeroize;

/// A part that does not fit its capability's contract — a bind without a value, a parameter the
/// query does not take, a window outside the acquisition's period. Refused before any connection
/// exists; Java maps it to `INVALID_REQUEST`.
pub const INVALID_REQUEST: &str = "INVALID_REQUEST";

/// A result column whose PostgreSQL type has no canonical conversion (`numeric`, `float8`,
/// `timestamp`, …) or whose shape is not the one its descriptor declares. The frozen query must
/// convert it itself — a decimal travels as text (§1.7.1).
pub const UNSUPPORTED_COLUMN_TYPE: &str = "UNSUPPORTED_COLUMN_TYPE";

/// The frozen query does not return the columns, or take the binds, its descriptor declares.
pub const QUERY_CONTRACT_MISMATCH: &str = "QUERY_CONTRACT_MISMATCH";

/// A request refused before any connection exists: reported with `uncertain: false`.
#[derive(Debug)]
pub struct Refusal {
    pub code: &'static str,
    pub detail: String,
}

fn invalid(detail: String) -> Refusal {
    Refusal {
        code: INVALID_REQUEST,
        detail,
    }
}

/// Every part of an acquisition, resolved and bound, and the period that spans their windows.
#[derive(Debug)]
pub struct Plan {
    pub period_start: NaiveDate,
    pub period_end_exclusive: NaiveDate,
    pub parts: Vec<PlannedPart>,
}

/// One part, resolved against the registry and bound in its descriptor's order: ready to run.
#[derive(Debug)]
pub struct PlannedPart {
    pub index: usize,
    pub capability: Capability,
    pub window_start: NaiveDate,
    pub window_end_exclusive: NaiveDate,
    pub binds: Vec<BindValue>,
}

impl PlannedPart {
    /// `part 1 (care_encounter)`, the prefix of every message about this part.
    fn label(&self) -> String {
        format!(
            "part {} ({})",
            self.index, self.capability.descriptor.capability
        )
    }
}

/// A bind's value, typed as the query receives it.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum BindValue {
    Text(String),
    Date(NaiveDate),
    TextArray(Vec<String>),
}

impl BindValue {
    fn as_sql(&self) -> &(dyn ToSql + Sync) {
        match self {
            Self::Text(value) => value,
            Self::Date(value) => value,
            Self::TextArray(value) => value,
        }
    }

    /// Whether the query's own parameter type takes this value — checked once, after `prepare`.
    fn accepts(&self, ty: &Type) -> bool {
        match self {
            Self::Text(_) => <String as ToSql>::accepts(ty),
            Self::Date(_) => <NaiveDate as ToSql>::accepts(ty),
            Self::TextArray(_) => <Vec<String> as ToSql>::accepts(ty),
        }
    }

    fn type_name(&self) -> &'static str {
        match self {
            Self::Text(_) => "text",
            Self::Date(_) => "date",
            Self::TextArray(_) => "text[]",
        }
    }
}

/// Resolves every part against the compiled-in registry and binds it — all before connecting,
/// so a request this binary cannot honour never opens a session on the source.
pub fn plan(envelope: &AcquireEnvelope) -> Result<Plan, Refusal> {
    if envelope.canonical_schema_version.as_deref() != Some("2") {
        return Err(invalid(format!(
            "a canonical v2 acquisition declares canonical_schema_version \"2\", got {:?}",
            envelope.canonical_schema_version
        )));
    }
    if !extract::is_seven_digit_ibge(&envelope.municipality_ibge) {
        return Err(invalid(
            "municipality_ibge must be a 7-digit IBGE code".to_string(),
        ));
    }
    let (period_start, period_end_exclusive) =
        window(&envelope.period_start, &envelope.period_end_exclusive)
            .map_err(|detail| invalid(format!("acquisition period: {detail}")))?;
    if envelope.parts.is_empty() {
        return Err(invalid(
            "a canonical v2 acquisition reads at least one part".to_string(),
        ));
    }

    let mut seen = HashSet::new();
    let mut parts = Vec::with_capacity(envelope.parts.len());
    for (index, part) in envelope.parts.iter().enumerate() {
        if !seen.insert(part.capability.as_str()) {
            return Err(invalid(format!(
                "part {index}: capability {} is listed twice",
                part.capability
            )));
        }
        let capability = capabilities::resolve(part).map_err(|detail| Refusal {
            code: capabilities::UNKNOWN_CAPABILITY,
            detail: format!("part {index}: {detail}"),
        })?;
        let planned = plan_part(
            index,
            capability,
            part,
            &envelope.municipality_ibge,
            (period_start, period_end_exclusive),
        )
        .map_err(|detail| invalid(format!("part {index} ({}): {detail}", part.capability)))?;
        parts.push(planned);
    }
    Ok(Plan {
        period_start,
        period_end_exclusive,
        parts,
    })
}

/// Binds one part in its descriptor's order: `MUNICIPALITY_IBGE` ← the envelope's municipality,
/// `PERIOD_START`/`PERIOD_END_EXCLUSIVE` ← the part's window, `DATE` ← `date_params[name]`,
/// `TEXT_ARRAY` ← `array_params[name]`. A parameter the query does not take is refused too: the
/// manifest would otherwise record a filter that was never applied.
pub fn plan_part(
    index: usize,
    capability: Capability,
    part: &PartRequest,
    municipality_ibge: &str,
    period: (NaiveDate, NaiveDate),
) -> Result<PlannedPart, String> {
    let (window_start, window_end_exclusive) =
        window(&part.period_start, &part.period_end_exclusive)?;
    if window_start < period.0 || window_end_exclusive > period.1 {
        return Err(format!(
            "window [{window_start}, {window_end_exclusive}) is outside the acquisition period [{}, {})",
            period.0, period.1
        ));
    }
    let binds = &capability.descriptor.binds;
    let declares = |name: &str, kind: BindKind| {
        binds
            .iter()
            .any(|bind| bind.kind == kind && bind.name == name)
    };
    if let Some(name) = part
        .array_params
        .keys()
        .find(|name| !declares(name, BindKind::TextArray))
    {
        return Err(format!("{name} is not a code-list bind of the capability"));
    }
    if let Some(name) = part
        .date_params
        .keys()
        .find(|name| !declares(name, BindKind::Date))
    {
        return Err(format!("{name} is not a date bind of the capability"));
    }

    let values = binds
        .iter()
        .map(|bind| match bind.kind {
            BindKind::MunicipalityIbge => Ok(BindValue::Text(municipality_ibge.to_string())),
            BindKind::PeriodStart => Ok(BindValue::Date(window_start)),
            BindKind::PeriodEndExclusive => Ok(BindValue::Date(window_end_exclusive)),
            BindKind::Date => {
                let value = part
                    .date_params
                    .get(&bind.name)
                    .ok_or_else(|| format!("date bind {} has no value", bind.name))?;
                iso_date(value)
                    .map(BindValue::Date)
                    .ok_or_else(|| format!("date bind {} is not an ISO date: {value}", bind.name))
            }
            BindKind::TextArray => part
                .array_params
                .get(&bind.name)
                .map(|codes| BindValue::TextArray(codes.clone()))
                .ok_or_else(|| format!("code-list bind {} has no value", bind.name)),
        })
        .collect::<Result<Vec<_>, String>>()?;
    Ok(PlannedPart {
        index,
        capability,
        window_start,
        window_end_exclusive,
        binds: values,
    })
}

fn iso_date(value: &str) -> Option<NaiveDate> {
    NaiveDate::parse_from_str(value, "%Y-%m-%d").ok()
}

fn window(start: &str, end_exclusive: &str) -> Result<(NaiveDate, NaiveDate), String> {
    let parsed = |value: &str| iso_date(value).ok_or_else(|| format!("{value} is not an ISO date"));
    let (start, end_exclusive) = (parsed(start)?, parsed(end_exclusive)?);
    if end_exclusive <= start {
        return Err(format!("[{start}, {end_exclusive}) is empty"));
    }
    Ok((start, end_exclusive))
}

/// `acquire` for an envelope with `parts`: C1's protocol — the same session, the probe handshake
/// and Java's `proceed`/`abort`, the extract file, the terminal message — over every part, in one
/// read-only repeatable-read transaction (§1.9.3). The `probe` lists each part's own objects and
/// query checksum, so Java checks each against its own matrix entry; `complete` adds
/// `part_row_counts`.
pub fn acquire(
    mut envelope: AcquireEnvelope,
    mut lines: io::Lines<io::StdinLock<'static>>,
) -> Result<i32, Box<dyn Error>> {
    let plan = match plan(&envelope) {
        Ok(plan) => plan,
        Err(refusal) => {
            // No session ever existed — like a connection that never opened, not uncertain.
            envelope.password.zeroize();
            write_line(&error_message(refusal.code, &refusal.detail, false))?;
            return Ok(1);
        }
    };
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
    let canceller = tls::Canceller::new(client.cancel_token(), session_tls);
    let mut txn = match client
        .build_transaction()
        .read_only(true)
        .isolation_level(IsolationLevel::RepeatableRead)
        .start()
    {
        Ok(txn) => txn,
        Err(err) => return report_pre_probe_failure(&err),
    };

    // Before probing, as on the v1 path: the probes spend the same duration budget.
    let start = Instant::now();
    let budget = Budget {
        max_rows: envelope.budget.max_rows,
        max_duration_ms: envelope.budget.max_duration_ms,
        max_payload_bytes: envelope.budget.max_payload_bytes,
    };
    match probe_message(&mut txn, &envelope, &plan.parts, &start, &budget) {
        Ok(probe) => write_line(&probe)?,
        Err(err) => {
            end_transaction(txn.rollback());
            return report_pre_probe_failure(err.as_ref());
        }
    }

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
    // The cancel listener takes its own lock on stdin.
    drop(lines);
    extract_parts(txn, &envelope, &plan, canceller, start, &budget)
}

/// After Java's `proceed`: opens the extract file, reads every part and reports the outcome,
/// committing only after `complete` is written.
fn extract_parts(
    mut txn: Transaction,
    envelope: &AcquireEnvelope,
    plan: &Plan,
    canceller: tls::Canceller,
    start: Instant,
    budget: &Budget,
) -> Result<i32, Box<dyn Error>> {
    // Opened only now, like the v1 path: a local sink failure still aborts before any row is
    // read, at the one path Java reserved under its `.extract.lock`.
    let scope = extract::Scope {
        source_id: envelope.source_id.clone(),
        municipality_ibge: envelope.municipality_ibge.clone(),
        period_start: plan.period_start,
        period_end_exclusive: plan.period_end_exclusive,
    };
    let mut sink = match ExtractSink::open(
        std::path::Path::new(&envelope.extract_temp_path),
        envelope.budget.max_temp_file_bytes,
        scope,
    ) {
        Ok(sink) => sink,
        Err(err) => {
            let (code, detail) = extract_error_code_and_detail(err);
            write_line(&error_message(code, &detail, true))?;
            end_transaction(txn.rollback());
            return Ok(1);
        }
    };

    let interrupts = Interrupts::spawn(canceller, start, budget.max_duration_ms);
    let outcome = read_parts(
        &mut txn,
        &plan.parts,
        &envelope.municipality_ibge,
        budget,
        start,
        &interrupts,
        &mut sink,
    )?;
    let (message, exit_code) = outcome_message(outcome, sink, &plan.parts);
    write_line(&message)?;
    end_transaction(if exit_code == 0 {
        txn.commit()
    } else {
        txn.rollback()
    });
    Ok(exit_code)
}

/// The `probe` message of a v2 acquisition: the server version once, then, per part, the objects
/// its matrix entry names (`matrix::objects_to_probe`), measured in this same snapshot, with the
/// checksum of the query this binary will run for it.
fn probe_message(
    txn: &mut Transaction,
    envelope: &AcquireEnvelope,
    parts: &[PlannedPart],
    start: &Instant,
    budget: &Budget,
) -> Result<Value, Box<dyn Error>> {
    let postgres_version = txn
        .query_one("SELECT current_setting('server_version')", &[])?
        .try_get::<_, String>(0)?
        .trim()
        .to_string();
    check_probe_duration(start, budget)?;

    let mut cache = probe::ProbeCache::default();
    let mut probed = Vec::with_capacity(parts.len());
    for part in parts {
        let descriptor = &part.capability.descriptor;
        let mut objects = Map::new();
        for object in matrix::objects_to_probe(
            &descriptor.capability,
            &descriptor.adapter_version,
            &envelope.pec_version,
            &envelope.read_model,
            &envelope.installation_role,
        ) {
            let measured = cache.probe_object(txn, &object.object, &object.columns_used)?;
            objects.insert(object.object, measured);
            check_probe_duration(start, budget)?;
        }
        probed.push(json!({
            "index": part.index,
            "capability": descriptor.capability,
            "adapter_version": descriptor.adapter_version,
            "query_checksum": part.capability.query_checksum,
            "objects": objects,
        }));
    }
    Ok(json!({
        "type": "probe",
        "postgres_version": postgres_version,
        "parts": probed,
    }))
}

/// How a read of every part ended.
#[derive(Debug)]
pub enum Outcome {
    /// Every part read and written, with its row count; `ExtractSink::finish` is still due.
    Complete(Vec<i64>),
    /// Stopped the way a v1 stream stops: budget, cancellation, a source failure or a record
    /// outside its part's scope.
    Stopped(StreamOutcome),
    /// The frozen query does not deliver what its descriptor declares (`UNSUPPORTED_COLUMN_TYPE`
    /// or `QUERY_CONTRACT_MISMATCH`) — found once, from the prepared statement, before any row.
    Contract { code: &'static str, detail: String },
}

fn stop<C>(outcome: StreamOutcome) -> ControlFlow<Outcome, C> {
    ControlFlow::Break(Outcome::Stopped(outcome))
}

fn contract(code: &'static str, detail: String) -> Outcome {
    Outcome::Contract { code, detail }
}

/// Reads every part, in order, inside `txn` — the transaction the probe ran in, so all parts see
/// one snapshot. `interrupts` carries Java's cancel and the duration watchdog (spawned by
/// `acquire`; set directly by tests).
pub fn read_parts(
    txn: &mut Transaction,
    parts: &[PlannedPart],
    municipality_ibge: &str,
    budget: &Budget,
    start: Instant,
    interrupts: &Interrupts,
    sink: &mut ExtractSink,
) -> Result<Outcome, Box<dyn Error>> {
    let mut reader = Reader {
        municipality_ibge,
        budget,
        start,
        interrupts,
        sink,
        rows: 0,
        payload_bytes: 0,
    };
    let mut counts = Vec::with_capacity(parts.len());
    for part in parts {
        match reader.read_part(txn, part)? {
            ControlFlow::Continue(count) => counts.push(count),
            ControlFlow::Break(outcome) => return Ok(outcome),
        }
    }
    Ok(Outcome::Complete(counts))
}

/// One acquisition's read across its parts: the bound municipality, the budgets and the running
/// totals they are checked against.
struct Reader<'a> {
    municipality_ibge: &'a str,
    budget: &'a Budget,
    start: Instant,
    interrupts: &'a Interrupts,
    sink: &'a mut ExtractSink,
    rows: i64,
    payload_bytes: i64,
}

impl Reader<'_> {
    /// Prepares the part's query, checks it against the descriptor, then streams its rows — the
    /// v1 row loop's cancellation and duration checks, row by row and after the last fetch.
    fn read_part(
        &mut self,
        txn: &mut Transaction,
        part: &PlannedPart,
    ) -> Result<ControlFlow<Outcome, i64>, Box<dyn Error>> {
        if self.interrupts.cancelled() {
            return Ok(stop(StreamOutcome::Cancelled));
        }
        let query = stream::to_positional_placeholders(part.capability.query_text);
        let statement = match txn.prepare(&query) {
            Ok(statement) => statement,
            Err(err) => return Ok(stop(self.interrupts.classify(&err))),
        };
        let columns = match plan_columns(&statement, part) {
            Ok(columns) => columns,
            Err(outcome) => return Ok(ControlFlow::Break(outcome)),
        };
        let params: Vec<&(dyn ToSql + Sync)> = part.binds.iter().map(BindValue::as_sql).collect();
        let mut rows = match txn.query_raw(&statement, params) {
            Ok(rows) => rows,
            Err(err) => return Ok(stop(self.interrupts.classify(&err))),
        };

        let mut count: i64 = 0;
        loop {
            if self.interrupts.cancelled() {
                return Ok(stop(StreamOutcome::Cancelled));
            }
            let row = match rows.next() {
                Ok(Some(row)) => row,
                Ok(None) => {
                    if self.interrupts.cancelled() {
                        return Ok(stop(StreamOutcome::Cancelled));
                    }
                    if let Some(outcome) = stream::check_duration(&self.start, self.budget) {
                        return Ok(stop(outcome));
                    }
                    return Ok(ControlFlow::Continue(count));
                }
                Err(err) => return Ok(stop(self.interrupts.classify(&err))),
            };
            if let ControlFlow::Break(outcome) = self.write_row(part, &columns, &row)? {
                return Ok(ControlFlow::Break(outcome));
            }
            count += 1;
        }
    }

    /// One row: the cumulative row ceiling, the conversion, the cumulative payload ceiling, the
    /// part's scope, then the extract file — in the v1 row loop's order.
    fn write_row(
        &mut self,
        part: &PlannedPart,
        columns: &[ColumnPlan],
        row: &Row,
    ) -> Result<ControlFlow<Outcome>, Box<dyn Error>> {
        self.rows += 1;
        if self.rows > self.budget.max_rows {
            return Ok(stop(StreamOutcome::BudgetExceeded(format!(
                "row ceiling exceeded: {} > {}",
                self.rows, self.budget.max_rows
            ))));
        }
        if let Some(outcome) = stream::check_duration(&self.start, self.budget) {
            return Ok(stop(outcome));
        }

        let values = match decode_row(row, columns) {
            Ok(values) => values,
            Err(detail) => {
                return Ok(stop(StreamOutcome::InvalidRecord(format!(
                    "{}: {detail}",
                    part.label()
                ))))
            }
        };
        let line = match serialize_line(part, columns, &values) {
            Ok(line) => line,
            Err(err) => {
                return Ok(stop(StreamOutcome::Failed {
                    detail: format!("could not serialize a record: {err}"),
                    sqlstate: None,
                }))
            }
        };
        self.payload_bytes = self
            .payload_bytes
            .saturating_add(i64::try_from(line.len()).unwrap_or(i64::MAX));
        if self.payload_bytes > self.budget.max_payload_bytes {
            return Ok(stop(StreamOutcome::BudgetExceeded(format!(
                "payload byte ceiling exceeded: {} > {}",
                self.payload_bytes, self.budget.max_payload_bytes
            ))));
        }
        if let Some(outcome) = stream::check_duration(&self.start, self.budget) {
            return Ok(stop(outcome));
        }

        if let Err(detail) = check_record(part, columns, &values, self.municipality_ibge) {
            return Ok(stop(StreamOutcome::InvalidRecord(detail)));
        }
        if let Err(err) = self.sink.write_canonical_line(line) {
            return Ok(stop(stream::sink_failure(err)));
        }
        if self.rows % stream::PROGRESS_INTERVAL == 0 {
            write_line(&json!({ "type": "progress" }))?;
        }
        Ok(ControlFlow::Continue(()))
    }
}

/// How one result column becomes JSON — decided by its PostgreSQL type, once per query: integers
/// and text become strings, a date `"yyyy-MM-dd"`, a boolean stays one, a text array an array of
/// strings, and NULL is `null`. Everything else (`numeric`, `float8`, `timestamp`, …) has no
/// canonical form here. PostgreSQL 9.6 types an untyped literal column (`'tb_x' AS
/// source_entity_type`) as `unknown` and sends it as text, so it decodes as text.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum Decode {
    Int2,
    Int4,
    Int8,
    Text,
    Date,
    Bool,
    TextArray,
}

impl Decode {
    fn of(ty: &Type) -> Option<Self> {
        match *ty {
            Type::INT2 => Some(Self::Int2),
            Type::INT4 => Some(Self::Int4),
            Type::INT8 => Some(Self::Int8),
            Type::TEXT | Type::VARCHAR | Type::BPCHAR | Type::UNKNOWN => Some(Self::Text),
            Type::DATE => Some(Self::Date),
            Type::BOOL => Some(Self::Bool),
            Type::TEXT_ARRAY | Type::VARCHAR_ARRAY | Type::BPCHAR_ARRAY => Some(Self::TextArray),
            _ => None,
        }
    }

    /// Whether a value decoded this way can be the field its descriptor declares: the reader
    /// parses a `date`, `integer` or `decimal` field from a string, never from a boolean or array.
    fn fits(self, kind: ColumnKind) -> bool {
        match kind {
            ColumnKind::Text => !matches!(self, Self::Bool | Self::TextArray),
            ColumnKind::Date => matches!(self, Self::Date | Self::Text),
            ColumnKind::Integer | ColumnKind::Decimal => {
                matches!(self, Self::Int2 | Self::Int4 | Self::Int8 | Self::Text)
            }
            ColumnKind::Bool => self == Self::Bool,
            ColumnKind::TextArray => self == Self::TextArray,
        }
    }
}

fn kind_name(kind: ColumnKind) -> &'static str {
    match kind {
        ColumnKind::Text => "text",
        ColumnKind::Date => "date",
        ColumnKind::Integer => "integer",
        ColumnKind::Decimal => "decimal",
        ColumnKind::Bool => "bool",
        ColumnKind::TextArray => "text[]",
    }
}

/// One record field: where it sits in the row and how it is decoded.
#[derive(Debug)]
struct ColumnPlan<'a> {
    name: &'a str,
    index: usize,
    decode: Decode,
    required: bool,
}

/// Checks the prepared statement against the descriptor — the binds' types, then exactly the
/// declared columns, each with a type that converts to its declared shape — and returns the record
/// fields in descriptor order.
fn plan_columns<'a>(
    statement: &Statement,
    part: &'a PlannedPart,
) -> Result<Vec<ColumnPlan<'a>>, Outcome> {
    let descriptor = &part.capability.descriptor;
    let label = part.label();
    let params = statement.params();
    if params.len() != part.binds.len() {
        return Err(contract(
            QUERY_CONTRACT_MISMATCH,
            format!(
                "{label}: the query takes {} binds, its descriptor declares {}",
                params.len(),
                part.binds.len()
            ),
        ));
    }
    for (position, (bind, ty)) in part.binds.iter().zip(params).enumerate() {
        if !bind.accepts(ty) {
            return Err(contract(
                QUERY_CONTRACT_MISMATCH,
                format!(
                    "{label}: bind {} ({}) is {} in the query, not {}",
                    position + 1,
                    descriptor.binds[position].name,
                    ty.name(),
                    bind.type_name()
                ),
            ));
        }
    }

    let returned = statement.columns();
    let mut seen = HashSet::new();
    for column in returned {
        if !seen.insert(column.name()) {
            return Err(contract(
                QUERY_CONTRACT_MISMATCH,
                format!("{label}: the query returns column {} twice", column.name()),
            ));
        }
        if descriptor.column(column.name()).is_none() {
            return Err(contract(
                QUERY_CONTRACT_MISMATCH,
                format!(
                    "{label}: the query returns column {}, which its descriptor does not declare",
                    column.name()
                ),
            ));
        }
    }
    descriptor
        .columns
        .iter()
        .map(|declared| {
            let (index, column) = returned
                .iter()
                .enumerate()
                .find(|(_, column)| column.name() == declared.name)
                .ok_or_else(|| {
                    contract(
                        QUERY_CONTRACT_MISMATCH,
                        format!("{label}: the query does not return column {}", declared.name),
                    )
                })?;
            let decode = Decode::of(column.type_()).ok_or_else(|| {
                contract(
                    UNSUPPORTED_COLUMN_TYPE,
                    format!(
                        "{label}: column {} is {}, which has no canonical form — convert it in the query",
                        declared.name,
                        column.type_().name()
                    ),
                )
            })?;
            if !decode.fits(declared.kind) {
                return Err(contract(
                    UNSUPPORTED_COLUMN_TYPE,
                    format!(
                        "{label}: column {} is declared {} but the query returns {}",
                        declared.name,
                        kind_name(declared.kind),
                        column.type_().name()
                    ),
                ));
            }
            Ok(ColumnPlan {
                name: &declared.name,
                index,
                decode,
                required: declared.required,
            })
        })
        .collect()
}

fn decode_row(row: &Row, columns: &[ColumnPlan]) -> Result<Vec<Value>, String> {
    columns.iter().map(|column| decode(row, column)).collect()
}

/// `try_get`, never `get`: a value that does not decode is a refused record, not a panic.
fn decode(row: &Row, column: &ColumnPlan) -> Result<Value, String> {
    let undecodable =
        |err: postgres::Error| format!("column {} does not decode: {err}", column.name);
    let text = |value: Option<String>| value.map_or(Value::Null, Value::String);
    Ok(match column.decode {
        Decode::Int2 => text(
            row.try_get::<_, Option<i16>>(column.index)
                .map_err(undecodable)?
                .map(|n| n.to_string()),
        ),
        Decode::Int4 => text(
            row.try_get::<_, Option<i32>>(column.index)
                .map_err(undecodable)?
                .map(|n| n.to_string()),
        ),
        Decode::Int8 => text(
            row.try_get::<_, Option<i64>>(column.index)
                .map_err(undecodable)?
                .map(|n| n.to_string()),
        ),
        Decode::Text => text(
            row.try_get::<_, Option<String>>(column.index)
                .map_err(undecodable)?,
        ),
        Decode::Date => text(
            row.try_get::<_, Option<NaiveDate>>(column.index)
                .map_err(undecodable)?
                .map(|date| date.format("%Y-%m-%d").to_string()),
        ),
        Decode::Bool => row
            .try_get::<_, Option<bool>>(column.index)
            .map_err(undecodable)?
            .map_or(Value::Null, Value::Bool),
        Decode::TextArray => match row
            .try_get::<_, Option<Vec<Option<String>>>>(column.index)
            .map_err(undecodable)?
        {
            None => Value::Null,
            Some(elements) => Value::Array(
                elements
                    .into_iter()
                    .map(|element| {
                        element.map(Value::String).ok_or_else(|| {
                            format!("column {} holds a NULL array element", column.name)
                        })
                    })
                    .collect::<Result<_, _>>()?,
            ),
        },
    })
}

/// The write-time scope check of a v2 record, the part-aware twin of `extract::validate`:
/// required columns hold a value (a blank string is none), the municipality column is the bound
/// municipality, and the scope date — when the capability has one — lies in the part's window.
fn check_record(
    part: &PlannedPart,
    columns: &[ColumnPlan],
    values: &[Value],
    municipality_ibge: &str,
) -> Result<(), String> {
    let label = part.label();
    for (column, value) in columns.iter().zip(values) {
        let empty = value.is_null() || value.as_str().is_some_and(|text| text.trim().is_empty());
        if column.required && empty {
            return Err(format!("{label}: required column {} is empty", column.name));
        }
    }
    let descriptor = &part.capability.descriptor;
    let value_of = |name: &str| {
        columns
            .iter()
            .zip(values)
            .find(|(column, _)| column.name == name)
            .and_then(|(_, value)| value.as_str())
    };
    let municipality = value_of(&descriptor.municipality_column);
    if municipality != Some(municipality_ibge) {
        return Err(format!(
            "record does not match the bound acquisition scope: {label} has {} {}",
            descriptor.municipality_column,
            municipality.unwrap_or("null")
        ));
    }
    if let Some(scope) = &descriptor.scope_date_column {
        let date = value_of(scope).and_then(iso_date);
        if !date.is_some_and(|date| date >= part.window_start && date < part.window_end_exclusive) {
            return Err(format!(
                "record does not match the bound acquisition scope: {label} has {scope} outside [{}, {})",
                part.window_start, part.window_end_exclusive
            ));
        }
    }
    Ok(())
}

/// `{"part":i,"kind":"…","record":{…}}`, the record's fields in descriptor order.
fn serialize_line(
    part: &PlannedPart,
    columns: &[ColumnPlan],
    values: &[Value],
) -> serde_json::Result<Vec<u8>> {
    serde_json::to_vec(&Line {
        part: part.index,
        kind: &part.capability.descriptor.record_kind,
        record: Record { columns, values },
    })
}

#[derive(Serialize)]
struct Line<'a> {
    part: usize,
    kind: &'a str,
    record: Record<'a>,
}

struct Record<'a> {
    columns: &'a [ColumnPlan<'a>],
    values: &'a [Value],
}

impl Serialize for Record<'_> {
    fn serialize<S: Serializer>(&self, serializer: S) -> Result<S::Ok, S::Error> {
        let mut map = serializer.serialize_map(Some(self.columns.len()))?;
        for (column, value) in self.columns.iter().zip(self.values) {
            map.serialize_entry(column.name, value)?;
        }
        map.end()
    }
}

fn error_message(code: &str, detail: &str, uncertain: bool) -> Value {
    json!({ "type": "error", "code": code, "detail": detail, "uncertain": uncertain })
}

/// The terminal message for `outcome` and the exit code that goes with it — `0` (commit) only
/// after the extract file is finished. Every failure after the session opened is uncertain
/// (ENG-51), as on the v1 path.
pub fn outcome_message(outcome: Outcome, sink: ExtractSink, parts: &[PlannedPart]) -> (Value, i32) {
    match outcome {
        Outcome::Complete(counts) => match sink.finish() {
            Ok(completion) => (
                json!({
                    "type": "complete",
                    "row_count": completion.row_count,
                    "exclusion_count": completion.exclusion_count,
                    "checksum": completion.checksum,
                    "compressed_bytes": completion.compressed_bytes,
                    "part_row_counts": part_row_counts(parts, &counts),
                }),
                0,
            ),
            Err(err) => {
                let (code, detail) = extract_error_code_and_detail(err);
                (error_message(code, &detail, true), 1)
            }
        },
        Outcome::Stopped(stopped) => stopped_message(stopped),
        Outcome::Contract { code, detail } => (error_message(code, &detail, true), 1),
    }
}

/// One entry per part, in part order: its index, capability, record kind and row count — Java
/// checks each against the part it asked for before publishing the manifest.
fn part_row_counts(parts: &[PlannedPart], counts: &[i64]) -> Vec<Value> {
    parts
        .iter()
        .zip(counts)
        .map(|(part, count)| {
            json!({
                "part": part.index,
                "capability": part.capability.descriptor.capability,
                "kind": part.capability.descriptor.record_kind,
                "row_count": count,
            })
        })
        .collect()
}

/// The v1 path's error codes for a stopped stream (`main.rs` `acquire`), the same exit codes.
fn stopped_message(stopped: StreamOutcome) -> (Value, i32) {
    match stopped {
        StreamOutcome::BudgetExceeded(detail) => {
            (error_message("SOURCE_BUDGET_EXCEEDED", &detail, true), 1)
        }
        StreamOutcome::Cancelled => (
            error_message("CANCELLED", "cancelled cooperatively", true),
            2,
        ),
        StreamOutcome::Failed { detail, sqlstate } => {
            let mut error = error_message("UNCLASSIFIED_ERROR", &detail, true);
            if let Some(sqlstate) = sqlstate {
                error["code"] = json!("SQL_ERROR");
                error["sqlstate"] = json!(sqlstate);
            }
            (error, 1)
        }
        StreamOutcome::InvalidRecord(detail) => {
            (error_message("INVALID_EXTRACT_RECORD", &detail, true), 1)
        }
        // A stop never carries the v1 stream's success; without the part counts it is no success.
        StreamOutcome::Success => (
            error_message(
                "UNCLASSIFIED_ERROR",
                "the read stopped without a reason",
                true,
            ),
            1,
        ),
    }
}

#[cfg(test)]
#[path = "../tests/support/postgres.rs"]
mod test_postgres;

#[cfg(test)]
mod tests {
    use super::*;
    use crate::capabilities::{query_checksum, Packaged, REGISTRY};
    use sha2::Digest as _;
    use std::collections::BTreeMap;
    use std::path::PathBuf;

    const MUNICIPALITY: &str = "3541307";

    fn date(value: &str) -> NaiveDate {
        iso_date(value).unwrap()
    }

    /// A request for `capability` exactly as compiled in, over the first quarter of 2026.
    fn request(capability: &str) -> PartRequest {
        let packaged = REGISTRY.iter().find(|p| p.id == capability).unwrap();
        let descriptor = packaged.load().unwrap().descriptor;
        PartRequest {
            capability: capability.to_string(),
            adapter_version: descriptor.adapter_version,
            query_checksum: query_checksum(packaged.query_text),
            record_kind: descriptor.record_kind,
            period_start: "2026-01-01".to_string(),
            period_end_exclusive: "2026-04-01".to_string(),
            array_params: BTreeMap::new(),
            date_params: BTreeMap::from([
                ("birth_date_from".to_string(), "2024-01-01".to_string()),
                ("birth_date_to".to_string(), "2024-12-31".to_string()),
            ]),
        }
    }

    fn envelope(parts: Vec<PartRequest>) -> AcquireEnvelope {
        let mut envelope: AcquireEnvelope = serde_json::from_value(json!({
            "type": "acquire", "source_id": "src-1", "host": "127.0.0.1", "port": 1,
            "database": "esus", "user": "u", "password": "p", "municipality_ibge": MUNICIPALITY,
            "pec_version": "5.5.28", "read_model": "PEC_DW", "installation_role": "PRONTUARIO",
            "extraction_id": "e", "period_start": "2026-01-01",
            "period_end_exclusive": "2026-04-01", "source_zone_id": "America/Sao_Paulo",
            "extract_temp_path": "/nonexistent/e.jsonl.gz.tmp", "query_checksum": "sha256:c",
            "adapter_version": "canonical-v2", "canonical_schema_version": "2",
            "budget": {"connect_timeout_ms": 1, "acquisition_timeout_ms": 1,
                "statement_timeout_ms": 1, "lock_timeout_ms": 1,
                "idle_in_transaction_timeout_ms": 1, "max_rows": 1, "max_duration_ms": 1,
                "max_payload_bytes": 1, "max_temp_file_bytes": 1},
        }))
        .unwrap();
        envelope.parts = parts;
        envelope
    }

    fn refusal(envelope: &AcquireEnvelope) -> Refusal {
        plan(envelope).unwrap_err()
    }

    #[test]
    fn each_part_is_bound_in_its_descriptors_order() {
        let mut conditions = request("condition_list");
        conditions.period_start = "2025-04-01".to_string();
        conditions.period_end_exclusive = "2026-04-01".to_string();
        conditions.array_params = BTreeMap::from([
            ("ciap_codes".to_string(), vec!["T90".to_string()]),
            (
                "cid_codes".to_string(),
                vec!["E11".to_string(), "E14".to_string()],
            ),
        ]);
        let mut envelope = envelope(vec![conditions, request("citizen")]);
        envelope.period_start = "2025-04-01".to_string();

        let plan = plan(&envelope).unwrap();

        assert_eq!(
            (plan.period_start, plan.period_end_exclusive),
            (date("2025-04-01"), date("2026-04-01"))
        );
        assert_eq!(
            plan.parts[0].binds,
            [
                BindValue::Text(MUNICIPALITY.to_string()),
                BindValue::Date(date("2025-04-01")),
                BindValue::Date(date("2026-04-01")),
                BindValue::Date(date("2024-01-01")),
                BindValue::Date(date("2024-12-31")),
                BindValue::TextArray(vec!["T90".to_string()]),
                BindValue::TextArray(vec!["E11".to_string(), "E14".to_string()]),
            ]
        );
        assert_eq!(plan.parts[1].index, 1);
        assert_eq!(
            plan.parts[1].binds,
            [
                BindValue::Text(MUNICIPALITY.to_string()),
                BindValue::Date(date("2024-01-01")),
                BindValue::Date(date("2024-12-31")),
            ]
        );
        assert_eq!(plan.parts[1].label(), "part 1 (citizen)");
    }

    #[test]
    fn a_part_this_binary_does_not_have_is_an_unknown_capability() {
        let mut stranger = request("citizen");
        stranger.capability = "pregnancy_outcome".to_string();
        let refused = refusal(&envelope(vec![request("home_visit"), stranger]));
        assert_eq!(refused.code, capabilities::UNKNOWN_CAPABILITY);
        assert!(refused.detail.starts_with("part 1: "), "{}", refused.detail);

        let mut tampered = request("home_visit");
        tampered.query_checksum = format!("sha256:{}", "f".repeat(64));
        assert_eq!(
            refusal(&envelope(vec![tampered])).code,
            capabilities::UNKNOWN_CAPABILITY
        );
    }

    #[test]
    fn a_request_that_does_not_fit_its_capability_is_invalid() {
        type Change = fn(&mut AcquireEnvelope);
        let cases: [(&str, Change); 10] = [
            ("has no value", |e| {
                e.parts[0].date_params.remove("birth_date_to");
            }),
            ("not an ISO date", |e| {
                e.parts[0]
                    .date_params
                    .insert("birth_date_from".to_string(), "2024/01/01".to_string());
            }),
            ("not a code-list bind", |e| {
                e.parts[0]
                    .array_params
                    .insert("procedure_codes".to_string(), vec![]);
            }),
            ("not a date bind", |e| {
                e.parts[0]
                    .date_params
                    .insert("period_start".to_string(), "2026-01-01".to_string());
            }),
            ("outside the acquisition period", |e| {
                e.parts[0].period_start = "2025-12-01".to_string();
            }),
            ("is empty", |e| {
                e.parts[0].period_end_exclusive = "2026-01-01".to_string();
            }),
            ("listed twice", |e| {
                let again = request("citizen");
                e.parts.push(again);
            }),
            ("canonical_schema_version", |e| {
                e.canonical_schema_version = Some("1".to_string());
            }),
            ("7-digit", |e| e.municipality_ibge = "354130".to_string()),
            ("at least one part", |e| e.parts.clear()),
        ];
        for (expected, change) in cases {
            let mut envelope = envelope(vec![request("citizen")]);
            change(&mut envelope);
            let refused = refusal(&envelope);
            assert_eq!(refused.code, INVALID_REQUEST, "{expected}");
            assert!(refused.detail.contains(expected), "{}", refused.detail);
        }
    }

    #[test]
    fn a_code_list_bind_needs_its_list() {
        let refused = refusal(&envelope(vec![request("procedure_performed")]));
        assert_eq!(refused.code, INVALID_REQUEST);
        assert!(refused
            .detail
            .contains("code-list bind procedure_codes has no value"));
    }

    #[test]
    fn only_the_canonical_postgres_types_decode() {
        for (ty, decode) in [
            (Type::INT2, Decode::Int2),
            (Type::INT4, Decode::Int4),
            (Type::INT8, Decode::Int8),
            (Type::TEXT, Decode::Text),
            (Type::VARCHAR, Decode::Text),
            (Type::BPCHAR, Decode::Text),
            (Type::UNKNOWN, Decode::Text),
            (Type::DATE, Decode::Date),
            (Type::BOOL, Decode::Bool),
            (Type::TEXT_ARRAY, Decode::TextArray),
            (Type::VARCHAR_ARRAY, Decode::TextArray),
        ] {
            assert_eq!(Decode::of(&ty), Some(decode), "{ty}");
        }
        for ty in [
            Type::NUMERIC,
            Type::FLOAT4,
            Type::FLOAT8,
            Type::TIMESTAMP,
            Type::TIMESTAMPTZ,
            Type::UUID,
            Type::INT4_ARRAY,
            Type::JSON,
        ] {
            assert_eq!(Decode::of(&ty), None, "{ty}");
        }
    }

    #[test]
    fn a_column_must_decode_to_the_shape_its_descriptor_declares() {
        use ColumnKind as K;
        use Decode as D;
        for (decode, fits) in [
            (D::Text, vec![K::Text, K::Date, K::Integer, K::Decimal]),
            (D::Int8, vec![K::Text, K::Integer, K::Decimal]),
            (D::Date, vec![K::Text, K::Date]),
            (D::Bool, vec![K::Bool]),
            (D::TextArray, vec![K::TextArray]),
        ] {
            for kind in [
                K::Text,
                K::Date,
                K::Integer,
                K::Decimal,
                K::Bool,
                K::TextArray,
            ] {
                assert_eq!(
                    decode.fits(kind),
                    fits.contains(&kind),
                    "{decode:?} as {}",
                    kind_name(kind)
                );
            }
        }
    }

    /// A planned part of an in-test capability; its query text is not checked against anything.
    fn planned(
        index: usize,
        id: &'static str,
        descriptor: &'static str,
        sql: &str,
        request: &PartRequest,
    ) -> PlannedPart {
        let capability = Packaged {
            id,
            descriptor_json: descriptor,
            query_text: sql.to_string().leak(),
        }
        .load()
        .unwrap();
        plan_part(
            index,
            capability,
            request,
            MUNICIPALITY,
            (date("2026-01-01"), date("2026-04-01")),
        )
        .unwrap()
    }

    /// `test_event`: one row per event of a period, each column type the decoder knows.
    const EVENT_DESCRIPTOR: &str = r#"{"capability":"test_event","adapter_version":"0.1.0",
        "description":"in-test capability","record_kind":"care_event",
        "entity_type_column":"source_entity_type","record_id_column":"source_record_id",
        "municipality_column":"municipality_ibge","scope_date_column":"event_date",
        "query":"queries/test_event@0.1.0.sql",
        "binds":[{"name":"municipality_ibge","type":"MUNICIPALITY_IBGE"},
                 {"name":"period_start","type":"PERIOD_START"},
                 {"name":"period_end_exclusive","type":"PERIOD_END_EXCLUSIVE"},
                 {"name":"type_codes","type":"TEXT_ARRAY"}],
        "columns":[{"name":"source_entity_type","type":"text","required":true},
                   {"name":"source_record_id","type":"text","required":true},
                   {"name":"municipality_ibge","type":"text","required":true},
                   {"name":"person_key","type":"text","required":true},
                   {"name":"event_date","type":"date","required":true},
                   {"name":"active","type":"bool","required":false},
                   {"name":"codes","type":"text[]","required":false},
                   {"name":"weight_kg","type":"decimal","required":false},
                   {"name":"small_count","type":"integer","required":false},
                   {"name":"mid_count","type":"integer","required":false},
                   {"name":"letter","type":"text","required":false}]}"#;

    const EVENT_SQL: &str = "SELECT 'test_event' AS source_entity_type,
       e.id AS source_record_id,
       e.municipio AS municipality_ibge,
       e.pessoa AS person_key,
       e.dia AS event_date,
       e.ativo AS active,
       e.codigos AS codes,
       CAST(e.peso AS text) AS weight_kg,
       e.pequeno AS small_count,
       e.medio AS mid_count,
       e.letra AS letter
  FROM test_event e
 WHERE e.municipio = ?
   AND e.dia >= ?
   AND e.dia < ?
   AND e.tipo = ANY(?)
 ORDER BY e.id";

    /// `test_person`: people born in a range, no scope date — like `citizen`.
    const PERSON_DESCRIPTOR: &str = r#"{"capability":"test_person","adapter_version":"0.1.0",
        "description":"in-test capability","record_kind":"person",
        "entity_type_column":"source_entity_type","record_id_column":"source_record_id",
        "municipality_column":"municipality_ibge","scope_date_column":null,
        "query":"queries/test_person@0.1.0.sql",
        "binds":[{"name":"municipality_ibge","type":"MUNICIPALITY_IBGE"},
                 {"name":"birth_date_from","type":"DATE"},
                 {"name":"birth_date_to","type":"DATE"}],
        "columns":[{"name":"source_entity_type","type":"text","required":true},
                   {"name":"source_record_id","type":"text","required":true},
                   {"name":"municipality_ibge","type":"text","required":true},
                   {"name":"person_key","type":"text","required":true},
                   {"name":"birth_date","type":"date","required":true}]}"#;

    const PERSON_SQL: &str = "SELECT CAST('test_person' AS text) AS source_entity_type,
       CAST(p.id AS text) AS source_record_id,
       p.municipio AS municipality_ibge,
       p.pessoa AS person_key,
       p.nascimento AS birth_date
  FROM test_person p
 WHERE p.municipio = ? AND p.nascimento >= ? AND p.nascimento <= ?
 ORDER BY p.id";

    fn event_request() -> PartRequest {
        PartRequest {
            capability: "test_event".to_string(),
            adapter_version: "0.1.0".to_string(),
            query_checksum: "sha256:in-test".to_string(),
            record_kind: "care_event".to_string(),
            period_start: "2026-03-01".to_string(),
            period_end_exclusive: "2026-04-01".to_string(),
            array_params: BTreeMap::from([(
                "type_codes".to_string(),
                vec!["A".to_string(), "B".to_string()],
            )]),
            date_params: BTreeMap::new(),
        }
    }

    fn person_request() -> PartRequest {
        PartRequest {
            capability: "test_person".to_string(),
            adapter_version: "0.1.0".to_string(),
            query_checksum: "sha256:in-test".to_string(),
            record_kind: "person".to_string(),
            period_start: "2026-01-01".to_string(),
            period_end_exclusive: "2026-04-01".to_string(),
            array_params: BTreeMap::new(),
            date_params: BTreeMap::from([
                ("birth_date_from".to_string(), "2024-01-01".to_string()),
                ("birth_date_to".to_string(), "2024-12-31".to_string()),
            ]),
        }
    }

    fn event_part(sql: &str) -> PlannedPart {
        planned(0, "test_event", EVENT_DESCRIPTOR, sql, &event_request())
    }

    fn person_part(index: usize) -> PlannedPart {
        planned(
            index,
            "test_person",
            PERSON_DESCRIPTOR,
            PERSON_SQL,
            &person_request(),
        )
    }

    /// The person part's record fields, as `plan_columns` would build them.
    fn person_columns(part: &PlannedPart) -> Vec<ColumnPlan<'_>> {
        part.capability
            .descriptor
            .columns
            .iter()
            .enumerate()
            .map(|(index, column)| ColumnPlan {
                name: &column.name,
                index,
                decode: Decode::Text,
                required: column.required,
            })
            .collect()
    }

    fn person_values(municipality: &str, person_key: Value) -> Vec<Value> {
        vec![
            json!("test_person"),
            json!("10"),
            json!(municipality),
            person_key,
            json!("2024-02-29"),
        ]
    }

    #[test]
    fn a_record_needs_its_required_columns_and_the_bound_municipality() {
        let part = person_part(1);
        let columns = person_columns(&part);
        let check = |values: Vec<Value>| check_record(&part, &columns, &values, MUNICIPALITY);

        assert_eq!(check(person_values(MUNICIPALITY, json!("p1"))), Ok(()));
        assert!(check(person_values(MUNICIPALITY, Value::Null))
            .unwrap_err()
            .contains("part 1 (test_person): required column person_key is empty"));
        assert!(check(person_values(MUNICIPALITY, json!("  ")))
            .unwrap_err()
            .contains("required column person_key"));
        let foreign = check(person_values("1100015", json!("p1"))).unwrap_err();
        assert!(foreign.contains("bound acquisition scope"), "{foreign}");
        assert!(foreign.contains("municipality_ibge 1100015"), "{foreign}");
    }

    #[test]
    fn a_record_dated_outside_its_parts_window_is_refused() {
        let part = event_part(EVENT_SQL);
        let columns: Vec<ColumnPlan> = part
            .capability
            .descriptor
            .columns
            .iter()
            .enumerate()
            .map(|(index, column)| ColumnPlan {
                name: &column.name,
                index,
                decode: Decode::Text,
                required: column.required,
            })
            .collect();
        let with_date = |event_date: &str| {
            let mut values = vec![Value::Null; columns.len()];
            values[0] = json!("test_event");
            values[1] = json!("1");
            values[2] = json!(MUNICIPALITY);
            values[3] = json!("p1");
            values[4] = json!(event_date);
            check_record(&part, &columns, &values, MUNICIPALITY)
        };
        assert_eq!(with_date("2026-03-01"), Ok(()));
        assert_eq!(with_date("2026-03-31"), Ok(()));
        for outside in ["2026-02-28", "2026-04-01", "01/03/2026"] {
            let refused = with_date(outside).unwrap_err();
            assert!(
                refused.contains("event_date outside [2026-03-01, 2026-04-01)"),
                "{refused}"
            );
        }
    }

    #[test]
    fn a_line_names_its_part_and_kind_and_keeps_the_descriptors_column_order() {
        let part = person_part(1);
        let columns = person_columns(&part);
        let line = serialize_line(&part, &columns, &person_values(MUNICIPALITY, Value::Null));
        assert_eq!(
            String::from_utf8(line.unwrap()).unwrap(),
            r#"{"part":1,"kind":"person","record":{"source_entity_type":"test_person","source_record_id":"10","municipality_ibge":"3541307","person_key":null,"birth_date":"2024-02-29"}}"#
        );
    }

    #[test]
    fn a_stopped_read_reports_the_v1_error_codes() {
        let cases = [
            (
                StreamOutcome::BudgetExceeded("row ceiling".to_string()),
                "SOURCE_BUDGET_EXCEEDED",
                1,
            ),
            (StreamOutcome::Cancelled, "CANCELLED", 2),
            (
                StreamOutcome::Failed {
                    detail: "relation does not exist".to_string(),
                    sqlstate: Some("42P01".to_string()),
                },
                "SQL_ERROR",
                1,
            ),
            (
                StreamOutcome::Failed {
                    detail: "broken".to_string(),
                    sqlstate: None,
                },
                "UNCLASSIFIED_ERROR",
                1,
            ),
            (
                StreamOutcome::InvalidRecord("scope".to_string()),
                "INVALID_EXTRACT_RECORD",
                1,
            ),
            (StreamOutcome::Success, "UNCLASSIFIED_ERROR", 1),
        ];
        for (stopped, code, exit_code) in cases {
            let (message, exit) = stopped_message(stopped);
            assert_eq!((message["code"].as_str(), exit), (Some(code), exit_code));
            assert_eq!(message["uncertain"], true);
        }
        let (failed, _) = stopped_message(StreamOutcome::Failed {
            detail: "d".to_string(),
            sqlstate: Some("42P01".to_string()),
        });
        assert_eq!(failed["sqlstate"], "42P01");
    }

    fn temp_sink(name: &str, max_bytes: i64) -> (ExtractSink, PathBuf) {
        static NEXT: std::sync::atomic::AtomicUsize = std::sync::atomic::AtomicUsize::new(0);
        let dir = std::env::temp_dir().join(format!(
            "execplane-canonical-test-{}-{name}-{}",
            std::process::id(),
            NEXT.fetch_add(1, std::sync::atomic::Ordering::Relaxed)
        ));
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("extract.jsonl.gz.tmp");
        let scope = extract::Scope {
            source_id: "src-1".to_string(),
            municipality_ibge: MUNICIPALITY.to_string(),
            period_start: date("2026-01-01"),
            period_end_exclusive: date("2026-04-01"),
        };
        (ExtractSink::open(&path, max_bytes, scope).unwrap(), path)
    }

    fn gunzip_lines(path: &std::path::Path) -> Vec<String> {
        use std::io::Read;
        let mut text = String::new();
        flate2::read::GzDecoder::new(std::fs::File::open(path).unwrap())
            .read_to_string(&mut text)
            .unwrap();
        text.lines().map(str::to_string).collect()
    }

    #[test]
    fn a_contract_failure_is_reported_as_uncertain_with_its_own_code() {
        let (sink, path) = temp_sink("contract", 1_048_576);
        let (message, exit_code) = outcome_message(
            contract(UNSUPPORTED_COLUMN_TYPE, "numeric".to_string()),
            sink,
            &[],
        );
        assert_eq!(exit_code, 1);
        assert_eq!(
            message,
            json!({"type": "error", "code": "UNSUPPORTED_COLUMN_TYPE", "detail": "numeric",
                   "uncertain": true})
        );
        std::fs::remove_dir_all(path.parent().unwrap()).ok();
    }

    // ---- Against a real PostgreSQL 9.6 (Docker): the typed read of in-test capabilities. ----

    const SCHEMA: &str = "
        CREATE TABLE test_event (
            id bigint PRIMARY KEY, municipio varchar(7) NOT NULL, pessoa text, dia date NOT NULL,
            tipo text NOT NULL, ativo boolean, codigos varchar[], peso numeric(6,2),
            pequeno smallint, medio integer, letra char(2));
        INSERT INTO test_event VALUES
            (1, '3541307', 'p1', '2026-03-05', 'A', true, '{X1,X2}', 70.50, 1, 10, 'ab'),
            (2, '3541307', 'p2', '2026-03-31', 'B', NULL, NULL, NULL, NULL, NULL, NULL),
            (3, '3541307', 'p3', '2026-04-01', 'A', false, '{}', 1, 1, 1, 'cd'),
            (4, '1100015', 'p4', '2026-03-10', 'A', true, '{}', 1, 1, 1, 'ef'),
            (5, '3541307', 'p5', '2026-03-12', 'Z', true, '{}', 1, 1, 1, 'gh');
        CREATE TABLE test_person (
            id bigint PRIMARY KEY, municipio text NOT NULL, pessoa text NOT NULL,
            nascimento date NOT NULL);
        INSERT INTO test_person VALUES
            (10, '3541307', 'p1', '2024-02-29'), (11, '3541307', 'p2', '2024-12-31'),
            (12, '3541307', 'p3', '2023-12-31'), (13, '1100015', 'p4', '2024-05-05');";

    /// The extract of `test_event` (March, codes A and B) and `test_person` (born in 2024) over
    /// `SCHEMA`: every decodable type, NULLs, and the descriptor's column order.
    const TWO_PARTS_LINES: [&str; 4] = [
        r#"{"part":0,"kind":"care_event","record":{"source_entity_type":"test_event","source_record_id":"1","municipality_ibge":"3541307","person_key":"p1","event_date":"2026-03-05","active":true,"codes":["X1","X2"],"weight_kg":"70.50","small_count":"1","mid_count":"10","letter":"ab"}}"#,
        r#"{"part":0,"kind":"care_event","record":{"source_entity_type":"test_event","source_record_id":"2","municipality_ibge":"3541307","person_key":"p2","event_date":"2026-03-31","active":null,"codes":null,"weight_kg":null,"small_count":null,"mid_count":null,"letter":null}}"#,
        r#"{"part":1,"kind":"person","record":{"source_entity_type":"test_person","source_record_id":"10","municipality_ibge":"3541307","person_key":"p1","birth_date":"2024-02-29"}}"#,
        r#"{"part":1,"kind":"person","record":{"source_entity_type":"test_person","source_record_id":"11","municipality_ibge":"3541307","person_key":"p2","birth_date":"2024-12-31"}}"#,
    ];

    fn budget(max_rows: i64, max_payload_bytes: i64) -> Budget {
        Budget {
            max_rows,
            max_duration_ms: 60_000,
            max_payload_bytes,
        }
    }

    /// One acquisition's read, as `acquire` runs it: a read-only repeatable-read transaction,
    /// a fresh extract file.
    fn read(
        client: &mut postgres::Client,
        parts: &[PlannedPart],
        budget: &Budget,
        interrupts: &Interrupts,
    ) -> (Outcome, ExtractSink, PathBuf) {
        let (mut sink, path) = temp_sink("read", 1_048_576);
        let mut txn = client
            .build_transaction()
            .read_only(true)
            .isolation_level(IsolationLevel::RepeatableRead)
            .start()
            .unwrap();
        let outcome = read_parts(
            &mut txn,
            parts,
            MUNICIPALITY,
            budget,
            Instant::now(),
            interrupts,
            &mut sink,
        )
        .unwrap();
        txn.rollback().unwrap();
        (outcome, sink, path)
    }

    /// `read`, keeping only the outcome: the extract file is discarded.
    fn outcome_of(
        client: &mut postgres::Client,
        parts: &[PlannedPart],
        budget: &Budget,
        interrupts: &Interrupts,
    ) -> Outcome {
        let (outcome, sink, path) = read(client, parts, budget, interrupts);
        drop(sink);
        std::fs::remove_dir_all(path.parent().unwrap()).ok();
        outcome
    }

    fn stopped(
        client: &mut postgres::Client,
        parts: &[PlannedPart],
        budget: &Budget,
    ) -> StreamOutcome {
        match outcome_of(client, parts, budget, &Interrupts::default()) {
            Outcome::Stopped(stopped) => stopped,
            other => panic!("expected a stopped read, got {other:?}"),
        }
    }

    fn contract_failure(client: &mut postgres::Client, sql: &str) -> (&'static str, String) {
        match outcome_of(
            client,
            &[event_part(sql)],
            &budget(100, 1_000_000),
            &Interrupts::default(),
        ) {
            Outcome::Contract { code, detail } => (code, detail),
            other => panic!("expected a contract failure, got {other:?}"),
        }
    }

    #[test]
    fn reads_typed_parts_against_postgres() {
        let Some(postgres) = test_postgres::Postgres::start() else {
            return;
        };
        let mut client = postgres.connect().unwrap();
        client.batch_execute(SCHEMA).unwrap();

        two_parts_become_typed_lines_and_a_complete_message(&mut client);
        every_part_reads_the_same_snapshot(&postgres, &mut client);
        numeric_float_and_timestamp_columns_are_refused(&mut client);
        a_query_that_differs_from_its_descriptor_is_refused(&mut client);
        records_outside_the_contract_or_the_scope_are_refused(&mut client);
        the_budgets_are_cumulative_across_parts(&mut client);
        cancellation_and_source_failures_stop_the_read(&mut client);
    }

    fn two_parts_become_typed_lines_and_a_complete_message(client: &mut postgres::Client) {
        let parts = [event_part(EVENT_SQL), person_part(1)];
        let (outcome, sink, path) = read(
            client,
            &parts,
            &budget(100, 1_000_000),
            &Interrupts::default(),
        );
        assert!(matches!(outcome, Outcome::Complete(ref counts) if counts == &[2, 2]));
        let (complete, exit_code) = outcome_message(outcome, sink, &parts);
        assert_eq!(exit_code, 0);

        assert_eq!(gunzip_lines(&path), TWO_PARTS_LINES);
        let raw = std::fs::read(&path).unwrap();
        assert_eq!(
            complete,
            json!({
                "type": "complete", "row_count": 4, "exclusion_count": 0,
                "checksum": crate::hex_encode(sha2::Sha256::digest(&raw)),
                "compressed_bytes": raw.len(),
                "part_row_counts": [
                    {"part": 0, "capability": "test_event", "kind": "care_event", "row_count": 2},
                    {"part": 1, "capability": "test_person", "kind": "person", "row_count": 2},
                ],
            })
        );
        std::fs::remove_dir_all(path.parent().unwrap()).ok();
    }

    /// The parts run inside the caller's transaction: a row committed after its snapshot was
    /// taken is in no part (§1.9.3).
    fn every_part_reads_the_same_snapshot(
        postgres: &test_postgres::Postgres,
        client: &mut postgres::Client,
    ) {
        let (mut sink, path) = temp_sink("snapshot", 1_048_576);
        let mut txn = client
            .build_transaction()
            .read_only(true)
            .isolation_level(IsolationLevel::RepeatableRead)
            .start()
            .unwrap();
        txn.query_one("SELECT 1", &[]).unwrap();
        let mut writer = postgres.connect().unwrap();
        writer
            .batch_execute(
                "INSERT INTO test_event VALUES
                 (6, '3541307', 'p6', '2026-03-20', 'A', true, '{}', 1, 1, 1, 'ij')",
            )
            .unwrap();
        let outcome = read_parts(
            &mut txn,
            &[event_part(EVENT_SQL), person_part(1)],
            MUNICIPALITY,
            &budget(100, 1_000_000),
            Instant::now(),
            &Interrupts::default(),
            &mut sink,
        )
        .unwrap();
        txn.rollback().unwrap();
        assert!(matches!(outcome, Outcome::Complete(ref counts) if counts == &[2, 2]));
        writer
            .batch_execute("DELETE FROM test_event WHERE id = 6")
            .unwrap();
        std::fs::remove_dir_all(path.parent().unwrap()).ok();
    }

    fn numeric_float_and_timestamp_columns_are_refused(client: &mut postgres::Client) {
        for (column, replacement, expected) in [
            (
                "CAST(e.peso AS text) AS weight_kg",
                "e.peso AS weight_kg",
                "column weight_kg is numeric",
            ),
            (
                "CAST(e.peso AS text) AS weight_kg",
                "CAST(e.peso AS float8) AS weight_kg",
                "column weight_kg is float8",
            ),
            (
                "e.dia AS event_date",
                "CAST(e.dia AS timestamp) AS event_date",
                "column event_date is timestamp",
            ),
        ] {
            let (code, detail) = contract_failure(client, &EVENT_SQL.replace(column, replacement));
            assert_eq!(code, UNSUPPORTED_COLUMN_TYPE, "{detail}");
            assert!(detail.contains(expected), "{detail}");
        }
    }

    fn a_query_that_differs_from_its_descriptor_is_refused(client: &mut postgres::Client) {
        let cases = [
            (
                EVENT_SQL.replace("e.ativo AS active", "e.ativo AS letter_flag"),
                QUERY_CONTRACT_MISMATCH,
                "returns column letter_flag",
            ),
            (
                EVENT_SQL.replace("e.letra AS letter", "e.letra AS active"),
                QUERY_CONTRACT_MISMATCH,
                "returns column active twice",
            ),
            (
                EVENT_SQL.replace("e.dia >= ?", "e.dia >= CAST(? AS timestamp)"),
                QUERY_CONTRACT_MISMATCH,
                "bind 2 (period_start) is timestamp in the query, not date",
            ),
            (
                EVENT_SQL.replace("e.letra AS letter", "e.ativo AS letter"),
                UNSUPPORTED_COLUMN_TYPE,
                "column letter is declared text but the query returns bool",
            ),
            (
                EVENT_SQL.replace("e.codigos AS codes", "CAST(e.codigos AS text) AS codes"),
                UNSUPPORTED_COLUMN_TYPE,
                "column codes is declared text[] but the query returns text",
            ),
        ];
        for (sql, expected_code, expected_detail) in cases {
            let (code, detail) = contract_failure(client, &sql);
            assert_eq!(code, expected_code, "{detail}");
            assert!(detail.contains(expected_detail), "{detail}");
        }
        let missing = EVENT_SQL.replace("       e.medio AS mid_count,\n", "");
        let (code, detail) = contract_failure(client, &missing);
        assert_eq!(code, QUERY_CONTRACT_MISMATCH);
        assert!(
            detail.contains("does not return column mid_count"),
            "{detail}"
        );
    }

    fn records_outside_the_contract_or_the_scope_are_refused(client: &mut postgres::Client) {
        let cases = [
            (
                EVENT_SQL.replace("e.pessoa AS person_key", "NULLIF(e.pessoa, 'p2') AS person_key"),
                "part 0 (test_event): required column person_key is empty",
            ),
            (
                EVENT_SQL.replace("e.municipio AS municipality_ibge", "'1100015' AS municipality_ibge"),
                "record does not match the bound acquisition scope: part 0 (test_event) has municipality_ibge 1100015",
            ),
            (
                EVENT_SQL.replace("e.dia AS event_date", "e.dia + 30 AS event_date"),
                "event_date outside [2026-03-01, 2026-04-01)",
            ),
            (
                EVENT_SQL.replace(
                    "e.codigos AS codes",
                    "CAST(ARRAY[e.letra, NULL] AS text[]) AS codes",
                ),
                "column codes holds a NULL array element",
            ),
        ];
        for (sql, expected) in cases {
            match stopped(client, &[event_part(&sql)], &budget(100, 1_000_000)) {
                StreamOutcome::InvalidRecord(detail) => {
                    assert!(detail.contains(expected), "{detail}");
                }
                other => panic!("expected an invalid record, got {other:?}"),
            }
        }
    }

    /// Each ceiling fits either part alone and is crossed only in the second one.
    fn the_budgets_are_cumulative_across_parts(client: &mut postgres::Client) {
        let parts = [event_part(EVENT_SQL), person_part(1)];
        match stopped(client, &parts, &budget(3, 1_000_000)) {
            StreamOutcome::BudgetExceeded(detail) => {
                assert_eq!(detail, "row ceiling exceeded: 4 > 3");
            }
            other => panic!("expected the row ceiling, got {other:?}"),
        }
        let bytes = |lines: &[&str]| lines.iter().map(|line| line.len()).sum::<usize>();
        let through_first_person = i64::try_from(bytes(&TWO_PARTS_LINES[..3])).unwrap();
        let ceiling = through_first_person - 1;
        // The second part alone fits too: only the running total crosses the ceiling.
        assert!(i64::try_from(bytes(&TWO_PARTS_LINES[2..])).unwrap() <= ceiling);
        match stopped(client, &parts, &budget(100, ceiling)) {
            StreamOutcome::BudgetExceeded(detail) => assert_eq!(
                detail,
                format!("payload byte ceiling exceeded: {through_first_person} > {ceiling}")
            ),
            other => panic!("expected the payload ceiling, got {other:?}"),
        }
        let mut no_time = budget(100, 1_000_000);
        no_time.max_duration_ms = -1;
        match stopped(client, &parts, &no_time) {
            StreamOutcome::BudgetExceeded(detail) => {
                assert!(detail.starts_with("duration ceiling exceeded"), "{detail}");
            }
            other => panic!("expected the duration ceiling, got {other:?}"),
        }
    }

    fn cancellation_and_source_failures_stop_the_read(client: &mut postgres::Client) {
        let interrupts = Interrupts::default();
        interrupts
            .cancel_requested
            .store(true, std::sync::atomic::Ordering::SeqCst);
        let outcome = outcome_of(
            client,
            &[event_part(EVENT_SQL)],
            &budget(100, 1_000_000),
            &interrupts,
        );
        assert!(matches!(
            outcome,
            Outcome::Stopped(StreamOutcome::Cancelled)
        ));

        let missing_table = EVENT_SQL.replace("FROM test_event e", "FROM no_such_table e");
        match stopped(
            client,
            &[person_part(0), event_part(&missing_table)],
            &budget(100, 1_000_000),
        ) {
            StreamOutcome::Failed { sqlstate, .. } => {
                assert_eq!(sqlstate.as_deref(), Some("42P01"));
            }
            other => panic!("expected the source's error, got {other:?}"),
        }
    }
}
