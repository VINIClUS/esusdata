package br.gov.observatorioaps.api;

import br.gov.observatorioaps.jobrunner.Job;
import br.gov.observatorioaps.jobrunner.JobRepository;
import br.gov.observatorioaps.jobrunner.JobState;
import br.gov.observatorioaps.resultstore.ResultRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/** Shared {@code Job} → {@link RunResponse} mapping — {@code RunController} and {@code RunEventsController} (SSE) must never render a run differently. */
@Component
class RunResponseFactory {

    private final JobRepository jobRepository;
    private final ResultRepository resultRepository;

    RunResponseFactory(JobRepository jobRepository, ResultRepository resultRepository) {
        this.jobRepository = jobRepository;
        this.resultRepository = resultRepository;
    }

    RunResponse toResponse(Job job) {
        String resultId = job.state() == JobState.SUCCEEDED
                ? resultRepository.findResultIdByJobId(job.jobId(), job.municipalityIbge()).orElse(null)
                : null;
        List<AttemptResponse> attempts = jobRepository.findAttempts(job.jobId()).stream()
                .map(a -> new AttemptResponse(a.attempt(), a.startedAt().toString(),
                        a.finishedAt() == null ? null : a.finishedAt().toString(), a.outcome(),
                        a.failureCode(), a.failureDetail()))
                .toList();
        return new RunResponse(
                job.jobId(), job.runId(), job.state().name(), job.attempt(), job.maxAttempts(),
                job.municipalityIbge(), job.indicatorPack(), job.ruleVersion(), job.referencePeriod(),
                job.sourceId(), job.extractionId(), job.createdAt() == null ? null : job.createdAt().toString(),
                job.startedAt() == null ? null : job.startedAt().toString(),
                job.finishedAt() == null ? null : job.finishedAt().toString(),
                job.lastProgressAt() == null ? null : job.lastProgressAt().toString(),
                job.failureCode(), job.failureDetail(), resultId, attempts);
    }
}
