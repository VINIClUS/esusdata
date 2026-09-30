package esusdata.run.acquisition;

import esusdata.source.SourceCoverageCheck;
import esusdata.source.SourceIsolationCheck.Status;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSecretResolver;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.PeriodCoverageContract;
import esusdata.source.pec.ReadBudget;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;

/**
 * {@link SourceCoverageCheck} through the execution plane (ADR 0027): one {@code check_coverage}
 * {@link ExecPlaneAggregateRead} of the {@code period_coverage} capability, whose {@code coverage}
 * message carries the window's counts per {@code co_ibge} and competência.
 */
public final class ExecPlaneCoverageCheck implements SourceCoverageCheck {

    private static final ExecPlaneAggregateRead.Kind KIND = new ExecPlaneAggregateRead.Kind(
            "check_coverage",
            PeriodCoverageContract.CAPABILITY,
            PeriodCoverageContract.ADAPTER_VERSION,
            PeriodCoverageContract.QUERY_CHECKSUM,
            "coverage",
            "coverage check");

    private final ExecPlaneAggregateRead read;

    public ExecPlaneCoverageCheck(
            List<String> command, PecSecretResolver secretResolver, ExecPlaneTransport transport, Duration exitGrace) {
        this(command, secretResolver, transport, PecCompatibilityMatrix.fromClasspathResource(), exitGrace);
    }

    /** Package-visible seam for tests to inject a synthetic matrix, like {@link ExecPlaneAcquisition}'s. */
    ExecPlaneCoverageCheck(
            List<String> command,
            PecSecretResolver secretResolver,
            ExecPlaneTransport transport,
            PecCompatibilityMatrix matrix,
            Duration exitGrace) {
        this.read = new ExecPlaneAggregateRead(command, secretResolver, transport, matrix, exitGrace);
    }

    @Override
    public Result check(
            PecConnectionProperties properties,
            PecSourceIdentity identity,
            String validatedHost,
            YearMonth from,
            YearMonth toExclusive,
            ReadBudget budget) {
        ExecPlaneAggregateRead.Outcome<List<PeriodCount>> outcome = read.read(
                KIND,
                properties,
                identity,
                validatedHost,
                from.atDay(1),
                toExclusive.atDay(1),
                budget,
                message -> ExecPlaneAggregateRead.counts(message, ExecPlaneCoverageCheck::periodCount));
        return switch (outcome) {
            case ExecPlaneAggregateRead.Outcome.Terminal<List<PeriodCount>> terminal ->
                Result.checked(terminal.value());
            case ExecPlaneAggregateRead.Outcome.Failed<List<PeriodCount>> failed -> Result.failed(failed.sqlState());
            case ExecPlaneAggregateRead.Outcome.CompatibilityMismatch<List<PeriodCount>> mismatch ->
                Result.of(Status.COMPATIBILITY_MISMATCH);
            case ExecPlaneAggregateRead.Outcome.BudgetExceeded<List<PeriodCount>> exceeded ->
                Result.of(Status.SOURCE_BUDGET_EXCEEDED);
        };
    }

    private static PeriodCount periodCount(tools.jackson.databind.JsonNode count, long value) {
        String period = ExecPlaneProcess.text(count, "period");
        try {
            YearMonth.parse(period);
        } catch (RuntimeException e) { // NOPMD - null or malformed: either way a protocol violation
            throw new IllegalStateException("coverage period is not yyyy-MM: " + period, e);
        }
        return new PeriodCount(ExecPlaneProcess.text(count, "ibge"), period, value);
    }
}
