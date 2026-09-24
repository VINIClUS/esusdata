package esusdata.result.model;

import esusdata.indicator.model.IndicatorResult;
import java.time.Instant;

/** Inputs to open one {@code result_staging} row (§1.9.5). */
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
        String inputFingerprint) {}
