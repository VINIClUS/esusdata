package br.gov.observatorioaps.execution.domain.job;

import java.util.Map;
import java.util.Set;
/**
 * Tech Spec §1.9.4 transition table, verbatim:
 *
 * <pre>
 * QUEUED            -&gt; RUNNING | CANCELLED
 * RUNNING           -&gt; STAGED | FAILED | CANCEL_REQUESTED
 * STAGED            -&gt; SUCCEEDED | FAILED | CANCEL_REQUESTED
 * CANCEL_REQUESTED  -&gt; CANCELLED
 * RUNNING/STAGED abandoned after restart -&gt; QUEUED | FAILED
 * CANCEL_REQUESTED after restart         -&gt; CANCELLED
 * </pre>
 *
 * <p>The "abandoned after restart" row adds no new edges beyond {@code RUNNING -&gt; QUEUED} and
 * {@code STAGED -&gt; QUEUED} — both already needed for a <em>live</em> transient-failure retry
 * (§1.9.4: "retries automáticos... com atraso crescente"), which never visits {@code FAILED} at
 * all while attempts remain. Recovery and live retry are the same edge; only the caller differs.
 *
 * <p>This is the single source of truth for legality. {@code JobRepository}'s CAS updates encode
 * the identical table in SQL {@code WHERE} clauses — {@code JobStateMachineTest} exercises both
 * and fails if they diverge.
 */
public final class JobStateMachine {

    private static final Map<JobState, Set<JobState>> ALLOWED = Map.of(
            JobState.QUEUED, Set.of(JobState.RUNNING, JobState.CANCELLED),
            JobState.RUNNING, Set.of(JobState.STAGED, JobState.FAILED, JobState.CANCEL_REQUESTED, JobState.QUEUED),
            JobState.STAGED, Set.of(JobState.SUCCEEDED, JobState.FAILED, JobState.CANCEL_REQUESTED, JobState.QUEUED),
            JobState.CANCEL_REQUESTED, Set.of(JobState.CANCELLED),
            JobState.CANCELLED, Set.of(),
            JobState.SUCCEEDED, Set.of(),
            JobState.FAILED, Set.of()
    );

    private JobStateMachine() {
    }

    public static boolean isAllowed(JobState from, JobState to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }

    public static void requireAllowed(JobState from, JobState to) {
        if (!isAllowed(from, to)) {
            throw new IllegalStateException("illegal job transition: " + from + " -> " + to);
        }
    }

    public static boolean isTerminal(JobState state) {
        return ALLOWED.getOrDefault(state, Set.of()).isEmpty();
    }
}
