package esusdata.run.acquisition;

import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ReadBudget;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything an {@link Acquisition} needs to run one live acquisition — assembled by
 * {@code jobrunner.application.RunExecutor} from the job's {@code RunContext} and the
 * registered {@link SourceRecord}, so the port itself never has to resolve a source by id.
 *
 * <p>{@code parts} is empty for C1's canonical v1 acquisition (one compiled-in capability, the
 * period above) and lists every capability of a canonical v2 acquisition (ADR 0030), all read in
 * one consistent read-only transaction; the period above then spans every part's window.
 */
public record AcquisitionCommand(
        PecConnectionProperties connectionProperties,
        PecSourceIdentity sourceIdentity,
        ReadBudget budget,
        String extractionId,
        LocalDate periodStart,
        LocalDate periodEndExclusive,
        String sourceZoneId,
        List<AcquisitionPart> parts) {

    public AcquisitionCommand {
        parts = List.copyOf(parts);
    }

    /** A canonical v1 acquisition (C1): the one compiled-in capability over the period. */
    public AcquisitionCommand(
            PecConnectionProperties connectionProperties,
            PecSourceIdentity sourceIdentity,
            ReadBudget budget,
            String extractionId,
            LocalDate periodStart,
            LocalDate periodEndExclusive,
            String sourceZoneId) {
        this(
                connectionProperties,
                sourceIdentity,
                budget,
                extractionId,
                periodStart,
                periodEndExclusive,
                sourceZoneId,
                List.of());
    }

    public boolean isCanonicalV2() {
        return !parts.isEmpty();
    }
}
