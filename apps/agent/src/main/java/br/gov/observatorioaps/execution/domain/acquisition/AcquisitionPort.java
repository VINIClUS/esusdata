package br.gov.observatorioaps.execution.domain.acquisition;

import br.gov.observatorioaps.execution.domain.extract.ExtractionManifest;

/**
 * Reads one immutable window of the PEC and writes a finalized extract — the seam
 * {@code execution.application.IndicatorRunExecutor} calls instead of touching JDBC, the PEC
 * driver, or any adapter infrastructure directly. {@code execution.application} owns the
 * fila/generation/retry control plane and never implements this port itself; the production
 * implementation ({@code JdbcAcquisitionAdapter}) lives in {@code execution.adapter.out.pec} today and
 * is where a future out-of-process execution plane would be substituted, unchanged on this side.
 *
 * <p>Declares no checked exception: a connection failure, a probe/streaming failure, or a local
 * write failure all surface as unchecked types from {@code execution.domain.acquisition} (see {@link
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
