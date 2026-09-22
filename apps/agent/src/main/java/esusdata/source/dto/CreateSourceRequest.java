package esusdata.source.dto;

public record CreateSourceRequest(
        String id, String sourceFamily, String pecInstallationRole, String sourceLocationKind,
        String host, int port, String databaseName, String dbUser, String secretRef,
        String municipalityIbge, String pecVersion, String readModel) {
}
