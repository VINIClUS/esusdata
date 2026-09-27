package esusdata.source.model;

/**
 * The stored result of a source's last diagnostic, only while it still matches the source's
 * current configuration version. {@code detail} is a connection-class message, never the secret.
 */
public record LastDiagnostic(String outcome, String detail, String testedAt) {}
