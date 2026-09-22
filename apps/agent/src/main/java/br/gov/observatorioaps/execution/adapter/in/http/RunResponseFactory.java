package br.gov.observatorioaps.execution.adapter.in.http;

import br.gov.observatorioaps.execution.domain.job.Job;
import br.gov.observatorioaps.execution.domain.job.JobRepository;
import br.gov.observatorioaps.execution.domain.job.JobState;
import br.gov.observatorioaps.results.domain.ResultRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/** Shared {@code Job} → {@link RunResponse} mapping — {@code RunController} and {@code RunEventsController} (SSE) must never render a run differently. */
@Component
public class RunResponseFactory {

    private final JobRepository jobRepository;
    private final ResultRepository resultRepository;

    public RunResponseFactory(JobRepository jobRepository, ResultRepository resultRepository) {
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
