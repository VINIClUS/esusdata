package br.gov.observatorioaps.jobrunner.domain;

/** Job lifecycle states — Tech Spec §1.9.4, verbatim set. No other state exists. */
public enum JobState {
    QUEUED,
    RUNNING,
    STAGED,
    CANCEL_REQUESTED,
    CANCELLED,
    SUCCEEDED,
    FAILED
}
