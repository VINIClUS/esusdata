package esusdata.result.model;

import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.TeamResult;
import java.time.Instant;
import java.util.List;

/**
 * Inputs to open one {@code result_staging} row (§1.9.5). ADR 0030: {@code teams} is the same
 * result per team (INE), persisted beside the municipal one.
 */
public record StagingRequest(
        String stagingId,
        String jobId,
        long executionGeneration,
        String processInstanceId,
        Instant createdAt,
        String indicatorPack,
        IndicatorResult result,
        String extractionId,
        String adapterVersion,
        String evidenceGrain,
        String inputFingerprint,
        List<TeamResult> teams) {

    public StagingRequest {
        teams = teams == null ? List.of() : List.copyOf(teams);
    }

    /** A municipal result without a per-team breakdown. */
    public StagingRequest(
            String stagingId,
            String jobId,
            long executionGeneration,
            String processInstanceId,
            Instant createdAt,
            String indicatorPack,
            IndicatorResult result,
            String extractionId,
            String adapterVersion,
            String evidenceGrain,
            String inputFingerprint) {
        this(
                stagingId,
                jobId,
                executionGeneration,
                processInstanceId,
                createdAt,
                indicatorPack,
                result,
                extractionId,
                adapterVersion,
                evidenceGrain,
                inputFingerprint,
                List.of());
    }
}
