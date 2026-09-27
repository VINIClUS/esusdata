package esusdata.source.dto;

/** The source's last diagnostic under its current configuration; {@code detail} never holds the secret. */
public record LastDiagnosticResponse(String outcome, String detail, String testedAt) {}
