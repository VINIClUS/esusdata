package br.gov.observatorioaps.sourceconnector.domain;

import br.gov.observatorioaps.extractionstore.domain.ExtractionManifest;

/**
 * Reads one immutable window of the PEC and writes a finalized extract — the seam
 * {@code jobrunner.application.IndicatorRunExecutor} calls instead of touching JDBC, the PEC
 * driver, or any adapter infrastructure directly. {@code jobrunner} owns the fila/generation/
 * retry control plane and never implements this port itself; the production implementation
 * ({@code JdbcAcquisitionAdapter}) lives in {@code sourceconnector.infrastructure.jdbc} today and
 * is where a future out-of-process execution plane would be substituted, unchanged on this side.
 *
 * <p>Declares no checked exception: a connection failure, a probe/streaming failure, or a local
 * write failure all surface as unchecked types from {@code sourceconnector.domain} (see {@link
 * PecAcquisitionException}), or — for cancellation and budget overrun — as the same unchecked
 * types those conditions already use elsewhere in the codebase, so downstream classification
 * (job-runner's {@code FailureClassifier}) keeps working unchanged.
 */
public interface AcquisitionPort {

    ExtractionManifest acquire(
            AcquisitionCommand command,
            CancellationSignal cancellation,
            AcquisitionListener listener);
}
