package esusdata.source.dto;

/** {@code secretRef} is a reference/state string, never the resolved secret value (§1.12.7 L550). */
public record SourceResponse(
        String id, int sourceConfigurationVersion, String sourceFamily, String pecInstallationRole,
        String sourceLocationKind, String host, int port, String databaseName, String dbUser,
        String secretRef, String municipalityIbge, String pecVersion, String readModel, String createdAt) {
}
