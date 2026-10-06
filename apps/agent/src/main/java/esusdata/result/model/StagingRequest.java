package esusdata.result.model;

import esusdata.indicator.model.IndicatorResult;
import esusdata.indicator.model.TeamResult;
import java.time.Instant;
import java.util.List;

/**
 * Inputs to open one {@code result_staging} row (§1.9.5). ADR 0030: {@code teams} is the same
 * result per team (INE), persisted beside the municipal one. ADR 0032: {@code gateSnapshotJson} is
 * where the release gates stood when the executor gated the result; a {@code COMPUTED} result
 * cannot be staged without it.
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
        List<TeamResult> teams,
        String gateSnapshotJson) {

    public StagingRequest {
        teams = teams == null ? List.of() : List.copyOf(teams);
    }

    /** A result staged without a gate snapshot; the store refuses it when {@code COMPUTED} (V12). */
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
            String inputFingerprint,
            List<TeamResult> teams) {
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
                teams,
                null);
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
                List.of(),
                null);
    }
}
