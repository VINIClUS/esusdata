package esusdata.web;

/**
 * 409 body for {@code ACTIVE_JOB_EXISTS} (ADR 0026): {@link ApiError}'s fields plus the active
 * job the client should follow. The job is of the same município the caller was just authorized
 * for, so naming it discloses nothing outside the caller's scope.
 */
public record ActiveJobError(String code, String message, String jobId) {}
