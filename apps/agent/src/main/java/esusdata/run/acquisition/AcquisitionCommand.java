package esusdata.run.acquisition;

import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ReadBudget;
import java.time.LocalDate;

/**
 * Everything an {@link Acquisition} needs to run one live acquisition — assembled by
 * {@code jobrunner.application.RunExecutor} from the job's {@code RunContext} and the
 * registered {@link SourceRecord}, so the port itself never has to resolve a source by id.
 */
public record AcquisitionCommand(
        PecConnectionProperties connectionProperties,
        PecSourceIdentity sourceIdentity,
        ReadBudget budget,
        String extractionId,
        LocalDate periodStart,
        LocalDate periodEndExclusive,
        String sourceZoneId) {}
