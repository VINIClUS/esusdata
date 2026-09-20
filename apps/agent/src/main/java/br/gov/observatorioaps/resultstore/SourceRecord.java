package br.gov.observatorioaps.resultstore;

/**
 * A row of {@code sources} (Tech Spec §1.4.1). {@code pecVersion}/{@code readModel} are nullable
 * — a source registered only to replay an {@code IMMUTABLE_EXTRACT} may never need to reconstruct
 * a live {@code PecSourceIdentity}.
 */
public record SourceRecord(
        String id,
        int sourceConfigurationVersion,
        String sourceFamily,
        String pecInstallationRole,
        String sourceLocationKind,
        String host,
        int port,
        String databaseName,
        String dbUser,
        String secretRef,
        String municipalityIbge,
        String pecVersion,
        String readModel,
        String createdAt
) {
}
