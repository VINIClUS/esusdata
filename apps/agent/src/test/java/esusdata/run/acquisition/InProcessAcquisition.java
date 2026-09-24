package esusdata.run.acquisition;

import esusdata.indicator.model.CanonicalEncounter;
import esusdata.indicator.model.CanonicalModality;
import esusdata.indicator.model.SourceRef;
import esusdata.run.extract.ExtractWriter;
import esusdata.run.extract.ExtractionManifest;
import esusdata.source.pec.CompatibilityCatalog;
import esusdata.source.pec.IndividualEncounterModalityCapability;
import esusdata.source.pec.JdbcCompatibilityCatalog;
import esusdata.source.pec.PecAcquisition;
import esusdata.source.pec.PecDataSourceFactory;
import esusdata.source.pec.PecSourceConnection;
import esusdata.source.pec.RawEncounterRecord;
import java.io.IOException;
import java.nio.file.Path;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;

/**
 * The in-process JDBC {@link Acquisition}: opens a source-bound connection, validates
 * compatibility, streams the frozen capability query, and writes/finalizes a fresh extract.
 * Test-only since ADR 0016 — production acquires exclusively through {@link ExecPlaneAcquisition};
 * this class remains as the independent JDBC reading the differential tests compare the Rust
 * child against, and as the acquisition the job-runner fixtures drive without a compiled binary.
 */
public final class InProcessAcquisition implements Acquisition {

    private final PecDataSourceFactory pecDataSourceFactory;
    private final Path extractsBaseDir;
    private final Clock clock;
    private final CompatibilityCatalog catalog;

    public InProcessAcquisition(PecDataSourceFactory pecDataSourceFactory, Path extractsBaseDir, Clock clock) {
        this(pecDataSourceFactory, extractsBaseDir, clock, new JdbcCompatibilityCatalog());
    }

    /**
     * Injects the compatibility probe — mirrors {@code IndividualEncounterModalityCapability
     * .stream}'s own seam so a synthetic PostgreSQL fixture can supply probes for testing without
     * ever weakening the validation a production run performs (ENG-43: production always
     * constructs this class with the two-argument constructor, which always uses the real {@link
     * JdbcCompatibilityCatalog}).
     */
    public InProcessAcquisition(
            PecDataSourceFactory pecDataSourceFactory,
            Path extractsBaseDir,
            Clock clock,
            CompatibilityCatalog catalog) {
        this.pecDataSourceFactory = pecDataSourceFactory;
        this.extractsBaseDir = extractsBaseDir;
        this.clock = clock;
        this.catalog = catalog;
    }

    @Override
    public ExtractionManifest acquire(
            AcquisitionCommand command, CancellationSignal cancellation, AcquisitionListener listener) {
        Instant startedAt = clock.instant();
        try {
            ExtractionManifest manifest;
            try (PecSourceConnection sourceConnection = pecDataSourceFactory.open(
                    command.connectionProperties(), command.sourceIdentity(), command.budget())) {
                listener.onProgress();
                PecAcquisition acquisition =
                        sourceConnection.acquire(command.periodStart(), command.periodEndExclusive());
                try (ExtractWriter writer = new ExtractWriter(extractsBaseDir, command.extractionId(), acquisition)) {
                    try {
                        IndividualEncounterModalityCapability.stream(
                                acquisition,
                                raw -> writeCanonical(writer, acquisition, raw),
                                catalog,
                                statement -> cancellation.bindInterrupt(cancelInterrupt(statement)),
                                cancellation::checkCancelled);
                    } catch (RuntimeException uncertainFailure) {
                        // Rethrown unchanged (not wrapped): JobWorker/FailureClassifier dispatch on
                        // the concrete type (JobCancelledException, SourceBudgetExceededException,
                        // IllegalStateException...) — only the uncertain-outcome side effect is new.
                        listener.onUncertainOutcome(uncertainOutcomeMessage(command, uncertainFailure));
                        throw uncertainFailure;
                    } catch (SQLException uncertainFailure) {
                        listener.onUncertainOutcome(uncertainOutcomeMessage(command, uncertainFailure));
                        throw new PecAcquisitionException(uncertainFailure.getMessage(), uncertainFailure);
                    }
                    manifest = writer.finalizeExtract(
                            startedAt,
                            command.sourceZoneId(),
                            IndividualEncounterModalityCapability.QUERY_CHECKSUM,
                            IndividualEncounterModalityCapability.ADAPTER_VERSION,
                            "COMPLETE",
                            "SNAPSHOT");
                }
            }
            listener.onProgress();
            return manifest;
        } catch (SQLException | IOException failure) {
            // Reached only for a connection that never became live (open() itself) or a local
            // write failure outside the stream() call above — neither is "uncertain" in the ENG-51
            // sense, since no live PEC session either existed or could still be executing.
            throw new PecAcquisitionException(
                    "acquisition " + command.extractionId() + " failed: " + failure.getMessage(), failure);
        } finally {
            cancellation.unbindInterrupt();
        }
    }

    private static String uncertainOutcomeMessage(AcquisitionCommand command, Throwable failure) {
        return "acquisition " + command.extractionId() + " ended a live PEC read with an uncertain outcome: " + failure;
    }

    private static Runnable cancelInterrupt(PreparedStatement statement) {
        return () -> {
            try {
                statement.cancel();
            } catch (SQLException ignored) {
                // Best-effort only — some drivers/states do not support statement cancellation.
            }
        };
    }

    private static void writeCanonical(ExtractWriter writer, PecAcquisition acquisition, RawEncounterRecord raw) {
        CanonicalModality modality = switch (raw.modality()) {
            case PROGRAMADO -> CanonicalModality.PROGRAMADO;
            case ESPONTANEO -> CanonicalModality.ESPONTANEO;
            case UNMAPPED -> CanonicalModality.UNMAPPED;
        };
        CanonicalEncounter canonical = new CanonicalEncounter(
                new SourceRef(acquisition.sourceId(), "tb_fat_atendimento_individual", String.valueOf(raw.pk())),
                acquisition.municipalityIbge(),
                raw.careDate().toString(),
                modality,
                raw.cnes(),
                raw.ine(),
                raw.cbo());
        try {
            writer.write(canonical);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
