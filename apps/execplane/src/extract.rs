//! Generates the extract's data file (fatia 3 / ADR 0011): gzip, SHA-256 over the compressed
//! bytes, the compressed-byte ceiling, free-space reservation, and per-record validation. This is
//! the entire byte-level pipeline `ExtractWriter.java` used to own on the JDBC path — ported here
//! marker-by-marker, layer-by-layer (gzip -> digest -> bounded -> file, same order as Java's
//! `GZIPOutputStream(DigestOutputStream(BoundedOutputStream(file)))`).
//!
//! Java keeps the `.extract.lock`, `ExtractRecovery.reconcile`, the manifest, and the atomic
//! hard-link publication (`DelegatedExtractPublication`) — this module never touches any of
//! those. Java's own pre-publication check is metadata/integrity only (file size, counts, and a
//! raw SHA-256 over the `.tmp` file's bytes) — it does not re-parse or re-validate every record,
//! so the out-of-scope guarantee this module's `validate` enforces is now the only *write-time*
//! one there is (ADR 0011 supersedes ADR 0010 on this point). The *read-time* guarantee is
//! unchanged: Java's `ExtractReader`/`ExtractValidation.validateRecord` still rejects any
//! out-of-scope record unconditionally, before calculation, regardless of who wrote the file.

use chrono::NaiveDate;
use serde::Serialize;
use sha2::{Digest, Sha256};
use std::fs::{File, OpenOptions};
use std::io::{self, Write};
use std::path::Path;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;

#[cfg(unix)]
use std::os::unix::fs::OpenOptionsExt;

/// Reserve headroom beyond the configured ceiling, mirroring `ExtractWriter.ensureTempSpace`'s
/// own 1 MiB margin — the last write must never be allowed to exactly exhaust the file store.
const SPACE_MARGIN_BYTES: u64 = 1_048_576;

/// One canonical Atendimento event, wire-identical to Java's `CanonicalEncounter` record —
/// field-for-field, camelCase. `ExtractReader`'s mapper is a bare `new ObjectMapper()`
/// (fails on unknown properties): an extra or misnamed field here would make every line
/// unreadable, not just the record that's wrong. Shared between `stream.rs` (builds one per row)
/// and this module (validates and serializes it) — there is exactly one definition.
#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct Encounter {
    pub source_ref: SourceRef,
    pub municipality_ibge: String,
    pub care_date: String,
    pub modality: &'static str,
    pub cnes: Option<String>,
    pub ine: Option<String>,
    pub cbo: Option<String>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
pub struct SourceRef {
    pub source_id: String,
    pub entity_type: String,
    pub record_id: String,
}

/// The source, municipality, and period one extract is bound to — the Rust mirror of
/// `ExtractionScope`'s authority inside `ExtractWriter.validateRecordForWrite`. This is the
/// deviation ADR 0011 records: the write-time scope check that ADR 0010 kept in Java now runs
/// here, because Rust is the one holding the bytes as they're produced.
pub struct Scope {
    pub source_id: String,
    pub municipality_ibge: String,
    pub period_start: NaiveDate,
    pub period_end_exclusive: NaiveDate,
}

impl Scope {
    fn contains(&self, source_id: &str, municipality_ibge: &str, care_date: NaiveDate) -> bool {
        self.source_id == source_id
            && self.municipality_ibge == municipality_ibge
            && care_date >= self.period_start
            && care_date < self.period_end_exclusive
    }
}

pub struct Completion {
    pub row_count: i64,
    pub exclusion_count: i64,
    pub checksum: String,
    pub compressed_bytes: i64,
}

#[derive(Debug)]
pub enum ExtractError {
    /// A record failed the port of `validateRecordForWrite` (scope, blank fields, malformed
    /// date, ...) — mirrors Java's `IllegalArgumentException` on the same check.
    InvalidRecord(String),
    /// The compressed-byte ceiling or the file store's free space was exceeded — mirrors
    /// `SourceBudgetExceededException`.
    BudgetExceeded(String),
    /// Any other I/O failure opening, writing, or finishing the file.
    Io(String),
}

/// Carries a budget-exceeded classification up through `io::Error` — the only way for
/// `BoundedWriter::write` (which must return `io::Result`, since it implements `Write`) to signal
/// anything richer than a generic I/O failure to callers above `flate2::GzEncoder`. Verified
/// empirically that `GzEncoder::write_all` propagates this marker through unchanged, downcastable
/// via `io::Error::get_ref` on the far side — flate2 does not wrap or discard the inner error.
#[derive(Debug)]
struct BudgetMarker(String);

impl std::fmt::Display for BudgetMarker {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "{}", self.0)
    }
}

impl std::error::Error for BudgetMarker {}

impl From<io::Error> for ExtractError {
    fn from(err: io::Error) -> Self {
        match err.get_ref().and_then(|inner| inner.downcast_ref::<BudgetMarker>()) {
            Some(marker) => ExtractError::BudgetExceeded(marker.0.clone()),
            None => ExtractError::Io(err.to_string()),
        }
    }
}

/// Counts bytes passed through and enforces `max_bytes` before writing — the Rust mirror of
/// `ExtractWriter.BoundedOutputStream`. Sits directly above the file, so it counts and bounds
/// exactly the compressed bytes landing on disk, matching Java's layering.
struct BoundedWriter {
    file: File,
    max_bytes: u64,
    written: Arc<AtomicU64>,
}

impl Write for BoundedWriter {
    fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
        let current = self.written.load(Ordering::Relaxed);
        let remaining = self.max_bytes.saturating_sub(current);
        if buf.len() as u64 > remaining {
            return Err(io::Error::new(
                io::ErrorKind::Other,
                BudgetMarker(format!(
                    "temporary extract byte ceiling exceeded: {} > {}",
                    current.saturating_add(buf.len() as u64),
                    self.max_bytes
                )),
            ));
        }
        let n = self.file.write(buf)?;
        self.written.fetch_add(n as u64, Ordering::Relaxed);
        Ok(n)
    }

    fn flush(&mut self) -> io::Result<()> {
        self.file.flush()
    }
}

/// Hashes exactly the bytes that reach the file — the compressed stream, not the JSON lines
/// `ExtractSink::write` receives. Mirrors `DigestOutputStream` sitting directly above
/// `BoundedOutputStream` in `ExtractWriter`.
struct DigestWriter<W> {
    inner: W,
    hasher: Sha256,
}

impl<W: Write> Write for DigestWriter<W> {
    fn write(&mut self, buf: &[u8]) -> io::Result<usize> {
        let n = self.inner.write(buf)?;
        self.hasher.update(&buf[..n]);
        Ok(n)
    }

    fn flush(&mut self) -> io::Result<()> {
        self.inner.flush()
    }
}

pub struct ExtractSink {
    gz: flate2::write::GzEncoder<DigestWriter<BoundedWriter>>,
    written: Arc<AtomicU64>,
    max_bytes: u64,
    base_dir: std::path::PathBuf,
    scope: Scope,
    row_count: i64,
    exclusion_count: i64,
}

impl ExtractSink {
    /// Opens `temp_path` with create-new semantics (never overwrites, and — per POSIX `open(2)`,
    /// verified against the manpage — refuses a pre-existing symlink at that path too, since
    /// `O_EXCL` fails on any existing directory entry regardless of its type) and owner-only
    /// permissions on Unix, mirroring `ExtractWriter.createOwnerOnlyFile` +
    /// `ExtractValidation.rejectSymbolicLink`. Reserves free space up front, exactly like
    /// `ExtractWriter`'s constructor calling `ensureTempSpace(baseDir, maxTempFileBytes)` before
    /// creating its own temp file.
    pub fn open(temp_path: &Path, max_temp_file_bytes: i64, scope: Scope) -> Result<Self, ExtractError> {
        if max_temp_file_bytes <= 0 {
            return Err(ExtractError::Io("maxTempFileBytes must be positive".to_string()));
        }
        let max_bytes = max_temp_file_bytes as u64;
        let base_dir = temp_path
            .parent()
            .ok_or_else(|| ExtractError::Io("extract temp path has no parent directory".to_string()))?
            .to_path_buf();
        ensure_free_space(&base_dir, max_bytes)?;

        let mut options = OpenOptions::new();
        options.write(true).create_new(true);
        #[cfg(unix)]
        options.mode(0o600);
        let file = options
            .open(temp_path)
            .map_err(|e| ExtractError::Io(format!("could not create extract temp file: {e}")))?;

        let written = Arc::new(AtomicU64::new(0));
        let bounded = BoundedWriter { file, max_bytes, written: Arc::clone(&written) };
        let digest = DigestWriter { inner: bounded, hasher: Sha256::new() };
        let gz = flate2::write::GzEncoder::new(digest, flate2::Compression::default());

        Ok(ExtractSink { gz, written, max_bytes, base_dir, scope, row_count: 0, exclusion_count: 0 })
    }

    /// Ports `ExtractWriter.write` in full: validates the record against the bound scope, checks
    /// remaining free disk space (the store can fill up mid-extract, not only at `open`), then
    /// serializes exactly `Encounter`'s fields and writes them through the gzip/digest/bounded
    /// chain.
    pub fn write(&mut self, encounter: &Encounter) -> Result<(), ExtractError> {
        validate(encounter, &self.scope)?;
        let remaining = self.max_bytes.saturating_sub(self.written.load(Ordering::Relaxed));
        ensure_free_space(&self.base_dir, remaining)?;

        let mut line = serde_json::to_vec(encounter)
            .map_err(|e| ExtractError::Io(format!("could not serialize encounter: {e}")))?;
        line.push(b'\n');
        self.gz.write_all(&line)?;

        self.row_count += 1;
        if encounter.modality == "UNMAPPED" {
            self.exclusion_count += 1;
        }
        Ok(())
    }

    /// Closes the gzip stream, fsyncs, and returns the manifest-bound counts and checksum. Never
    /// touches the lock, the manifest, or publication — Java hard-links this same `.tmp` path
    /// into place after its own metadata/integrity check (ADR 0011).
    pub fn finish(self) -> Result<Completion, ExtractError> {
        let row_count = self.row_count;
        let exclusion_count = self.exclusion_count;
        let digest_writer = self.gz.finish()?;
        let checksum = crate::hex_encode(digest_writer.hasher.finalize());
        digest_writer
            .inner
            .file
            .sync_all()
            .map_err(|e| ExtractError::Io(format!("could not fsync extract temp file: {e}")))?;
        let compressed_bytes = self.written.load(Ordering::Relaxed) as i64;
        Ok(Completion { row_count, exclusion_count, checksum, compressed_bytes })
    }
}

/// Ports `ExtractWriter.validateRecordForWrite` — every check that runs before a line is ever
/// serialized, in the same order, for the same reasons.
fn validate(encounter: &Encounter, scope: &Scope) -> Result<(), ExtractError> {
    let source_ref = &encounter.source_ref;
    if is_blank(&source_ref.source_id) || is_blank(&source_ref.entity_type) || is_blank(&source_ref.record_id) {
        return Err(ExtractError::InvalidRecord("encounter source reference is incomplete".to_string()));
    }
    if !is_seven_digit_ibge(&encounter.municipality_ibge) {
        return Err(ExtractError::InvalidRecord(
            "encounter municipality must be a 7-digit IBGE code".to_string(),
        ));
    }
    let care_date = NaiveDate::parse_from_str(&encounter.care_date, "%Y-%m-%d")
        .map_err(|_| ExtractError::InvalidRecord("encounter careDate must be an ISO local date".to_string()))?;
    // Every row this process ever builds already carries the envelope's fixed source_id/
    // municipality_ibge (there is exactly one query per child invocation, one acquisition, one
    // scope) — so "all records in an extract must use one sourceId" (Java's other invariant here)
    // is trivially true and isn't separately tracked. The period bound below is the real check:
    // it's what stops a malformed or hostile row (wrong care_date) from ever being written.
    if !scope.contains(&source_ref.source_id, &encounter.municipality_ibge, care_date) {
        return Err(ExtractError::InvalidRecord("record does not match the bound acquisition scope".to_string()));
    }
    for optional in [&encounter.cnes, &encounter.ine, &encounter.cbo] {
        if let Some(value) = optional {
            if is_blank(value) {
                return Err(ExtractError::InvalidRecord("encounter optional fields cannot be blank".to_string()));
            }
        }
    }
    Ok(())
}

fn is_blank(value: &str) -> bool {
    value.trim().is_empty()
}

fn is_seven_digit_ibge(value: &str) -> bool {
    value.len() == 7 && value.chars().all(|c| c.is_ascii_digit())
}

/// Mirrors `ExtractWriter.ensureTempSpace`: `remaining_budget` is the number of bytes still
/// allowed under the ceiling (the full ceiling at `open`, `max_bytes - written` at each `write`),
/// and the file store's available space must cover that plus a 1 MiB margin.
fn ensure_free_space(base_dir: &Path, remaining_budget: u64) -> Result<(), ExtractError> {
    let reserve = remaining_budget.saturating_add(SPACE_MARGIN_BYTES);
    let usable = fs4::available_space(base_dir)
        .map_err(|e| ExtractError::Io(format!("could not read file store space: {e}")))?;
    if usable < reserve {
        return Err(ExtractError::BudgetExceeded(format!(
            "insufficient free space for the temporary extract: {usable} < {reserve} bytes reserved"
        )));
    }
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    fn scope() -> Scope {
        Scope {
            source_id: "src-1".to_string(),
            municipality_ibge: "3541307".to_string(),
            period_start: NaiveDate::from_ymd_opt(2026, 3, 1).unwrap(),
            period_end_exclusive: NaiveDate::from_ymd_opt(2026, 4, 1).unwrap(),
        }
    }

    fn encounter(record_id: &str, care_date: &str, municipality_ibge: &str) -> Encounter {
        Encounter {
            source_ref: SourceRef {
                source_id: "src-1".to_string(),
                entity_type: "tb_fat_atendimento_individual".to_string(),
                record_id: record_id.to_string(),
            },
            municipality_ibge: municipality_ibge.to_string(),
            care_date: care_date.to_string(),
            modality: "PROGRAMADO",
            cnes: None,
            ine: None,
            cbo: None,
        }
    }

    #[test]
    fn accepts_a_record_inside_the_bound_scope() {
        assert!(validate(&encounter("1", "2026-03-15", "3541307"), &scope()).is_ok());
    }

    #[test]
    fn rejects_a_record_before_the_period_start() {
        assert!(matches!(
            validate(&encounter("1", "2026-02-28", "3541307"), &scope()),
            Err(ExtractError::InvalidRecord(msg)) if msg.contains("bound acquisition scope")
        ));
    }

    #[test]
    fn rejects_a_record_on_or_after_the_period_end() {
        assert!(matches!(
            validate(&encounter("1", "2026-04-01", "3541307"), &scope()),
            Err(ExtractError::InvalidRecord(_))
        ));
    }

    #[test]
    fn rejects_a_record_outside_the_bound_municipality() {
        assert!(matches!(
            validate(&encounter("1", "2026-03-15", "9999999"), &scope()),
            Err(ExtractError::InvalidRecord(_))
        ));
    }

    #[test]
    fn rejects_a_non_iso_care_date() {
        assert!(matches!(
            validate(&encounter("1", "15-03-2026", "3541307"), &scope()),
            Err(ExtractError::InvalidRecord(msg)) if msg.contains("ISO local date")
        ));
    }

    #[test]
    fn rejects_a_municipality_that_is_not_seven_digits() {
        assert!(matches!(
            validate(&encounter("1", "2026-03-15", "354130"), &scope()),
            Err(ExtractError::InvalidRecord(msg)) if msg.contains("7-digit")
        ));
    }

    #[test]
    fn rejects_a_blank_optional_field() {
        let mut e = encounter("1", "2026-03-15", "3541307");
        e.cnes = Some("   ".to_string());
        assert!(matches!(
            validate(&e, &scope()),
            Err(ExtractError::InvalidRecord(msg)) if msg.contains("optional fields")
        ));
    }

    #[test]
    fn rejects_an_incomplete_source_ref() {
        let mut e = encounter("1", "2026-03-15", "3541307");
        e.source_ref.record_id = "".to_string();
        assert!(matches!(
            validate(&e, &scope()),
            Err(ExtractError::InvalidRecord(msg)) if msg.contains("source reference")
        ));
    }

    #[test]
    fn round_trips_gzip_and_checksum_through_a_real_temp_file() {
        let dir = std::env::temp_dir().join(format!("execplane-extract-test-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let temp_path = dir.join(format!("rt-{}.jsonl.gz.tmp", rand_suffix()));

        let mut sink = ExtractSink::open(&temp_path, 1_048_576, scope()).unwrap();
        sink.write(&encounter("1", "2026-03-15", "3541307")).unwrap();
        let mut unmapped = encounter("2", "2026-03-16", "3541307");
        unmapped.modality = "UNMAPPED";
        sink.write(&unmapped).unwrap();
        let completion = sink.finish().unwrap();

        assert_eq!(completion.row_count, 2);
        assert_eq!(completion.exclusion_count, 1);
        assert_eq!(completion.checksum.len(), 64);

        // Independently recomputes the checksum over the file's raw bytes — the same integrity
        // check DelegatedExtractPublication runs Java-side before publishing.
        let raw = std::fs::read(&temp_path).unwrap();
        assert_eq!(raw.len() as i64, completion.compressed_bytes);
        let mut hasher = Sha256::new();
        hasher.update(&raw);
        assert_eq!(crate::hex_encode(hasher.finalize()), completion.checksum);

        // And gunzips to exactly the two JSON lines written.
        use std::io::Read;
        let mut decoder = flate2::read::GzDecoder::new(&raw[..]);
        let mut decompressed = String::new();
        decoder.read_to_string(&mut decompressed).unwrap();
        assert_eq!(decompressed.lines().count(), 2);

        std::fs::remove_file(&temp_path).ok();
        std::fs::remove_dir(&dir).ok();
    }

    #[test]
    fn refuses_to_exceed_the_compressed_byte_ceiling() {
        let dir = std::env::temp_dir().join(format!("execplane-extract-test-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let temp_path = dir.join(format!("ceiling-{}.jsonl.gz.tmp", rand_suffix()));

        // A ceiling far too small for even the gzip header plus one line.
        let mut sink = ExtractSink::open(&temp_path, 8, scope()).unwrap();
        let result = sink.write(&encounter("1", "2026-03-15", "3541307"));
        assert!(matches!(result, Err(ExtractError::BudgetExceeded(msg)) if msg.contains("ceiling")));

        std::fs::remove_file(&temp_path).ok();
        std::fs::remove_dir(&dir).ok();
    }

    #[test]
    fn refuses_to_open_over_an_existing_file() {
        let dir = std::env::temp_dir().join(format!("execplane-extract-test-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let temp_path = dir.join(format!("exists-{}.jsonl.gz.tmp", rand_suffix()));
        std::fs::write(&temp_path, b"already here").unwrap();

        let result = ExtractSink::open(&temp_path, 1_048_576, scope());
        assert!(matches!(result, Err(ExtractError::Io(_))));

        std::fs::remove_file(&temp_path).ok();
        std::fs::remove_dir(&dir).ok();
    }

    #[cfg(unix)]
    #[test]
    fn creates_the_temp_file_owner_only() {
        use std::os::unix::fs::PermissionsExt;
        let dir = std::env::temp_dir().join(format!("execplane-extract-test-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let temp_path = dir.join(format!("perms-{}.jsonl.gz.tmp", rand_suffix()));

        let sink = ExtractSink::open(&temp_path, 1_048_576, scope()).unwrap();
        let mode = std::fs::metadata(&temp_path).unwrap().permissions().mode() & 0o777;
        assert_eq!(mode, 0o600);
        drop(sink);

        std::fs::remove_file(&temp_path).ok();
        std::fs::remove_dir(&dir).ok();
    }

    fn rand_suffix() -> u64 {
        use std::time::{SystemTime, UNIX_EPOCH};
        SystemTime::now().duration_since(UNIX_EPOCH).unwrap().as_nanos() as u64
    }
}
