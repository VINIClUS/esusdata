package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.reconciliation.Comparison.RowResult;
import esusdata.run.worker.SourceIdentity;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What the diagnostic run of the Portão D found, pack by period (spec 2026-10-08 §14): one {@link
 * Row} for every period, pack and captured reference revision, and the {@link PeriodExecutionPlan}
 * the rows follow, so a period the local source cannot run is a row and not an omission. Every
 * count of teams is masked as the summaries of the gate are ({@code <10}); D and T are shown as
 * they are; no INE, no team and no class by team is ever in it. It is a diagnostic by construction:
 * it names no registry, and its purpose is always {@link ReferencePurpose#DIAGNOSTIC}.
 *
 * @param generatedAt when the run ended
 * @param source the PEC the extracts were read from
 * @param plan what was decided for each period
 * @param rows the matrix
 * @param probes what each methodology probe of the catalog measured on each pack and period it ran on
 */
record DiagnosticMatrix(
        Instant generatedAt,
        SourceIdentity source,
        List<PeriodExecutionPlan> plan,
        List<Row> rows,
        List<ProbeRow> probes) {

    /** A cell is a verdict of the pack, or why there is none. */
    enum Cell {
        PASSED,
        FAILED,
        /** Nothing to compare: a gap in the reference, a pack with no reference, no evaluable row. */
        PENDING,
        /** The local source lacks months of the period, so nothing was computed. */
        MISSING_LOCAL_MONTHS,
        /** The cell could not be computed; the reason says why. The run fails after writing the matrix. */
        ERROR
    }

    /**
     * The figures of one team type, all masked but D and T.
     *
     * @param distance D, or {@code -} when the row was not evaluated
     * @param threshold T, or {@code -} when the row was not evaluated
     * @param line {@code passa}, {@code reprova} or {@code não avaliada}
     */
    record Figures(
            String teamType,
            String siapsTeams,
            String localTeams,
            String withoutLocalClass,
            String distance,
            String threshold,
            String line) {

        static Figures of(RowResult row) {
            return new Figures(
                    row.teamType(),
                    SummaryWriter.mask(row.siapsTeams()),
                    SummaryWriter.mask(row.localTeams()),
                    SummaryWriter.mask(row.semClasseLocal()),
                    row.evaluated() ? Integer.toString(row.distance()) : Row.NONE,
                    row.evaluated() ? Integer.toString(row.threshold()) : Row.NONE,
                    lineOf(row));
        }

        private static String lineOf(RowResult row) {
            if (!row.evaluated()) {
                return "não avaliada";
            }
            return row.passed() ? "passa" : "reprova";
        }
    }

    /**
     * One pack of one period against one captured revision of the official reference.
     *
     * @param pack {@code C1} to {@code C7} or {@code CIII}
     * @param referenceId the revision compared, or {@code -} when none was captured
     * @param reason why the cell is not a plain pass or fail; empty otherwise. Never a value of the data.
     * @param figures one entry per team type compared; empty when nothing was
     * @param notInSiaps the masked count of local teams the official list does not have, or {@code -}
     * @param fingerprint the {@code InputFingerprint} of the local extracts, or empty when none was read
     */
    record Row(
            Quadrimestre period,
            String pack,
            String referenceId,
            Cell status,
            String reason,
            List<Figures> figures,
            String notInSiaps,
            String fingerprint) {

        static final String NONE = "-";

        private static final int REASON_LIMIT = 300;
        private static final String LONG_NUMBER = "\\d{10,}";

        Row {
            Objects.requireNonNull(period, "period");
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(referenceId, "referenceId");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(reason, "reason");
            figures = List.copyOf(figures);
            Objects.requireNonNull(notInSiaps, "notInSiaps");
            Objects.requireNonNull(fingerprint, "fingerprint");
        }

        /** The cell of a verdict of the pack. */
        static Row of(Quadrimestre period, String referenceId, PackVerdict verdict) {
            boolean compared = !verdict.rows().isEmpty();
            return new Row(
                    period,
                    verdict.pack().code(),
                    referenceId,
                    Cell.valueOf(verdict.status().name()),
                    redacted(verdict.reason()),
                    verdict.rows().stream().map(Figures::of).toList(),
                    compared ? SummaryWriter.mask(verdict.localNotInSiaps()) : NONE,
                    verdict.localSourceFingerprint());
        }

        /** A cell with no figures: {@code status} because of {@code reason}. */
        static Row without(Quadrimestre period, GatePack pack, String referenceId, Cell status, String reason) {
            return new Row(period, pack.code(), referenceId, status, redacted(reason), List.of(), NONE, "");
        }

        /** A cell that could not be computed: the kind of failure and its message, with no long number in it. */
        static Row failed(Quadrimestre period, GatePack pack, String referenceId, Exception failure) {
            return without(
                    period,
                    pack,
                    referenceId,
                    Cell.ERROR,
                    failure.getClass().getSimpleName() + ": " + failure.getMessage());
        }

        /** A cell for a pack that has no captured reference in the period. */
        static Row absent(Quadrimestre period, GatePack pack) {
            return without(period, pack, NONE, Cell.PENDING, "nenhuma referência capturada para este pack e período");
        }

        /** Shortened, and any run of ten or more digits (an INE could be one) replaced. */
        static String redacted(String text) {
            String clean = text == null ? "" : text.replaceAll(LONG_NUMBER, "<n>");
            return clean.length() > REASON_LIMIT ? clean.substring(0, REASON_LIMIT) + "..." : clean;
        }
    }

    /**
     * What one methodology probe measured on one pack of one period (spec §9.4), in the only form
     * that may leave the machine: {@link ProbeResult#versionedForm()}, masked counts and no INE. The
     * probe's local detail travels in {@code localDetail}, which the matrix files never carry.
     *
     * @param result the versioned form of the result, or of the failure ({@code observability} {@code
     *     ERROR} and a redacted reason)
     * @param localDetail local-only lines, possibly per team
     */
    record ProbeRow(
            Quadrimestre period,
            String pack,
            String referenceId,
            Map<String, String> result,
            List<String> localDetail) {

        static final String ERROR = "ERROR";

        ProbeRow {
            Objects.requireNonNull(period, "period");
            Objects.requireNonNull(pack, "pack");
            Objects.requireNonNull(referenceId, "referenceId");
            result = Collections.unmodifiableMap(new LinkedHashMap<>(result));
            localDetail = List.copyOf(localDetail);
        }

        static ProbeRow of(Quadrimestre period, String pack, String referenceId, ProbeResult result) {
            return new ProbeRow(period, pack, referenceId, result.versionedForm(), result.localDetail());
        }

        /** A probe that could not run: the kind of failure and its message, with no long number in it. */
        static ProbeRow failed(
                Quadrimestre period, String pack, String referenceId, String probeId, RuntimeException failure) {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("probe_id", probeId);
            form.put("observability", ERROR);
            form.put("reason", Row.redacted(failure.getClass().getSimpleName() + ": " + failure.getMessage()));
            return new ProbeRow(period, pack, referenceId, form, List.of());
        }

        boolean failed() {
            return ERROR.equals(result.get("observability"));
        }

        /** The versioned form only: a log line or an assertion message must not carry the local detail. */
        @Override
        public String toString() {
            return "ProbeRow[" + SiapsFormats.quadrimestre(period) + " " + pack + " " + referenceId + " " + result
                    + "]";
        }
    }

    DiagnosticMatrix {
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(source, "source");
        plan = List.copyOf(plan);
        rows = List.copyOf(rows);
        probes = List.copyOf(probes);
    }

    /** The probes that could not run. */
    List<ProbeRow> probeErrors() {
        return probes.stream().filter(ProbeRow::failed).toList();
    }

    /** Always {@link ReferencePurpose#DIAGNOSTIC}. */
    ReferencePurpose purpose() {
        return ReferencePurpose.DIAGNOSTIC;
    }

    /** The cells that could not be computed. */
    List<Row> errors() {
        return rows.stream().filter(row -> row.status() == Cell.ERROR).toList();
    }

    /** How many cells have this status. */
    long count(Cell status) {
        return rows.stream().filter(row -> row.status() == status).count();
    }
}
