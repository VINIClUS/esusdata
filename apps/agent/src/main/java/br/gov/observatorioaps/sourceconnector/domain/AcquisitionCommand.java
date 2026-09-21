package br.gov.observatorioaps.sourceconnector.domain;

import java.time.LocalDate;

/**
 * Everything an {@link AcquisitionPort} needs to run one live acquisition — assembled by
 * {@code jobrunner.application.IndicatorRunExecutor} from the job's {@code RunContext} and the
 * registered {@link SourceRecord}, so the port itself never has to resolve a source by id.
 */
public record AcquisitionCommand(
        PecConnectionProperties connectionProperties,
        PecSourceIdentity sourceIdentity,
        ReadBudget budget,
        String extractionId,
        LocalDate periodStart,
        LocalDate periodEndExclusive,
        String sourceZoneId
) {
}
