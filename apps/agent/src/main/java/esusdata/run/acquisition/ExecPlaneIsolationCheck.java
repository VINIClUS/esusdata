package esusdata.run.acquisition;

import esusdata.source.SourceIsolationCheck;
import esusdata.source.pec.MunicipalIsolationContract;
import esusdata.source.pec.PecCompatibilityMatrix;
import esusdata.source.pec.PecConnectionProperties;
import esusdata.source.pec.PecSecretResolver;
import esusdata.source.pec.PecSourceIdentity;
import esusdata.source.pec.ReadBudget;
import java.time.Duration;
import java.time.YearMonth;
import java.util.List;

/**
 * {@link SourceIsolationCheck} through the execution plane (ADR 0023): one {@code check_isolation}
 * {@link ExecPlaneAggregateRead} of the {@code municipal_isolation} capability, whose {@code
 * isolation} message carries the competência's counts per {@code co_ibge}.
 */
public final class ExecPlaneIsolationCheck implements SourceIsolationCheck {

    private static final ExecPlaneAggregateRead.Kind KIND = new ExecPlaneAggregateRead.Kind(
            "check_isolation",
            MunicipalIsolationContract.CAPABILITY,
            MunicipalIsolationContract.ADAPTER_VERSION,
            MunicipalIsolationContract.QUERY_CHECKSUM,
            "isolation",
            "isolation check");

    private final ExecPlaneAggregateRead read;

    public ExecPlaneIsolationCheck(
            List<String> command, PecSecretResolver secretResolver, ExecPlaneTransport transport, Duration exitGrace) {
        this(command, secretResolver, transport, PecCompatibilityMatrix.fromClasspathResource(), exitGrace);
    }

    /** Package-visible seam for tests to inject a synthetic matrix, like {@link ExecPlaneAcquisition}'s. */
    ExecPlaneIsolationCheck(
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
            YearMonth referencePeriod,
            ReadBudget budget) {
        ExecPlaneAggregateRead.Outcome<List<MunicipalityCount>> outcome = read.read(
                KIND,
                properties,
                identity,
                validatedHost,
                referencePeriod.atDay(1),
                referencePeriod.plusMonths(1).atDay(1),
                budget,
                message -> ExecPlaneAggregateRead.counts(
                        message, (count, value) -> new MunicipalityCount(ExecPlaneProcess.text(count, "ibge"), value)));
        return switch (outcome) {
            case ExecPlaneAggregateRead.Outcome.Terminal<List<MunicipalityCount>> terminal ->
                Result.checked(terminal.value());
            case ExecPlaneAggregateRead.Outcome.Failed<List<MunicipalityCount>> failed ->
                Result.failed(failed.sqlState());
            case ExecPlaneAggregateRead.Outcome.CompatibilityMismatch<List<MunicipalityCount>> mismatch ->
                Result.of(Status.COMPATIBILITY_MISMATCH);
            case ExecPlaneAggregateRead.Outcome.BudgetExceeded<List<MunicipalityCount>> exceeded ->
                Result.of(Status.SOURCE_BUDGET_EXCEEDED);
        };
    }
}
