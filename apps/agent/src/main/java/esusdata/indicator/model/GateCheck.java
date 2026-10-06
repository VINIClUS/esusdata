package esusdata.indicator.model;

import java.util.List;
import java.util.Objects;

/**
 * The outcome of one automated gate check (ADR 0032): which check ran, when, and the evidence it
 * left. Nobody signs a gate off — an agent or the run itself records what a check found.
 *
 * @param check the automated check's id and version, e.g. {@code conferencia-fichas@1}; null while
 *     {@link State#PENDING}
 * @param checkedAt an ISO date ({@code yyyy-MM-dd}); null while pending
 */
public record GateCheck(State state, String check, String checkedAt, List<Evidence> evidence, String note) {

    /** Where a gate stands. Only {@link #PASSED} lets a result through. */
    public enum State {
        PENDING,
        PASSED,
        FAILED
    }

    /** A document the check relied on: a repo-relative reference and the SHA-256 of its bytes. */
    public record Evidence(String kind, String ref, String sha256) {
        public Evidence {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(ref, "ref");
            Objects.requireNonNull(sha256, "sha256");
        }
    }

    public GateCheck {
        Objects.requireNonNull(state, "state");
        evidence = List.copyOf(evidence);
    }

    /** A gate no check has passed yet. */
    public static GateCheck pending(String note) {
        return new GateCheck(State.PENDING, null, null, List.of(), note);
    }

    public boolean isPassed() {
        return state == State.PASSED;
    }
}
