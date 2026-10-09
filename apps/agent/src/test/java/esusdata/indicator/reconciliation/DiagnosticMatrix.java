package esusdata.indicator.reconciliation;

import esusdata.indicator.model.Quadrimestre;
import esusdata.indicator.reconciliation.Comparison.RowResult;
import esusdata.run.worker.SourceIdentity;
import java.time.Instant;
import java.util.List;
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
 */
record DiagnosticMatrix(Instant generatedAt, SourceIdentity source, List<PeriodExecutionPlan> plan, List<Row> rows) {

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
        private static String redacted(String text) {
            String clean = text == null ? "" : text.replaceAll(LONG_NUMBER, "<n>");
            return clean.length() > REASON_LIMIT ? clean.substring(0, REASON_LIMIT) + "..." : clean;
        }
    }

    DiagnosticMatrix {
        Objects.requireNonNull(generatedAt, "generatedAt");
        Objects.requireNonNull(source, "source");
        plan = List.copyOf(plan);
        rows = List.copyOf(rows);
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
