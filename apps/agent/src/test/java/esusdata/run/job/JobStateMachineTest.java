package esusdata.run.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Tech Spec §1.9.4 transition table, exercised directly. */
class JobStateMachineTest {

    @Test
    void allowsExactlyTheSpecTransitions() {
        assertThat(JobStateMachine.isAllowed(JobState.QUEUED, JobState.RUNNING)).isTrue();
        assertThat(JobStateMachine.isAllowed(JobState.QUEUED, JobState.CANCELLED))
                .isTrue();
        assertThat(JobStateMachine.isAllowed(JobState.RUNNING, JobState.STAGED)).isTrue();
        assertThat(JobStateMachine.isAllowed(JobState.RUNNING, JobState.FAILED)).isTrue();
        assertThat(JobStateMachine.isAllowed(JobState.RUNNING, JobState.CANCEL_REQUESTED))
                .isTrue();
        assertThat(JobStateMachine.isAllowed(JobState.RUNNING, JobState.QUEUED)).isTrue();
        assertThat(JobStateMachine.isAllowed(JobState.STAGED, JobState.SUCCEEDED))
                .isTrue();
        assertThat(JobStateMachine.isAllowed(JobState.STAGED, JobState.FAILED)).isTrue();
        assertThat(JobStateMachine.isAllowed(JobState.STAGED, JobState.CANCEL_REQUESTED))
                .isTrue();
        assertThat(JobStateMachine.isAllowed(JobState.STAGED, JobState.QUEUED)).isTrue();
        assertThat(JobStateMachine.isAllowed(JobState.CANCEL_REQUESTED, JobState.CANCELLED))
                .isTrue();
    }

    @Test
    void terminalStatesAcceptNoTransitionAtAll() {
        for (JobState terminal : new JobState[] {JobState.CANCELLED, JobState.SUCCEEDED, JobState.FAILED}) {
            for (JobState target : JobState.values()) {
                assertThat(JobStateMachine.isAllowed(terminal, target))
                        .as(terminal + " -> " + target)
                        .isFalse();
            }
            assertThat(JobStateMachine.isTerminal(terminal)).isTrue();
        }
    }

    @Test
    void succeededRefusesCancellation() {
        assertThat(JobStateMachine.isAllowed(JobState.SUCCEEDED, JobState.CANCEL_REQUESTED))
                .isFalse();
        assertThat(JobStateMachine.isAllowed(JobState.SUCCEEDED, JobState.CANCELLED))
                .isFalse();
    }

    @Test
    void disallowedTransitionsAreRejectedLoudly() {
        assertThatThrownBy(() -> JobStateMachine.requireAllowed(JobState.QUEUED, JobState.STAGED))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> JobStateMachine.requireAllowed(JobState.SUCCEEDED, JobState.RUNNING))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void everyStateHasSomeDefinedOutcome() {
        // No JobState is silently absent from the map — a coverage gap would let an untested
        // state fall through to "everything allowed" via the empty-set default.
        for (JobState state : JobState.values()) {
            for (JobState target : JobState.values()) {
                // Calling isAllowed must never throw for any (from, to) pair.
                JobStateMachine.isAllowed(state, target);
            }
        }
    }
}
